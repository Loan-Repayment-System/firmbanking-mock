package com.firmbanking.telegram.codec;

/**
 * 요청 전문의 형식 오류. 디스패처가 A001로 바꾼다.
 */
public class InvalidTelegramException extends RuntimeException {

    public InvalidTelegramException(String message) {
        super(message);
    }
}
