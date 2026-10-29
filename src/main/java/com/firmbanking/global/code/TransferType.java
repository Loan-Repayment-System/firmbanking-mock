package com.firmbanking.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * transfer_type 테이블
 */
@Getter
@RequiredArgsConstructor
public enum TransferType implements CodeEnum {

    PAYMENT(1),
    COLLECTION(2),
    ;

    private final int id;
}
