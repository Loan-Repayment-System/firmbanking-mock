package com.firmbanking.telegram.codec;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;

/**
 * 전문 필드 ↔ Java 값. N은 오른쪽 정렬·앞 0, AN은 왼쪽 정렬·뒤 SPACE, 인코딩은 EUC-KR이다 (전문 명세 2절).
 * 요청 형식 오류는 InvalidTelegramException(A001), 응답에 쓸 값이 틀리면 코드 버그라 IllegalArgumentException(9999)이다.
 */
public final class FieldConverter {

    static final Charset CHARSET = Charset.forName("EUC-KR");

    private static final byte SPACE = ' ';
    private static final byte ZERO = '0';

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HHmmss").withResolverStyle(ResolverStyle.STRICT);

    private FieldConverter() {
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

    public static void writeN(byte[] bytes, TelegramField field, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("N 필드에 음수를 쓸 수 없다: " + value);
        }
        writeDigits(bytes, field, Long.toString(value));
    }

    public static void writeAmount(byte[] bytes, TelegramField field, BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("N 필드에 음수를 쓸 수 없다: " + value);
        }
        try {
            writeDigits(bytes, field, value.toBigIntegerExact().toString());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("금액은 원 단위다: " + value);
        }
    }

    /**
     * 왼쪽부터 채우고 남는 자리는 SPACE. 길이를 넘으면 글자 경계에서 자른다.
     */
    public static void writeAN(byte[] bytes, TelegramField field, String value) {
        CharsetEncoder encoder = CHARSET.newEncoder();
        int position = field.offset();
        int end = field.offset() + field.length();
        for (int i = 0; i < value.length(); ) {
            int codePointLength = Character.charCount(value.codePointAt(i));
            String character = value.substring(i, i + codePointLength);
            if (!encoder.canEncode(character)) {
                throw new IllegalArgumentException("EUC-KR로 쓸 수 없는 문자다: " + character);
            }
            byte[] encoded = character.getBytes(CHARSET);
            if (position + encoded.length > end) {
                break;
            }
            System.arraycopy(encoded, 0, bytes, position, encoded.length);
            position += encoded.length;
            i += codePointLength;
        }
        Arrays.fill(bytes, position, end, SPACE);
    }

    private static void writeDigits(byte[] bytes, TelegramField field, String digits) {
        if (digits.length() > field.length()) {
            throw new IllegalArgumentException("N 필드 길이를 넘는다: " + digits + " " + describe(field));
        }
        int start = field.offset() + field.length() - digits.length();
        Arrays.fill(bytes, field.offset(), start, ZERO);
        System.arraycopy(digits.getBytes(StandardCharsets.US_ASCII), 0, bytes, start, digits.length());
    }

    private static String describe(TelegramField field) {
        return "(offset=" + field.offset() + ", length=" + field.length() + ")";
    }
}
