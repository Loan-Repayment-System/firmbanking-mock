package com.firmbanking.domain.consent.service;

import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.domain.consent.dto.request.ApplyWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.request.CreateWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.request.UpdateWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.response.WithdrawalConsentDetailResponse;
import com.firmbanking.domain.consent.mapper.WithdrawalConsentMapper;
import com.firmbanking.global.code.ConsentStatus;
import com.firmbanking.global.code.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawalConsentService {

    private final AccountMapper accountMapper;
    private final WithdrawalConsentMapper withdrawalConsentMapper;

    /**
     * 출금 동의를 요청한 상태로 만든다. 계좌가 없으면 B001이다.
     * 이미 그 상태거나, 동의한 적 없는 계좌를 해지하면 아무것도 바꾸지 않고 0000이다.
     */
    @Transactional
    public ResponseCode apply(ApplyWithdrawalConsentRequest request) {
        AccountDetailResponse account =
                accountMapper.findByBankCodeAndAccountNo(request.getBankCode(), request.getAccountNo());
        if (account == null) {
            return ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND;
        }
        WithdrawalConsentDetailResponse consent =
                withdrawalConsentMapper.findByCompanyNoAndAccountId(request.getCompanyNo(), account.getAccountId());
        if (consent == null) {
            if (request.getConsentStatus() == ConsentStatus.REGISTERED) {
                withdrawalConsentMapper.insert(CreateWithdrawalConsentRequest.builder()
                        .companyNo(request.getCompanyNo())
                        .accountId(account.getAccountId())
                        .consentStatus(ConsentStatus.REGISTERED)
                        .payerNo(request.getPayerNo())
                        .appliedDate(request.getAppliedDate())
                        .build());
            }
            return ResponseCode.SUCCESS;
        }
        if (consent.getConsentStatusId() != request.getConsentStatus()) {
            withdrawalConsentMapper.updateStatus(UpdateWithdrawalConsentRequest.builder()
                    .consentId(consent.getConsentId())
                    .consentStatus(request.getConsentStatus())
                    .payerNo(request.getPayerNo())
                    .appliedDate(request.getAppliedDate())
                    .build());
        }
        return ResponseCode.SUCCESS;
    }
}
