package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.domain.company.dto.response.CompanyDetailResponse;
import com.firmbanking.domain.company.mapper.CompanyMapper;
import com.firmbanking.domain.consent.dto.response.WithdrawalConsentDetailResponse;
import com.firmbanking.domain.consent.mapper.WithdrawalConsentMapper;
import com.firmbanking.domain.transfer.dto.request.CreateTransferRequest;
import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.global.code.ConsentStatus;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.global.code.TransferType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 계좌 조회 → 검사 → 잔액 변경 → 이체 결과 저장을 한 트랜잭션으로 처리한다.
 * 거절도 잔액은 바꾸지 않고 이체 결과를 저장한다.
 * 지급과 집금은 거래별 검사와 응답 잔액만 다르다.
 */
@Component
@RequiredArgsConstructor
public class TransferProcessor {

    private final AccountMapper accountMapper;
    private final CompanyMapper companyMapper;
    private final TransferMapper transferMapper;
    private final WithdrawalConsentMapper withdrawalConsentMapper;

    @Transactional
    public TransferResultResponse process(ExecuteTransferRequest request) {
        AccountDetailResponse withdrawal = findWithdrawalAccount(request);
        AccountDetailResponse deposit = findDepositAccount(request);
        ResponseCode checked = check(request, withdrawal, deposit);
        if (checked != ResponseCode.SUCCESS) {
            return save(request, checked, null);
        }
        BigDecimal balanceAfter = moveBalance(request, withdrawal, deposit);
        return save(request, ResponseCode.SUCCESS, balanceAfter);
    }

    private AccountDetailResponse findWithdrawalAccount(ExecuteTransferRequest request) {
        return accountMapper.findByBankCodeAndAccountNo(
                request.getWithdrawalBankCode(), request.getWithdrawalAccountNo());
    }

    private AccountDetailResponse findDepositAccount(ExecuteTransferRequest request) {
        return accountMapper.findByBankCodeAndAccountNo(
                request.getDepositBankCode(), request.getDepositAccountNo());
    }

    /**
     * 계좌 존재(B001·B002) → 거래별 검사(B004·C001) → 잔액(B003) 순서로 검사한다.
     * 통과하면 SUCCESS다.
     */
    private ResponseCode check(ExecuteTransferRequest request, AccountDetailResponse withdrawal,
                               AccountDetailResponse deposit) {
        if (withdrawal == null) {
            return ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND;
        }
        if (deposit == null) {
            return ResponseCode.DEPOSIT_ACCOUNT_NOT_FOUND;
        }
        ResponseCode checked = checkTransferType(request, withdrawal, deposit);
        if (checked != ResponseCode.SUCCESS) {
            return checked;
        }
        if (withdrawal.getBalance().compareTo(request.getAmount()) < 0) {
            return ResponseCode.INSUFFICIENT_BALANCE;
        }
        return ResponseCode.SUCCESS;
    }

    /**
     * 출금계좌에서 빼고 입금계좌에 더한 뒤 응답할 잔액을 돌려준다.
     * 지급은 출금 후 지급 계좌 잔액, 집금은 입금 후 수납 계좌 잔액이다.
     * 조회한 뒤 다른 이체가 잔액을 바꿨으면 OptimisticLockingFailureException을 던져 트랜잭션을 롤백한다.
     */
    private BigDecimal moveBalance(ExecuteTransferRequest request, AccountDetailResponse withdrawal,
                                   AccountDetailResponse deposit) {
        // 같은 계좌면 잔액이 그대로다. 따로 쓰면 입금이 조회한 잔액으로 출금을 덮어쓴다.
        if (withdrawal.getAccountId().equals(deposit.getAccountId())) {
            return withdrawal.getBalance();
        }
        BigDecimal withdrawalBalance = withdrawal.getBalance().subtract(request.getAmount());
        BigDecimal depositBalance = deposit.getBalance().add(request.getAmount());
        updateBalance(withdrawal, withdrawalBalance);
        updateBalance(deposit, depositBalance);
        return switch (request.getTransferType()) {
            case PAYMENT -> withdrawalBalance;
            case COLLECTION -> depositBalance;
        };
    }

    private void updateBalance(AccountDetailResponse account, BigDecimal balance) {
        int updated = accountMapper.updateBalance(account.getAccountId(), balance, account.getVersion());
        if (updated == 0) {
            throw new OptimisticLockingFailureException("계좌 " + account.getAccountId() + "의 version이 조회 후 바뀌었다");
        }
    }

    /**
     * 지급: 출금계좌가 업체 지급 계좌인가(B004).
     * 집금: 입금계좌가 업체 수납 계좌인가(B004), 출금계좌가 출금 동의 등록 상태인가(C001).
     * 통과하면 SUCCESS다.
     */
    private ResponseCode checkTransferType(ExecuteTransferRequest request, AccountDetailResponse withdrawal,
                                           AccountDetailResponse deposit) {
        CompanyDetailResponse company = companyMapper.findByCompanyNo(request.getCompanyNo());
        if (request.getTransferType() == TransferType.PAYMENT) {
            return withdrawal.getAccountId().equals(company.getPaymentAccountId())
                    ? ResponseCode.SUCCESS : ResponseCode.NOT_COMPANY_ACCOUNT;
        }
        if (!deposit.getAccountId().equals(company.getCollectionAccountId())) {
            return ResponseCode.NOT_COMPANY_ACCOUNT;
        }
        WithdrawalConsentDetailResponse consent =
                withdrawalConsentMapper.findByCompanyNoAndAccountId(request.getCompanyNo(), withdrawal.getAccountId());
        if (consent == null || consent.getConsentStatusId() != ConsentStatus.REGISTERED) {
            return ResponseCode.CONSENT_NOT_REGISTERED;
        }
        return ResponseCode.SUCCESS;
    }

    private TransferResultResponse save(ExecuteTransferRequest request, ResponseCode responseCode,
                                        BigDecimal balanceAfter) {
        transferMapper.insert(CreateTransferRequest.builder()
                .companyNo(request.getCompanyNo())
                .sentDate(request.getSentDate())
                .messageNo(request.getMessageNo())
                .sentTime(request.getSentTime())
                .transferType(request.getTransferType())
                .withdrawalAccountNo(request.getWithdrawalAccountNo())
                .depositBankCode(request.getDepositBankCode())
                .depositAccountNo(request.getDepositAccountNo())
                .amount(request.getAmount())
                .memo(request.getMemo())
                .responseCode(responseCode.getCode())
                .balanceAfter(balanceAfter)
                .build());
        return TransferResultResponse.builder()
                .responseCode(responseCode)
                .balanceAfter(balanceAfter)
                .build();
    }
}
