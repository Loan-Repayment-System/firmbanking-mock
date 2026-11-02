package com.firmbanking.telegram.codec;

import com.firmbanking.global.code.ResponseCode;

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
import java.util.Map;

/**
 * 전문 본문 300바이트. 필드는 offset·길이로 읽고 쓴다.
 * 요청 형식 오류는 InvalidTelegramException(A001), 응답에 쓸 값이 틀리면 코드 버그라 IllegalArgumentException(9999)이다.
 */
public class Telegram {

    public static final int LENGTH = 300;

    static final Charset CHARSET = Charset.forName("EUC-KR");

    private static final byte SPACE = ' ';
    private static final byte ZERO = '0';

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HHmmss").withResolverStyle(ResolverStyle.STRICT);

    // 요청 전문구분코드 → 응답 전문구분코드. 여기 없는 값은 응답에서도 그대로 둔다.
    private static final Map<String, String> RESPONSE_TELEGRAM_TYPE_CODES = Map.of(
            "2000", "2100",
            "6000", "6100",
            "7000", "7100");

    private final byte[] bytes;

    public Telegram(byte[] bytes) {
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("전문 본문은 " + LENGTH + "바이트다: " + bytes.length);
        }
        this.bytes = bytes.clone();
    }

    public byte[] toBytes() {
        return bytes.clone();
    }

    /**
     * 요청을 복사해 전문구분코드를 응답 코드로 바꾸고 응답코드를 채운다. 요청은 바뀌지 않는다.
     */
    public Telegram toResponse(ResponseCode responseCode) {
        Telegram response = new Telegram(bytes);
        String responseTelegramTypeCode = RESPONSE_TELEGRAM_TYPE_CODES.get(readAscii(CommonPart.TELEGRAM_TYPE_CODE));
        if (responseTelegramTypeCode != null) {
            response.writeAN(CommonPart.TELEGRAM_TYPE_CODE, responseTelegramTypeCode);
        }
        response.writeAN(CommonPart.RESPONSE_CODE, responseCode.getCode());
        return response;
    }

    public String readN(TelegramField field) {
        for (int i = field.offset(); i < field.offset() + field.length(); i++) {
            if (bytes[i] < '0' || bytes[i] > '9') {
                throw new InvalidTelegramException("N 필드에 숫자가 아닌 값이 있다: " + describe(field));
            }
        }
        return readAscii(field);
    }

    public BigDecimal readAmount(TelegramField field) {
        return new BigDecimal(readN(field));
    }

    public LocalDate readDate(TelegramField field) {
        String value = readN(field);
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new InvalidTelegramException("없는 날짜다: " + value + " " + describe(field));
        }
    }

    public LocalTime readTime(TelegramField field) {
        String value = readN(field);
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new InvalidTelegramException("없는 시각이다: " + value + " " + describe(field));
        }
    }

    /**
     * 뒤쪽 SPACE를 뺀 값을 돌려준다.
     */
    public String readAN(TelegramField field) {
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

    public void writeN(TelegramField field, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("N 필드에 음수를 쓸 수 없다: " + value);
        }
        writeDigits(field, Long.toString(value));
    }

    public void writeAmount(TelegramField field, BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("N 필드에 음수를 쓸 수 없다: " + value);
        }
        try {
            writeDigits(field, value.toBigIntegerExact().toString());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("금액은 원 단위다: " + value);
        }
    }

    /**
     * 왼쪽부터 채우고 남는 자리는 SPACE. 길이를 넘으면 글자 경계에서 자른다.
     */
    public void writeAN(TelegramField field, String value) {
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

    private void writeDigits(TelegramField field, String digits) {
        if (digits.length() > field.length()) {
            throw new IllegalArgumentException("N 필드 길이를 넘는다: " + digits + " " + describe(field));
        }
        int start = field.offset() + field.length() - digits.length();
        Arrays.fill(bytes, field.offset(), start, ZERO);
        System.arraycopy(digits.getBytes(StandardCharsets.US_ASCII), 0, bytes, start, digits.length());
    }

    private String readAscii(TelegramField field) {
        return new String(bytes, field.offset(), field.length(), StandardCharsets.US_ASCII);
    }

    private static String describe(TelegramField field) {
        return "(offset=" + field.offset() + ", length=" + field.length() + ")";
    }
}
