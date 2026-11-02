package com.firmbanking.telegram;

import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.codec.CommonPart;
import com.firmbanking.telegram.codec.CommonPartConverter;
import com.firmbanking.telegram.codec.CommonPartWriter;
import com.firmbanking.telegram.codec.InvalidTelegramException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 요청 전문 300바이트를 거래에 넘기고 응답 전문 300바이트를 만든다.
 * 순서: 공통부 형식(A001) → 거래 조합(A002) → 거래 처리. 예상 못한 예외는 9999다.
 * 응답은 요청을 복사한 뒤 거래가 개별부를 채우고, 공통부 전문구분코드·응답코드를 바꾼다 (전문 명세 2절).
 */
@Slf4j
@Component
public class TelegramDispatcher {

    private final Map<String, TelegramTransaction> transactions;

    public TelegramDispatcher(List<TelegramTransaction> transactions) {
        this.transactions = transactions.stream()
                .collect(Collectors.toMap(TelegramDispatcher::key, Function.identity(), (first, second) -> {
                    throw new IllegalStateException("같은 전문구분/업무구분의 거래가 둘이다: " + key(first));
                }));
    }

    public byte[] dispatch(byte[] request) {
        byte[] response = request.clone();
        ResponseCode responseCode;
        try {
            responseCode = process(request, response);
        } catch (InvalidTelegramException e) {
            // 거래가 쓰다 만 개별부를 버린다.
            response = request.clone();
            responseCode = ResponseCode.INVALID_FORMAT;
        } catch (Exception e) {
            log.error("전문 처리 중 예상 못한 예외", e);
            response = request.clone();
            responseCode = ResponseCode.SYSTEM_ERROR;
        }
        CommonPartWriter.writeResponse(response, responseCode);
        return response;
    }

    private ResponseCode process(byte[] request, byte[] response) {
        CommonPart commonPart = CommonPartConverter.read(request);
        TelegramTransaction transaction =
                transactions.get(key(commonPart.getTelegramTypeCode(), commonPart.getBusinessTypeCode()));
        if (transaction == null) {
            return ResponseCode.UNSUPPORTED_TELEGRAM;
        }
        return transaction.process(commonPart, request, response);
    }

    private static String key(TelegramTransaction transaction) {
        return key(transaction.telegramTypeCode(), transaction.businessTypeCode());
    }

    private static String key(String telegramTypeCode, String businessTypeCode) {
        return telegramTypeCode + "/" + businessTypeCode;
    }
}
