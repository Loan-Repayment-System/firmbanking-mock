package com.firmbanking.telegram.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static com.firmbanking.telegram.codec.TelegramFixture.blank;
import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FieldDecoder")
class FieldDecoderTest {

    private static final TelegramField FIELD_6 = new TelegramField(100, 6);
    private static final TelegramField FIELD_8 = new TelegramField(100, 8);
    private static final TelegramField FIELD_12 = new TelegramField(100, 12);
    private static final TelegramField FIELD_13 = new TelegramField(100, 13);

    @Nested
    @DisplayName("N 필드를 읽을 때")
    class ReadN {

        @Test
        @DisplayName("000123이면 문자열 000123으로 읽는다")
        void 숫자를_그대로_읽는다() {
            // given
            byte[] bytes = with(FIELD_6, "000123");

            // when
            String result = FieldDecoder.readN(bytes, FIELD_6);

            // then
            assertThat(result).isEqualTo("000123");
        }

        @Test
        @DisplayName("금액 0000000010000이면 10000으로 읽는다")
        void 금액을_BigDecimal로_읽는다() {
            // given
            byte[] bytes = with(FIELD_13, "0000000010000");

            // when
            BigDecimal result = FieldDecoder.readAmount(bytes, FIELD_13);

            // then
            assertThat(result).isEqualByComparingTo("10000");
        }

        // 전문 명세 2절: N은 오른쪽 정렬, 남는 자리는 앞에 0
        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"00 123", "00A123", "-00123"})
        @DisplayName("공백·문자·부호가 섞이면 InvalidTelegramException을 던진다")
        void 숫자가_아니면_예외를_던진다(String value) {
            // given
            byte[] bytes = with(FIELD_6, value);

            // when, then
            assertThatThrownBy(() -> FieldDecoder.readN(bytes, FIELD_6))
                    .isInstanceOf(InvalidTelegramException.class);
        }
    }

    @Nested
    @DisplayName("AN 필드를 읽을 때")
    class ReadAN {

        @Test
        @DisplayName("김철수 뒤에 SPACE 6개가 있으면 김철수로 읽는다")
        void 뒤쪽_SPACE를_뺀다() {
            // given
            byte[] bytes = with(FIELD_12, "김철수");

            // when
            String result = FieldDecoder.readAN(bytes, FIELD_12);

            // then
            assertThat(result).isEqualTo("김철수");
        }

        @Test
        @DisplayName("모두 SPACE면 빈 문자열로 읽는다")
        void 모두_SPACE면_빈_문자열이다() {
            // given
            byte[] bytes = blank();

            // when
            String result = FieldDecoder.readAN(bytes, FIELD_12);

            // then
            assertThat(result).isEmpty();
        }

        // 전문 명세 5절 A001: AN 필드가 EUC-KR이 아님
        @Test
        @DisplayName("EUC-KR이 아닌 바이트 0xFF 0xFF가 있으면 InvalidTelegramException을 던진다")
        void EUC_KR이_아니면_예외를_던진다() {
            // given
            byte[] bytes = blank();
            put(bytes, FIELD_12, new byte[]{(byte) 0xFF, (byte) 0xFF});

            // when, then
            assertThatThrownBy(() -> FieldDecoder.readAN(bytes, FIELD_12))
                    .isInstanceOf(InvalidTelegramException.class);
        }
    }

    @Nested
    @DisplayName("날짜·시각 필드를 읽을 때")
    class ReadDateTime {

        @ParameterizedTest(name = "[{0}]")
        @CsvSource({"20261029, 2026-10-29", "20240229, 2024-02-29"})
        @DisplayName("있는 날짜면 LocalDate로 읽는다")
        void 있는_날짜를_읽는다(String value, LocalDate expected) {
            // given
            byte[] bytes = with(FIELD_8, value);

            // when
            LocalDate result = FieldDecoder.readDate(bytes, FIELD_8);

            // then
            assertThat(result).isEqualTo(expected);
        }

        // 전문 명세 5절 A001: 없는 날짜·시각
        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"20260230", "20250229", "20261301"})
        @DisplayName("없는 날짜면 InvalidTelegramException을 던진다")
        void 없는_날짜면_예외를_던진다(String value) {
            // given
            byte[] bytes = with(FIELD_8, value);

            // when, then
            assertThatThrownBy(() -> FieldDecoder.readDate(bytes, FIELD_8))
                    .isInstanceOf(InvalidTelegramException.class);
        }

        @ParameterizedTest(name = "[{0}]")
        @CsvSource({"143005, 14:30:05", "000000, 00:00:00", "235959, 23:59:59"})
        @DisplayName("있는 시각이면 LocalTime으로 읽는다")
        void 있는_시각을_읽는다(String value, LocalTime expected) {
            // given
            byte[] bytes = with(FIELD_6, value);

            // when
            LocalTime result = FieldDecoder.readTime(bytes, FIELD_6);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"240000", "126000", "120060"})
        @DisplayName("없는 시각이면 InvalidTelegramException을 던진다")
        void 없는_시각이면_예외를_던진다(String value) {
            // given
            byte[] bytes = with(FIELD_6, value);

            // when, then
            assertThatThrownBy(() -> FieldDecoder.readTime(bytes, FIELD_6))
                    .isInstanceOf(InvalidTelegramException.class);
        }
    }
}
