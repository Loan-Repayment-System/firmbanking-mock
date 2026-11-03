package com.firmbanking.telegram.controller.field;

import com.firmbanking.telegram.codec.TelegramField;

/**
 * 수납출금계좌 등록 2000/550 개별부 필드 위치 (전문 명세 4.4절).
 */
public final class WithdrawalConsentField {

    public static final TelegramField PROCESS_SEQUENCE = new TelegramField(101, 7);
    public static final TelegramField BANK_CODE = new TelegramField(108, 3);
    public static final TelegramField ACCOUNT_NO = new TelegramField(111, 16);
    public static final TelegramField APPLICATION_TYPE = new TelegramField(127, 1);
    public static final TelegramField APPLIED_DATE = new TelegramField(137, 8);
    public static final TelegramField PROCESSED = new TelegramField(145, 1);
    public static final TelegramField FAILURE_CODE = new TelegramField(146, 4);
    public static final TelegramField PAYER_NO = new TelegramField(164, 20);

    private WithdrawalConsentField() {
    }
}
