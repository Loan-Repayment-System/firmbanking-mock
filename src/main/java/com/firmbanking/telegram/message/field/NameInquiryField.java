package com.firmbanking.telegram.message.field;

import com.firmbanking.telegram.codec.TelegramField;

/**
 * 계좌성명 조회 6000/100 개별부 필드 위치 (전문 명세 4.5절).
 */
public final class NameInquiryField {

    public static final TelegramField ACCOUNT_NO = new TelegramField(100, 15);
    public static final TelegramField REAL_NAME_NO = new TelegramField(115, 13);
    public static final TelegramField HOLDER_NAME = new TelegramField(128, 12);
    public static final TelegramField BANK_CODE = new TelegramField(140, 3);
    public static final TelegramField AMOUNT = new TelegramField(143, 13);

    private NameInquiryField() {
    }
}
