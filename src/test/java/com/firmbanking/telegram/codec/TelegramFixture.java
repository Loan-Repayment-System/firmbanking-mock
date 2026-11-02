package com.firmbanking.telegram.codec;

import java.util.Arrays;

/**
 * 테스트용 전문 바이트를 만든다.
 */
public final class TelegramFixture {

    public static final int LENGTH = 300;

    private TelegramFixture() {
    }

    public static byte[] blank() {
        byte[] bytes = new byte[LENGTH];
        Arrays.fill(bytes, (byte) ' ');
        return bytes;
    }

    /**
     * 지급이체 2000/100 요청의 정상 공통부. 개별부는 SPACE다.
     */
    public static byte[] request() {
        byte[] bytes = blank();
        put(bytes, CommonField.COMPANY_NO, "CAPITAL00001");
        put(bytes, CommonField.BANK_CODE, "999");
        put(bytes, CommonField.TELEGRAM_TYPE_CODE, "2000");
        put(bytes, CommonField.BUSINESS_TYPE_CODE, "100");
        put(bytes, CommonField.SEND_COUNT, "1");
        put(bytes, CommonField.TELEGRAM_NO, "000123");
        put(bytes, CommonField.SEND_DATE, "20261029");
        put(bytes, CommonField.SEND_TIME, "143005");
        return bytes;
    }

    /**
     * value를 EUC-KR로 필드 앞에서부터 넣고 남는 자리는 SPACE로 둔다.
     */
    public static void put(byte[] bytes, TelegramField field, String value) {
        put(bytes, field, value.getBytes(FieldDecoder.CHARSET));
    }

    public static void put(byte[] bytes, TelegramField field, byte[] value) {
        Arrays.fill(bytes, field.offset(), field.offset() + field.length(), (byte) ' ');
        System.arraycopy(value, 0, bytes, field.offset(), value.length);
    }

    public static byte[] with(TelegramField field, String value) {
        byte[] bytes = blank();
        put(bytes, field, value);
        return bytes;
    }

    public static String read(byte[] bytes, TelegramField field) {
        return new String(bytes, field.offset(), field.length(), FieldDecoder.CHARSET);
    }
}
