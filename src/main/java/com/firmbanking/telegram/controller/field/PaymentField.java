package com.firmbanking.telegram.controller.field;

import com.firmbanking.telegram.codec.TelegramField;

/**
 * 지급이체 2000/100 개별부 필드 위치 (전문 명세 4.1절).
 */
public final class PaymentField {

    public static final TelegramField WITHDRAWAL_ACCOUNT_NO = new TelegramField(100, 15);
    public static final TelegramField AMOUNT = new TelegramField(129, 13);
    public static final TelegramField BALANCE_SIGN = new TelegramField(142, 1);
    public static final TelegramField BALANCE_AFTER = new TelegramField(143, 13);
    public static final TelegramField DEPOSIT_BANK_CODE = new TelegramField(156, 3);
    public static final TelegramField DEPOSIT_ACCOUNT_NO = new TelegramField(159, 15);
    public static final TelegramField FEE = new TelegramField(174, 9);
    public static final TelegramField MEMO = new TelegramField(199, 14);

    private PaymentField() {
    }
}
