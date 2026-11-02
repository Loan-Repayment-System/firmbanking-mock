package com.firmbanking.telegram.codec;

import com.firmbanking.global.code.ResponseCode;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 응답 공통부 쓰기. 전문구분코드가 2000·6000·7000이면 100을 더하고 응답코드를 쓴다 (전문 명세 2절).
 * 그 밖의 전문구분코드는 숫자가 아니어도 그대로 둔다.
 */
public final class CommonPartWriter {

    private static final Set<String> REQUEST_TELEGRAM_TYPE_CODES = Set.of("2000", "6000", "7000");
    private static final int RESPONSE_TELEGRAM_TYPE_OFFSET = 100;

    private CommonPartWriter() {
    }

    public static void writeResponse(byte[] bytes, ResponseCode responseCode) {
        TelegramField field = CommonField.TELEGRAM_TYPE_CODE;
        String telegramTypeCode = new String(bytes, field.offset(), field.length(), StandardCharsets.US_ASCII);
        if (REQUEST_TELEGRAM_TYPE_CODES.contains(telegramTypeCode)) {
            FieldEncoder.writeN(bytes, field, Long.parseLong(telegramTypeCode) + RESPONSE_TELEGRAM_TYPE_OFFSET);
        }
        FieldEncoder.writeAN(bytes, CommonField.RESPONSE_CODE, responseCode.getCode());
    }
}
