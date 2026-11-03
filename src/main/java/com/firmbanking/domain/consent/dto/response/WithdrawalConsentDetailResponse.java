package com.firmbanking.domain.consent.dto.response;

import com.firmbanking.global.code.ConsentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalConsentDetailResponse {

    private Long consentId;
    private String companyNo;
    private Long accountId;
    private ConsentStatus consentStatusId;
    private String payerNo;
    private String appliedDate;
}
