package com.firmbanking.telegram.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TelegramConverter")
class TelegramConverterTest {

    private static final TelegramField ACCOUNT_NO = new TelegramField(100, 15);

    @Test
    @DisplayName("정상 지급이체 요청이면 공통부의 전문구분코드 2000, 업무구분코드 100을 읽는다")
    void 공통부를_읽는다() {
        // given
        byte[] bytes = request();

        // when
        RequestTelegram result = TelegramConverter.read(bytes);

        // then
        assertThat(result.getCommonPart().getTelegramTypeCode()).isEqualTo("2000");
        assertThat(result.getCommonPart().getBusinessTypeCode()).isEqualTo("100");
    }

    @Test
    @DisplayName("개별부 문자 칸에 김철수와 SPACE가 있으면 그 칸을 김철수로 읽는다")
    void 개별부를_읽는다() {
        // given
        byte[] bytes = request();
        put(bytes, ACCOUNT_NO, "김철수");

        // when
        RequestTelegram result = TelegramConverter.read(bytes);

        // then
        assertThat(result.readAN(ACCOUNT_NO)).isEqualTo("김철수");
    }

    @Test
    @DisplayName("299바이트면 IllegalArgumentException을 던진다")
    void 길이가_300바이트가_아니면_예외를_던진다() {
        // given
        byte[] bytes = new byte[299];

        // when, then
        assertThatThrownBy(() -> TelegramConverter.read(bytes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 전문 명세 5절 A001: 필드 형식이 틀림
    @Test
    @DisplayName("공통부 전문번호가 00012X면 InvalidTelegramException을 던진다")
    void 공통부_형식이_틀리면_예외를_던진다() {
        // given
        byte[] bytes = request();
        put(bytes, CommonField.TELEGRAM_NO, "00012X");

        // when, then
        assertThatThrownBy(() -> TelegramConverter.read(bytes))
                .isInstanceOf(InvalidTelegramException.class);
    }
}
