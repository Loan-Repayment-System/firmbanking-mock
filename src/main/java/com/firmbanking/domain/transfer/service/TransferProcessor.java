package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.domain.company.dto.response.CompanyDetailResponse;
import com.firmbanking.domain.company.mapper.CompanyMapper;
import com.firmbanking.domain.transfer.dto.request.CreateTransferRequest;
import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.global.code.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 이체 처리 흐름 3~6단계를 한 트랜잭션으로 처리한다 (프로그램 설계 1절).
 * 거절도 잔액은 바꾸지 않고 이체 결과를 저장한다.
 */
@Component
@RequiredArgsConstructor
public class TransferProcessor {

    private final AccountMapper accountMapper;
    private final CompanyMapper companyMapper;
    private final TransferMapper transferMapper;

    @Transactional
    public TransferResultResponse process(ExecuteTransferRequest request) {
        AccountDetailResponse withdrawal = accountMapper.findByBankCodeAndAccountNo(
                request.getWithdrawalBankCode(), request.getWithdrawalAccountNo());
        if (withdrawal == null) {
            return save(request, ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND, null);
        }
        AccountDetailResponse deposit = accountMapper.findByBankCodeAndAccountNo(
                request.getDepositBankCode(), request.getDepositAccountNo());
        if (deposit == null) {
            return save(request, ResponseCode.DEPOSIT_ACCOUNT_NOT_FOUND, null);
        }
        CompanyDetailResponse company = companyMapper.findByCompanyNo(request.getCompanyNo());
        if (!withdrawal.getAccountId().equals(company.getPaymentAccountId())) {
            return save(request, ResponseCode.NOT_COMPANY_ACCOUNT, null);
        }
        if (withdrawal.getBalance().compareTo(request.getAmount()) < 0) {
            return save(request, ResponseCode.INSUFFICIENT_BALANCE, null);
        }
        // 같은 계좌면 잔액이 그대로다. 따로 쓰면 입금이 조회한 잔액으로 출금을 덮어쓴다.
        if (withdrawal.getAccountId().equals(deposit.getAccountId())) {
            return save(request, ResponseCode.SUCCESS, withdrawal.getBalance());
        }
        BigDecimal balanceAfter = withdrawal.getBalance().subtract(request.getAmount());
        accountMapper.updateBalance(withdrawal.getAccountId(), balanceAfter);
        accountMapper.updateBalance(deposit.getAccountId(), deposit.getBalance().add(request.getAmount()));
        return save(request, ResponseCode.SUCCESS, balanceAfter);
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
