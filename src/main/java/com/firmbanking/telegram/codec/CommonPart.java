package com.firmbanking.telegram.codec;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 공통부 100바이트. N 필드가 숫자인지와 전송일자·전송시간이 있는 값인지만 검사하고, 고정값은 검사하지 않는다.
 */
@Getter
public class CommonPart {

    public static final TelegramField IDENTIFICATION_CODE = new TelegramField(0, 9);
    public static final TelegramField COMPANY_NO = new TelegramField(9, 12);
    public static final TelegramField BANK_CODE = new TelegramField(21, 3);
    public static final TelegramField TELEGRAM_TYPE_CODE = new TelegramField(24, 4);
    public static final TelegramField BUSINESS_TYPE_CODE = new TelegramField(28, 3);
    public static final TelegramField SEND_COUNT = new TelegramField(31, 1);
    public static final TelegramField TELEGRAM_NO = new TelegramField(32, 6);
    public static final TelegramField SEND_DATE = new TelegramField(38, 8);
    public static final TelegramField SEND_TIME = new TelegramField(46, 6);
    public static final TelegramField RESPONSE_CODE = new TelegramField(52, 4);

    private final String companyNo;
    private final String bankCode;
    private final String telegramTypeCode;
    private final String businessTypeCode;
    private final String telegramNo;
    private final LocalDate sendDate;
    private final LocalTime sendTime;

    private CommonPart(Telegram telegram) {
        this.companyNo = telegram.readAN(COMPANY_NO);
        this.bankCode = telegram.readN(BANK_CODE);
        this.telegramTypeCode = telegram.readN(TELEGRAM_TYPE_CODE);
        this.businessTypeCode = telegram.readN(BUSINESS_TYPE_CODE);
        telegram.readN(SEND_COUNT);
        this.telegramNo = telegram.readN(TELEGRAM_NO);
        this.sendDate = telegram.readDate(SEND_DATE);
        this.sendTime = telegram.readTime(SEND_TIME);
    }

    public static CommonPart from(Telegram telegram) {
        return new CommonPart(telegram);
    }
}
