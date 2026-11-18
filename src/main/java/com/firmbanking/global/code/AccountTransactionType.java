package com.firmbanking.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * account_transaction_type 테이블
 */
@Getter
@RequiredArgsConstructor
public enum AccountTransactionType implements CodeEnum {

    WITHDRAWAL(1),
    DEPOSIT(2),
    ;

    private final int id;
}
