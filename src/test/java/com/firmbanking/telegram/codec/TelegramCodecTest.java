package com.firmbanking.telegram.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.firmbanking.telegram.codec.TelegramFixture.request;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TelegramCodec")
class TelegramCodecTest {

    // 전문 명세 2절: 길이 헤더는 4바이트 ASCII 숫자 0300
    @Test
    @DisplayName("길이 헤더가 0300이면 유효하다")
    void 길이_헤더_0300은_유효하다() {
        // given
        byte[] header = "0300".getBytes(StandardCharsets.US_ASCII);

        // when
        boolean result = TelegramCodec.isValidLengthHeader(header);

        // then
        assertThat(result).isTrue();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"0299", "0301", "03A0", "    "})
    @DisplayName("길이 헤더가 0300이 아니면 유효하지 않다")
    void 길이_헤더가_0300이_아니면_유효하지_않다(String value) {
        // given
        byte[] header = value.getBytes(StandardCharsets.US_ASCII);

        // when
        boolean result = TelegramCodec.isValidLengthHeader(header);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("encode하면 길이 헤더 0300 뒤에 본문 300바이트가 붙은 304바이트가 된다")
    void 길이_헤더를_붙여_쓴다() {
        // given
        byte[] body = request();
        Telegram telegram = new Telegram(body);

        // when
        byte[] result = TelegramCodec.encode(telegram);

        // then
        assertThat(result).hasSize(304);
        assertThat(new String(result, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("0300");
        assertThat(Arrays.copyOfRange(result, 4, 304)).isEqualTo(body);
    }
}
