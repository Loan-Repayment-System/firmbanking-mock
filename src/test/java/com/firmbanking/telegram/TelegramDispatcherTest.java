package com.firmbanking.telegram;

import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.codec.CommonField;
import com.firmbanking.telegram.codec.InvalidTelegramException;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.codec.TelegramField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static com.firmbanking.telegram.codec.TelegramFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("TelegramDispatcher")
class TelegramDispatcherTest {

    // 거래가 채우는 개별부 응답 필드 자리
    private static final TelegramField RESULT_FIELD = new TelegramField(100, 4);

    private TelegramController controller;
    private TelegramDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        controller = controller("2000", "100");
        dispatcher = new TelegramDispatcher(List.of(controller));
    }

    @Nested
    @DisplayName("2000/100 거래가 등록됐을 때")
    class RegisteredTransaction {

        @Test
        @DisplayName("거래가 개별부를 채우고 0000을 돌려주면 전문구분 2100, 응답코드 0000이고 그 밖의 바이트는 요청과 같다")
        void 거래가_채운_응답을_돌려준다() {
            // given
            byte[] request = request();
            willWriteAndReturn(ResponseCode.SUCCESS);
            byte[] expected = request();
            put(expected, CommonField.TELEGRAM_TYPE_CODE, "2100");
            put(expected, CommonField.RESPONSE_CODE, "0000");
            put(expected, RESULT_FIELD, "DONE");

            // when
            byte[] response = dispatcher.dispatch(request);

            // then
            assertThat(response).isEqualTo(expected);
        }

        @Test
        @DisplayName("거래가 B003을 돌려주면 전문구분 2100, 응답코드 B003으로 응답한다")
        void 업무_거절_응답코드로_응답한다() {
            // given
            byte[] request = request();
            willWriteAndReturn(ResponseCode.INSUFFICIENT_BALANCE);

            // when
            byte[] response = dispatcher.dispatch(request);

            // then
            assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("2100");
            assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("B003");
        }

        // 전문 명세 5절 A001: 개별부 필드 형식이 틀림
        @Test
        @DisplayName("거래가 개별부를 일부 쓴 뒤 InvalidTelegramException을 던지면 A001이고 개별부는 요청과 같다")
        void 개별부_형식_오류면_A001로_응답한다() {
            // given
            byte[] request = request();
            willWriteAndThrow(new InvalidTelegramException("개별부 형식 오류"));
            byte[] expected = request();
            put(expected, CommonField.TELEGRAM_TYPE_CODE, "2100");
            put(expected, CommonField.RESPONSE_CODE, "A001");

            // when
            byte[] response = dispatcher.dispatch(request);

            // then
            assertThat(response).isEqualTo(expected);
        }

        // 전문 명세 5절 9999: 펌뱅킹 내부 오류
        @Test
        @DisplayName("거래가 개별부를 일부 쓴 뒤 RuntimeException을 던지면 9999이고 개별부는 요청과 같다")
        void 예상_못한_예외면_9999로_응답한다() {
            // given
            byte[] request = request();
            willWriteAndThrow(new RuntimeException("DB 오류"));
            byte[] expected = request();
            put(expected, CommonField.TELEGRAM_TYPE_CODE, "2100");
            put(expected, CommonField.RESPONSE_CODE, "9999");

            // when
            byte[] response = dispatcher.dispatch(request);

            // then
            assertThat(response).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("공통부 형식이 틀렸을 때")
    class InvalidCommonPart {

        // 전문 명세 5절 A001: 필드 형식이 틀림
        @Test
        @DisplayName("전문번호가 00012X면 전문구분 2100, 응답코드 A001로 응답하고 거래를 호출하지 않는다")
        void 전문번호가_숫자가_아니면_A001로_응답한다() {
            // given
            byte[] request = request();
            put(request, CommonField.TELEGRAM_NO, "00012X");

            // when
            byte[] response = dispatcher.dispatch(request);

            // then
            assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("2100");
            assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A001");
            then(controller).should(never()).process(any(), any());
        }

        // 전문 명세 2절: 전문구분코드가 2000·6000·7000이 아니면 그대로 둔다
        @Test
        @DisplayName("전문구분코드가 20A0이면 전문구분 20A0 그대로, 응답코드 A001로 응답한다")
        void 전문구분코드가_숫자가_아니면_그대로_두고_A001로_응답한다() {
            // given
            byte[] request = request();
            put(request, CommonField.TELEGRAM_TYPE_CODE, "20A0");

            // when
            byte[] response = dispatcher.dispatch(request);

            // then
            assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("20A0");
            assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A001");
        }
    }

    @Nested
    @DisplayName("등록되지 않은 전문구분/업무구분 조합일 때")
    class UnsupportedTransaction {

        // 전문 명세 5절 A002: 전문구분코드·업무구분코드 조합이 1절에 없음
        @Test
        @DisplayName("2000/300이면 전문구분 2100, 응답코드 A002로 응답한다")
        void 업무구분이_없으면_A002로_응답한다() {
            // given
            byte[] request = request();
            put(request, CommonField.BUSINESS_TYPE_CODE, "300");

            // when
            byte[] response = dispatcher.dispatch(request);

            // then
            assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("2100");
            assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A002");
        }

        // 전문 명세 2절: 전문구분코드가 2000·6000·7000이 아니면 그대로 둔다
        @Test
        @DisplayName("3000/100이면 전문구분 3000 그대로, 응답코드 A002로 응답한다")
        void 전문구분이_없으면_그대로_두고_A002로_응답한다() {
            // given
            byte[] request = request();
            put(request, CommonField.TELEGRAM_TYPE_CODE, "3000");

            // when
            byte[] response = dispatcher.dispatch(request);

            // then
            assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("3000");
            assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A002");
        }
    }

    @Test
    @DisplayName("2000/100 거래가 둘이면 디스패처를 만들 때 IllegalStateException을 던진다")
    void 같은_조합의_거래가_둘이면_예외를_던진다() {
        // given
        List<TelegramController> duplicated = List.of(controller("2000", "100"), controller("2000", "100"));

        // when, then
        assertThatThrownBy(() -> new TelegramDispatcher(duplicated))
                .isInstanceOf(IllegalStateException.class);
    }

    private static TelegramController controller(String telegramTypeCode, String businessTypeCode) {
        TelegramController controller = mock(TelegramController.class);
        given(controller.telegramTypeCode()).willReturn(telegramTypeCode);
        given(controller.businessTypeCode()).willReturn(businessTypeCode);
        return controller;
    }

    private void willWriteAndReturn(ResponseCode responseCode) {
        willAnswer(invocation -> {
            invocation.<ResponseTelegram>getArgument(1).writeAN(RESULT_FIELD, "DONE");
            return responseCode;
        }).given(controller).process(any(), any());
    }

    private void willWriteAndThrow(RuntimeException exception) {
        willAnswer(invocation -> {
            invocation.<ResponseTelegram>getArgument(1).writeAN(RESULT_FIELD, "DONE");
            throw exception;
        }).given(controller).process(any(), any());
    }
}
