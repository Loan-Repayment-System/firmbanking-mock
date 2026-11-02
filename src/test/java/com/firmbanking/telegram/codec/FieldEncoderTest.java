package com.firmbanking.telegram.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;

import static com.firmbanking.telegram.codec.TelegramFixture.blank;
import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FieldEncoder")
class FieldEncoderTest {

    private static final TelegramField FIELD_6 = new TelegramField(100, 6);
    private static final TelegramField FIELD_12 = new TelegramField(100, 12);
    private static final TelegramField FIELD_13 = new TelegramField(100, 13);

    @Nested
    @DisplayName("N 필드를 쓸 때")
    class WriteN {

        @Test
        @DisplayName("6자리 필드에 123을 쓰면 000123이 된다")
        void 앞을_0으로_채운다() {
            // given
            byte[] bytes = blank();

            // when
            FieldEncoder.writeN(bytes, FIELD_6, 123);

            // then
            assertThat(read(bytes, FIELD_6)).isEqualTo("000123");
        }

        @Test
        @DisplayName("13자리 필드에 금액 10000을 쓰면 0000000010000이 된다")
        void 금액을_쓴다() {
            // given
            byte[] bytes = blank();

            // when
            FieldEncoder.writeAmount(bytes, FIELD_13, new BigDecimal("10000"));

            // then
            assertThat(read(bytes, FIELD_13)).isEqualTo("0000000010000");
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(longs = {-1, 1234567})
        @DisplayName("6자리 필드에 음수나 7자리 수를 쓰면 IllegalArgumentException을 던진다")
        void 음수나_자릿수_초과면_예외를_던진다(long value) {
            // given
            byte[] bytes = blank();

            // when, then
            assertThatThrownBy(() -> FieldEncoder.writeN(bytes, FIELD_6, value))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("AN 필드를 쓸 때")
    class WriteAN {

        @Test
        @DisplayName("12바이트 필드에 김철수를 쓰면 김철수 뒤에 SPACE 6개가 붙는다")
        void 남는_자리는_SPACE로_채운다() {
            // given
            byte[] bytes = blank();

            // when
            FieldEncoder.writeAN(bytes, FIELD_12, "김철수");

            // then
            assertThat(read(bytes, FIELD_12)).isEqualTo("김철수      ");
        }

        // 전문 명세 4.5: 예금주명1은 한글 6자까지, 넘치면 자름
        @Test
        @DisplayName("12바이트 필드에 남궁가나다라마바를 쓰면 남궁가나다라까지 쓴다")
        void 길이를_넘으면_자른다() {
            // given
            byte[] bytes = blank();

            // when
            FieldEncoder.writeAN(bytes, FIELD_12, "남궁가나다라마바");

            // then
            assertThat(read(bytes, FIELD_12)).isEqualTo("남궁가나다라");
        }

        @Test
        @DisplayName("12바이트 필드에 13바이트 A남궁가나다라를 쓰면 한글을 반만 쓰지 않고 A남궁가나다 뒤에 SPACE 1개를 붙인다")
        void 글자_경계에서_자른다() {
            // given
            byte[] bytes = blank();

            // when
            FieldEncoder.writeAN(bytes, FIELD_12, "A남궁가나다라");

            // then
            assertThat(read(bytes, FIELD_12)).isEqualTo("A남궁가나다 ");
        }

        @Test
        @DisplayName("EUC-KR로 쓸 수 없는 이모지를 쓰면 IllegalArgumentException을 던진다")
        void EUC_KR로_쓸_수_없으면_예외를_던진다() {
            // given
            byte[] bytes = blank();

            // when, then
            assertThatThrownBy(() -> FieldEncoder.writeAN(bytes, FIELD_12, "김😀"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("필드 하나를 쓰면 그 필드 밖의 바이트는 바뀌지 않는다")
        void 필드_밖은_바뀌지_않는다() {
            // given
            byte[] original = new byte[TelegramFixture.LENGTH];
            Arrays.fill(original, (byte) 'X');
            byte[] bytes = original.clone();

            // when
            FieldEncoder.writeAN(bytes, FIELD_6, "AB");

            // then
            assertThat(Arrays.copyOfRange(bytes, 0, 100)).isEqualTo(Arrays.copyOfRange(original, 0, 100));
            assertThat(Arrays.copyOfRange(bytes, 106, 300)).isEqualTo(Arrays.copyOfRange(original, 106, 300));
        }
    }
}
