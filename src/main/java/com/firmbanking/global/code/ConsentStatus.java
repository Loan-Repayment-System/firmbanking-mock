package com.firmbanking.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * consent_status 테이블
 */
@Getter
@RequiredArgsConstructor
public enum ConsentStatus implements CodeEnum {

    REGISTERED(1),
    CANCELLED(2),
    ;

    private final int id;
}
