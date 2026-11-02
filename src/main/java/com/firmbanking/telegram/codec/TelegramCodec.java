package com.firmbanking.telegram.codec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 길이 헤더(4바이트 ASCII 숫자) + 본문 300바이트.
 */
public final class TelegramCodec {

    public static final int LENGTH_HEADER_SIZE = 4;

    private static final byte[] LENGTH_HEADER =
            String.format("%04d", Telegram.LENGTH).getBytes(StandardCharsets.US_ASCII);

    private TelegramCodec() {
    }

    /**
     * 0300이 아니면 응답 없이 연결을 닫는다.
     */
    public static boolean isValidLengthHeader(byte[] header) {
        return Arrays.equals(header, LENGTH_HEADER);
    }

    public static byte[] encode(Telegram telegram) {
        byte[] frame = new byte[LENGTH_HEADER_SIZE + Telegram.LENGTH];
        System.arraycopy(LENGTH_HEADER, 0, frame, 0, LENGTH_HEADER_SIZE);
        System.arraycopy(telegram.toBytes(), 0, frame, LENGTH_HEADER_SIZE, Telegram.LENGTH);
        return frame;
    }
}
