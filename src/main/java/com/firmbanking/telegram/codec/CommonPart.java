package com.firmbanking.telegram.codec;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 공통부 100바이트. N 필드가 숫자인지와 전송일자·전송시간이 있는 값인지만 검사하고, 고정값은 검사하지 않는다.
 */
@Getter
public class CommonPart {

    private final String companyNo;
    private final String bankCode;
    private final String telegramTypeCode;
    private final String businessTypeCode;
    private final String telegramNo;
    private final LocalDate sendDate;
    private final LocalTime sendTime;

    private CommonPart(Telegram telegram) {
        this.companyNo = telegram.readAN(CommonField.COMPANY_NO);
        this.bankCode = telegram.readN(CommonField.BANK_CODE);
        this.telegramTypeCode = telegram.readN(CommonField.TELEGRAM_TYPE_CODE);
        this.businessTypeCode = telegram.readN(CommonField.BUSINESS_TYPE_CODE);
        telegram.readN(CommonField.SEND_COUNT);
        this.telegramNo = telegram.readN(CommonField.TELEGRAM_NO);
        this.sendDate = telegram.readDate(CommonField.SEND_DATE);
        this.sendTime = telegram.readTime(CommonField.SEND_TIME);
    }

    public static CommonPart from(Telegram telegram) {
        return new CommonPart(telegram);
    }
}
