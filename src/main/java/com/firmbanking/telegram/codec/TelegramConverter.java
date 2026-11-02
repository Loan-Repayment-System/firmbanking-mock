package com.firmbanking.telegram.codec;

/**
 * 요청 바이트 → RequestTelegram. 300바이트인지 확인하고 공통부를 읽는다.
 * 공통부 형식이 틀리면 InvalidTelegramException(A001), 길이가 틀리면 코드 버그라 IllegalArgumentException(9999)이다.
 */
public final class TelegramConverter {

    private TelegramConverter() {
    }

    public static RequestTelegram read(byte[] bytes) {
        if (bytes.length != TelegramField.TELEGRAM_LENGTH) {
            throw new IllegalArgumentException("전문은 300바이트다: " + bytes.length);
        }
        byte[] copy = bytes.clone();
        return new RequestTelegram(copy, CommonPartConverter.read(copy));
    }
}
