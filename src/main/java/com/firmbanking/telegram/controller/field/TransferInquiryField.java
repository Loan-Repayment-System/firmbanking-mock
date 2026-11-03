package com.firmbanking.telegram.controller.field;

import com.firmbanking.telegram.codec.TelegramField;

/**
 * 이체결과조회 7000/100 개별부 필드 위치 (전문 명세 4.3절).
 */
public final class TransferInquiryField {

    public static final TelegramField ORIGINAL_MESSAGE_NO = new TelegramField(100, 6);
    public static final TelegramField ORIGINAL_SENT_DATE = new TelegramField(106, 8);
    public static final TelegramField WITHDRAWAL_ACCOUNT_NO = new TelegramField(114, 15);
    public static final TelegramField DEPOSIT_BANK_CODE = new TelegramField(129, 3);
    public static final TelegramField DEPOSIT_ACCOUNT_NO = new TelegramField(132, 15);
    public static final TelegramField REQUESTED_AMOUNT = new TelegramField(147, 13);
    public static final TelegramField PROCESSED_AMOUNT = new TelegramField(160, 13);
    public static final TelegramField UNPROCESSED_AMOUNT = new TelegramField(173, 13);
    public static final TelegramField FEE = new TelegramField(186, 9);
    public static final TelegramField TRANSFER_TIME = new TelegramField(195, 6);
    public static final TelegramField PROCESS_RESULT = new TelegramField(201, 4);

    private TransferInquiryField() {
    }
}
