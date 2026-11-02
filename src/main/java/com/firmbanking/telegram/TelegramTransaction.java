package com.firmbanking.telegram;

import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.codec.RequestTelegram;
import com.firmbanking.telegram.codec.ResponseTelegram;

/**
 * 전문구분코드 + 업무구분코드 하나에 해당하는 거래. 빈으로 등록하면 디스패처가 찾는다.
 */
public interface TelegramTransaction {

    String telegramTypeCode();

    String businessTypeCode();

    /**
     * request에서 개별부를 읽고 response 개별부의 응답 필드를 채운 뒤 응답코드를 돌려준다.
     * 개별부 형식이 틀리면 InvalidTelegramException(A001)을 던지고, 업무 거절은 응답코드로 돌려준다.
     */
    ResponseCode process(RequestTelegram request, ResponseTelegram response);
}
