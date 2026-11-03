package com.firmbanking.telegram.controller;

import com.firmbanking.telegram.codec.CommonField;
import com.firmbanking.telegram.codec.TelegramFixture;
import com.firmbanking.telegram.controller.field.TransferInquiryField;

import static com.firmbanking.telegram.codec.TelegramFixture.put;

/**
 * 이체결과조회 7000/100 요청 바이트. 응답 필드는 N이면 0, AN이면 SPACE다.
 */
final class TransferInquiryFixture {

    private TransferInquiryFixture() {
    }

    static byte[] request(String originalSentDate, String originalMessageNo) {
        byte[] bytes = TelegramFixture.request();
        put(bytes, CommonField.TELEGRAM_TYPE_CODE, "7000");
        put(bytes, TransferInquiryField.ORIGINAL_MESSAGE_NO, originalMessageNo);
        put(bytes, TransferInquiryField.ORIGINAL_SENT_DATE, originalSentDate);
        put(bytes, TransferInquiryField.DEPOSIT_BANK_CODE, "000");
        put(bytes, TransferInquiryField.REQUESTED_AMOUNT, "0000000000000");
        put(bytes, TransferInquiryField.PROCESSED_AMOUNT, "0000000000000");
        put(bytes, TransferInquiryField.UNPROCESSED_AMOUNT, "0000000000000");
        put(bytes, TransferInquiryField.FEE, "000000000");
        put(bytes, TransferInquiryField.TRANSFER_TIME, "000000");
        return bytes;
    }
}
