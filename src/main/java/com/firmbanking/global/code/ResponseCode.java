package com.firmbanking.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전문 공통부의 응답코드. code가 전문에 실리는 값이다.
 */
@Getter
@RequiredArgsConstructor
public enum ResponseCode {

    SUCCESS("0000"),

    // 전문
    INVALID_FORMAT("A001"),
    UNSUPPORTED_TELEGRAM("A002"),
    UNREGISTERED_COMPANY("A003"),

    // 계좌
    WITHDRAWAL_ACCOUNT_NOT_FOUND("B001"),
    DEPOSIT_ACCOUNT_NOT_FOUND("B002"),
    INSUFFICIENT_BALANCE("B003"),
    NOT_COMPANY_ACCOUNT("B004"),
    REAL_NAME_MISMATCH("B005"),

    // 출금 동의
    CONSENT_NOT_REGISTERED("C001"),

    // 거래고유번호
    DUPLICATE_TRANSACTION_NO("D001"),
    ORIGINAL_TRANSFER_NOT_FOUND("D002"),

    SYSTEM_ERROR("9999"),
    ;

    private final String code;
}
