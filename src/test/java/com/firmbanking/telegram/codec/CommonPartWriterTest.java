package com.firmbanking.telegram.codec;

import com.firmbanking.global.code.ResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static com.firmbanking.telegram.codec.TelegramFixture.request;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommonPartWriter")
class CommonPartWriterTest {

    // 전문 명세 2절: 2000→2100, 6000→6100, 7000→7100
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"2000, 2100", "6000, 6100", "7000, 7100"})
    @DisplayName("전문구분코드가 2000·6000·7000이면 100을 더한다")
    void 요청_전문구분코드에_100을_더한다(String requestCode, String responseCode) {
        // given
        byte[] bytes = request();
        put(bytes, CommonField.TELEGRAM_TYPE_CODE, requestCode);

        // when
        CommonPartWriter.writeResponse(bytes, ResponseCode.SUCCESS);

        // then
        assertThat(read(bytes, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo(responseCode);
    }

    // 전문 명세 2절: 전문구분코드가 2000·6000·7000이 아니면 그대로 둔다
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"2100", "3000", "20A0"})
    @DisplayName("전문구분코드가 2100, 3000, 20A0이면 그대로 둔다")
    void 그_밖의_전문구분코드는_그대로_둔다(String code) {
        // given
        byte[] bytes = request();
        put(bytes, CommonField.TELEGRAM_TYPE_CODE, code);

        // when
        CommonPartWriter.writeResponse(bytes, ResponseCode.INVALID_FORMAT);

        // then
        assertThat(read(bytes, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo(code);
    }

    @Test
    @DisplayName("B003을 쓰면 응답코드가 B003이 되고 전문구분코드·응답코드 말고는 모든 바이트가 그대로다")
    void 응답코드를_쓰고_나머지는_그대로_둔다() {
        // given
        byte[] bytes = request();
        byte[] expected = request();
        put(expected, CommonField.TELEGRAM_TYPE_CODE, "2100");
        put(expected, CommonField.RESPONSE_CODE, "B003");

        // when
        CommonPartWriter.writeResponse(bytes, ResponseCode.INSUFFICIENT_BALANCE);

        // then
        assertThat(bytes).isEqualTo(expected);
    }
}
