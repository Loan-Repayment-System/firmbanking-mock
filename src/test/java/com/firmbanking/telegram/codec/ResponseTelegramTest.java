package com.firmbanking.telegram.codec;

import com.firmbanking.global.code.ResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static com.firmbanking.telegram.codec.TelegramFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ResponseTelegram")
class ResponseTelegramTest {

    private static final TelegramField RESULT_FIELD = new TelegramField(100, 4);

    @Nested
    @DisplayName("응답을 완성할 때")
    class Complete {

        // 전문 명세 2절: 요청을 복사한 뒤 전문구분코드와 응답코드를 바꾸고 응답 필드를 채운다
        @Test
        @DisplayName("개별부에 DONE을 쓰고 0000으로 완성하면 전문구분 2100, 응답코드 0000, DONE이고 그 밖의 바이트는 요청과 같다")
        void 개별부와_공통부를_채운_응답을_만든다() {
            // given
            ResponseTelegram response = ResponseTelegram.copyOf(request());
            response.writeAN(RESULT_FIELD, "DONE");
            byte[] expected = request();
            put(expected, CommonField.TELEGRAM_TYPE_CODE, "2100");
            put(expected, CommonField.RESPONSE_CODE, "0000");
            put(expected, RESULT_FIELD, "DONE");

            // when
            byte[] result = response.complete(ResponseCode.SUCCESS);

            // then
            assertThat(result).isEqualTo(expected);
        }

        // 전문 명세 2절: 2000→2100, 6000→6100, 7000→7100
        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"2000, 2100", "6000, 6100", "7000, 7100"})
        @DisplayName("전문구분코드가 2000·6000·7000이면 100을 더한다")
        void 요청_전문구분코드에_100을_더한다(String requestCode, String responseCode) {
            // given
            byte[] bytes = request();
            put(bytes, CommonField.TELEGRAM_TYPE_CODE, requestCode);
            ResponseTelegram response = ResponseTelegram.copyOf(bytes);

            // when
            byte[] result = response.complete(ResponseCode.SUCCESS);

            // then
            assertThat(read(result, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo(responseCode);
        }

        // 전문 명세 2절: 전문구분코드가 2000·6000·7000이 아니면 그대로 둔다
        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"2100", "3000", "20A0"})
        @DisplayName("전문구분코드가 2100, 3000, 20A0이면 그대로 둔다")
        void 그_밖의_전문구분코드는_그대로_둔다(String code) {
            // given
            byte[] bytes = request();
            put(bytes, CommonField.TELEGRAM_TYPE_CODE, code);
            ResponseTelegram response = ResponseTelegram.copyOf(bytes);

            // when
            byte[] result = response.complete(ResponseCode.INVALID_FORMAT);

            // then
            assertThat(read(result, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo(code);
        }
    }

    @Test
    @DisplayName("공통부 전문번호 칸에 쓰면 IllegalArgumentException을 던진다")
    void 공통부에_쓰면_예외를_던진다() {
        // given
        ResponseTelegram response = ResponseTelegram.copyOf(request());

        // when, then
        assertThatThrownBy(() -> response.writeAN(CommonField.TELEGRAM_NO, "000999"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("299바이트로 만들면 IllegalArgumentException을 던진다")
    void 길이가_300바이트가_아니면_예외를_던진다() {
        // given
        byte[] bytes = new byte[299];

        // when, then
        assertThatThrownBy(() -> ResponseTelegram.copyOf(bytes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("개별부에 DONE을 써도 복사한 원래 요청 배열은 바뀌지 않는다")
    void 원래_요청_배열은_바뀌지_않는다() {
        // given
        byte[] bytes = request();
        ResponseTelegram response = ResponseTelegram.copyOf(bytes);

        // when
        response.writeAN(RESULT_FIELD, "DONE");

        // then
        assertThat(bytes).isEqualTo(request());
    }
}
