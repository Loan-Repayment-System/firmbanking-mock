package com.firmbanking.telegram.controller;

import com.firmbanking.telegram.codec.CommonField;
import com.firmbanking.telegram.codec.TelegramFixture;
import com.firmbanking.telegram.controller.field.WithdrawalConsentField;

import static com.firmbanking.telegram.codec.TelegramFixture.put;

/**
 * 수납출금계좌 등록 2000/550 요청 바이트. 납부자번호 L0001, 신청일자 20261103이다.
 */
final class WithdrawalConsentFixture {

    private WithdrawalConsentFixture() {
    }

    static byte[] request(String bankCode, String accountNo, String applicationType) {
        byte[] bytes = TelegramFixture.request();
        put(bytes, CommonField.BUSINESS_TYPE_CODE, "550");
        put(bytes, WithdrawalConsentField.BANK_CODE, bankCode);
        put(bytes, WithdrawalConsentField.ACCOUNT_NO, accountNo);
        put(bytes, WithdrawalConsentField.APPLICATION_TYPE, applicationType);
        put(bytes, WithdrawalConsentField.APPLIED_DATE, "20261103");
        put(bytes, WithdrawalConsentField.PAYER_NO, "L0001");
        return bytes;
    }
}
