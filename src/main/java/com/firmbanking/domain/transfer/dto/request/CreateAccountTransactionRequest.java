package com.firmbanking.domain.transfer.dto.request;

import com.firmbanking.global.code.AccountTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountTransactionRequest {

    private Long accountTransactionId;
    private Long accountId;
    private AccountTransactionType accountTransactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
}
