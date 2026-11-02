package com.firmbanking.telegram.codec;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 요청 전문. 읽기만 한다. TelegramConverter가 만든다.
 */
public final class RequestTelegram {

    private final byte[] bytes;

    @Getter
    private final CommonPart commonPart;

    RequestTelegram(byte[] bytes, CommonPart commonPart) {
        this.bytes = bytes;
        this.commonPart = commonPart;
    }

    public String readN(TelegramField field) {
        return FieldDecoder.readN(bytes, field);
    }

    public BigDecimal readAmount(TelegramField field) {
        return FieldDecoder.readAmount(bytes, field);
    }

    public LocalDate readDate(TelegramField field) {
        return FieldDecoder.readDate(bytes, field);
    }

    public LocalTime readTime(TelegramField field) {
        return FieldDecoder.readTime(bytes, field);
    }

    public String readAN(TelegramField field) {
        return FieldDecoder.readAN(bytes, field);
    }
}
