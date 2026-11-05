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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 이체 처리 흐름 3~6단계를 한 트랜잭션으로 처리한다 (프로그램 설계 1절).
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
        if (!moveBalance(request, withdrawal, deposit)) {
            return save(request, ResponseCode.INSUFFICIENT_BALANCE, null);
        }
        return save(request, ResponseCode.SUCCESS, findBalanceAfter(request));
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
     * 계좌 존재(B001·B002) → 거래별 검사(B004·C001) 순서로 검사한다.
     * 통과하면 SUCCESS다. 잔액(B003)은 출금 UPDATE가 판단한다.
     */
    private ResponseCode check(ExecuteTransferRequest request, AccountDetailResponse withdrawal,
                               AccountDetailResponse deposit) {
        if (withdrawal == null) {
            return ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND;
        }
        if (deposit == null) {
            return ResponseCode.DEPOSIT_ACCOUNT_NOT_FOUND;
        }
        return checkTransferType(request, withdrawal, deposit);
    }

    /**
     * 출금계좌에서 빼고 입금계좌에 더한다. 잔액은 조회한 값이 아니라 UPDATE 시점의 잔액으로 계산한다.
     * 출금계좌 잔액이 금액보다 적어 출금하지 못하면 false다.
     */
    private boolean moveBalance(ExecuteTransferRequest request, AccountDetailResponse withdrawal,
                                AccountDetailResponse deposit) {
        if (accountMapper.updateWithdrawal(withdrawal.getAccountId(), request.getAmount()) == 0) {
            return false;
        }
        accountMapper.updateDeposit(deposit.getAccountId(), request.getAmount());
        return true;
    }

    /**
     * 지급은 출금 후 지급 계좌 잔액, 집금은 입금 후 수납 계좌 잔액이다.
     * 이 트랜잭션이 UPDATE한 행이라 다시 조회하면 바꾼 잔액이 나온다.
     */
    private BigDecimal findBalanceAfter(ExecuteTransferRequest request) {
        AccountDetailResponse account = switch (request.getTransferType()) {
            case PAYMENT -> findWithdrawalAccount(request);
            case COLLECTION -> findDepositAccount(request);
        };
        return account.getBalance();
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
