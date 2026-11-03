package com.firmbanking.domain.account.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDetailResponse {

    private Long accountId;
    private String bankCode;
    private String accountNo;
    private String holderName;
    private String holderBirthDate;
    private BigDecimal balance;
}
