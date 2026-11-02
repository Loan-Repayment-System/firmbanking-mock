package com.firmbanking.telegram.codec;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 전문 바이트 → Java 값. N은 숫자만, AN은 EUC-KR이고 뒤쪽 SPACE를 뺀다 (전문 명세 2절).
 * 형식이 틀리면 InvalidTelegramException(A001)이다.
 */
public final class FieldDecoder {

    static final Charset CHARSET = Charset.forName("EUC-KR");

    private static final byte SPACE = ' ';

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HHmmss").withResolverStyle(ResolverStyle.STRICT);

    private FieldDecoder() {
    }

    public static String readN(byte[] bytes, TelegramField field) {
        for (int i = field.offset(); i < field.offset() + field.length(); i++) {
            if (bytes[i] < '0' || bytes[i] > '9') {
                throw new InvalidTelegramException("N 필드에 숫자가 아닌 값이 있다: " + describe(field));
            }
        }
        return new String(bytes, field.offset(), field.length(), StandardCharsets.US_ASCII);
    }

    public static BigDecimal readAmount(byte[] bytes, TelegramField field) {
        return new BigDecimal(readN(bytes, field));
    }

    public static LocalDate readDate(byte[] bytes, TelegramField field) {
        String value = readN(bytes, field);
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new InvalidTelegramException("없는 날짜다: " + value + " " + describe(field));
        }
    }

    public static LocalTime readTime(byte[] bytes, TelegramField field) {
        String value = readN(bytes, field);
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new InvalidTelegramException("없는 시각이다: " + value + " " + describe(field));
        }
    }

    /**
     * 뒤쪽 SPACE를 뺀 값을 돌려준다.
     */
    public static String readAN(byte[] bytes, TelegramField field) {
        int end = field.offset() + field.length();
        while (end > field.offset() && bytes[end - 1] == SPACE) {
            end--;
        }
        try {
            return CHARSET.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, field.offset(), end - field.offset()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidTelegramException("AN 필드가 EUC-KR이 아니다: " + describe(field));
        }
    }

    private static String describe(TelegramField field) {
        return "(offset=" + field.offset() + ", length=" + field.length() + ")";
    }
}
