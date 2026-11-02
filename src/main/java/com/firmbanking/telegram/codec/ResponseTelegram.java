package com.firmbanking.telegram.codec;

import com.firmbanking.global.code.ResponseCode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 응답 전문. 요청 원문을 복사해서 만들고, 거래는 개별부만 쓴다.
 * complete가 전문구분코드(2000·6000·7000이면 +100, 그 밖은 그대로)와 응답코드를 쓴다 (전문 명세 2절).
 */
public final class ResponseTelegram {

    private static final Set<String> REQUEST_TELEGRAM_TYPE_CODES = Set.of("2000", "6000", "7000");
    private static final int RESPONSE_TELEGRAM_TYPE_OFFSET = 100;

    private final byte[] bytes;

    private ResponseTelegram(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * 공통부 형식이 틀린 요청도 그대로 복사한다. 길이가 틀리면 코드 버그라 IllegalArgumentException(9999)이다.
     */
    public static ResponseTelegram copyOf(byte[] request) {
        if (request.length != TelegramField.TELEGRAM_LENGTH) {
            throw new IllegalArgumentException("전문은 300바이트다: " + request.length);
        }
        return new ResponseTelegram(request.clone());
    }

    public void writeN(TelegramField field, long value) {
        checkIndividualPart(field);
        FieldEncoder.writeN(bytes, field, value);
    }

    public void writeAmount(TelegramField field, BigDecimal value) {
        checkIndividualPart(field);
        FieldEncoder.writeAmount(bytes, field, value);
    }

    public void writeAN(TelegramField field, String value) {
        checkIndividualPart(field);
        FieldEncoder.writeAN(bytes, field, value);
    }

    public byte[] complete(ResponseCode responseCode) {
        TelegramField field = CommonField.TELEGRAM_TYPE_CODE;
        String telegramTypeCode = new String(bytes, field.offset(), field.length(), StandardCharsets.US_ASCII);
        if (REQUEST_TELEGRAM_TYPE_CODES.contains(telegramTypeCode)) {
            FieldEncoder.writeN(bytes, field, Long.parseLong(telegramTypeCode) + RESPONSE_TELEGRAM_TYPE_OFFSET);
        }
        FieldEncoder.writeAN(bytes, CommonField.RESPONSE_CODE, responseCode.getCode());
        return bytes.clone();
    }

    private static void checkIndividualPart(TelegramField field) {
        if (field.offset() < TelegramField.INDIVIDUAL_PART_OFFSET) {
            throw new IllegalArgumentException("거래는 공통부를 쓸 수 없다: (offset=" + field.offset() + ")");
        }
    }
}
