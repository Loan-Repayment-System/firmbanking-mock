package com.firmbanking.telegram.controller;

import com.firmbanking.telegram.codec.TelegramFixture;
import com.firmbanking.telegram.controller.field.PaymentField;

import static com.firmbanking.telegram.codec.TelegramFixture.put;

/**
 * 지급이체 2000/100 요청 바이트. 출금후잔액 0, 수수료 0, 적요 대출금이다.
 */
final class PaymentFixture {

    private PaymentFixture() {
    }

    static byte[] request(String withdrawalAccountNo, long amount, String depositBankCode, String depositAccountNo) {
        byte[] bytes = TelegramFixture.request();
        put(bytes, PaymentField.WITHDRAWAL_ACCOUNT_NO, withdrawalAccountNo);
        put(bytes, PaymentField.AMOUNT, String.format("%013d", amount));
        put(bytes, PaymentField.BALANCE_AFTER, "0000000000000");
        put(bytes, PaymentField.DEPOSIT_BANK_CODE, depositBankCode);
        put(bytes, PaymentField.DEPOSIT_ACCOUNT_NO, depositAccountNo);
        put(bytes, PaymentField.FEE, "000000000");
        put(bytes, PaymentField.MEMO, "대출금");
        return bytes;
    }
}
