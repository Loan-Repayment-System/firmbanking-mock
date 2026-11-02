package com.firmbanking.telegram.codec;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 공통부에서 읽은 값.
 */
@Getter
@Builder
public class CommonPart {

    private final String companyNo;
    private final String bankCode;
    private final String telegramTypeCode;
    private final String businessTypeCode;
    private final String telegramNo;
    private final LocalDate sendDate;
    private final LocalTime sendTime;
}
