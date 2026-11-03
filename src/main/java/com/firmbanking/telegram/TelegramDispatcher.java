package com.firmbanking.telegram;

import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.codec.CommonPart;
import com.firmbanking.telegram.codec.InvalidTelegramException;
import com.firmbanking.telegram.codec.RequestTelegram;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.codec.TelegramConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 요청 전문 300바이트를 거래에 넘기고 응답 전문 300바이트를 만든다.
 * 순서: 공통부 형식(A001) → 거래 조합(A002) → 거래 처리. 예상 못한 예외는 9999다.
 * 응답은 요청 원문을 복사한 ResponseTelegram에 거래가 개별부를 채운 것이다 (전문 명세 2절).
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

    public byte[] dispatch(byte[] bytes) {
        ResponseTelegram response = ResponseTelegram.copyOf(bytes);
        ResponseCode responseCode;
        try {
            RequestTelegram request = TelegramConverter.read(bytes);
            responseCode = process(request, response);
        } catch (InvalidTelegramException e) {
            // 거래가 쓰다 만 개별부를 버린다.
            response = ResponseTelegram.copyOf(bytes);
            responseCode = ResponseCode.INVALID_FORMAT;
        } catch (Exception e) {
            log.error("전문 처리 중 예상 못한 예외", e);
            response = ResponseTelegram.copyOf(bytes);
            responseCode = ResponseCode.SYSTEM_ERROR;
        }
        return response.complete(responseCode);
    }

    private ResponseCode process(RequestTelegram request, ResponseTelegram response) {
        CommonPart commonPart = request.getCommonPart();
        TelegramTransaction transaction =
                transactions.get(key(commonPart.getTelegramTypeCode(), commonPart.getBusinessTypeCode()));
        if (transaction == null) {
            return ResponseCode.UNSUPPORTED_TELEGRAM;
        }
        return transaction.process(request, response);
    }

    private static String key(TelegramTransaction transaction) {
        return key(transaction.telegramTypeCode(), transaction.businessTypeCode());
    }

    private static String key(String telegramTypeCode, String businessTypeCode) {
        return telegramTypeCode + "/" + businessTypeCode;
    }
}
