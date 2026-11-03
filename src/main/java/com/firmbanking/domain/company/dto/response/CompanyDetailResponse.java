package com.firmbanking.domain.company.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyDetailResponse {

    private String companyNo;
    private String companyName;
    private Long collectionAccountId;
    private Long paymentAccountId;
}
