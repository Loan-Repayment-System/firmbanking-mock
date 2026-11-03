package com.firmbanking.telegram.controller;

import com.firmbanking.telegram.codec.CommonField;
import com.firmbanking.telegram.codec.TelegramFixture;
import com.firmbanking.telegram.controller.field.CollectionField;

import static com.firmbanking.telegram.codec.TelegramFixture.put;

/**
 * 집금이체 2000/200 요청 바이트. 입금후잔액 0, 수수료 0, 적요 상환금이다.
 */
final class CollectionFixture {

    private CollectionFixture() {
    }

    static byte[] request(String withdrawalAccountNo, long amount, String depositBankCode, String depositAccountNo) {
        byte[] bytes = TelegramFixture.request();
        put(bytes, CommonField.BUSINESS_TYPE_CODE, "200");
        put(bytes, CollectionField.WITHDRAWAL_ACCOUNT_NO, withdrawalAccountNo);
        put(bytes, CollectionField.AMOUNT, String.format("%013d", amount));
        put(bytes, CollectionField.BALANCE_AFTER, "0000000000000");
        put(bytes, CollectionField.DEPOSIT_BANK_CODE, depositBankCode);
        put(bytes, CollectionField.DEPOSIT_ACCOUNT_NO, depositAccountNo);
        put(bytes, CollectionField.FEE, "000000000");
        put(bytes, CollectionField.MEMO, "상환금");
        return bytes;
    }
}
