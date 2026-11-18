package com.firmbanking.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * transfer_request_status 테이블
 */
@Getter
@RequiredArgsConstructor
public enum TransferRequestStatus implements CodeEnum {

    PROCESSING(1),
    COMPLETED(2),
    ERROR(3),
    ;

    private final int id;
}
