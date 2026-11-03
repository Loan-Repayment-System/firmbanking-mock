package com.firmbanking.domain.consent.dto.request;

import com.firmbanking.global.code.ConsentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplyWithdrawalConsentRequest {

    private String companyNo;
    private String bankCode;
    private String accountNo;
    private ConsentStatus consentStatus;
    private String payerNo;
    private String appliedDate;
}
