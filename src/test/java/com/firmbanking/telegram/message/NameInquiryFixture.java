package com.firmbanking.telegram.message;

import com.firmbanking.telegram.codec.CommonField;
import com.firmbanking.telegram.codec.TelegramFixture;

import static com.firmbanking.telegram.codec.TelegramFixture.put;

/**
 * 계좌성명 조회 6000/100 요청 바이트. 금액은 0이다.
 */
final class NameInquiryFixture {

    private NameInquiryFixture() {
    }

    static byte[] request(String bankCode, String accountNo, String realNameNo) {
        byte[] bytes = TelegramFixture.request();
        put(bytes, CommonField.TELEGRAM_TYPE_CODE, "6000");
        put(bytes, CommonField.BUSINESS_TYPE_CODE, "100");
        put(bytes, NameInquiryMessage.ACCOUNT_NO, accountNo);
        put(bytes, NameInquiryMessage.REAL_NAME_NO, realNameNo);
        put(bytes, NameInquiryMessage.BANK_CODE, bankCode);
        put(bytes, NameInquiryMessage.AMOUNT, "0000000000000");
        return bytes;
    }
}
