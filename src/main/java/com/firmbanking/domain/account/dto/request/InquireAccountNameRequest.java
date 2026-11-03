package com.firmbanking.domain.account.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquireAccountNameRequest {

    private String bankCode;
    private String accountNo;
    private String realNameNo;
}
