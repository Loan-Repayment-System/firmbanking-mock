package com.firmbanking.telegram.codec;

import com.firmbanking.global.code.ResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

import static com.firmbanking.telegram.codec.TelegramFixture.blank;
import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static com.firmbanking.telegram.codec.TelegramFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Telegram")
class TelegramTest {

    private static final TelegramField FIELD_6 = new TelegramField(100, 6);
    private static final TelegramField FIELD_8 = new TelegramField(100, 8);
    private static final TelegramField FIELD_12 = new TelegramField(100, 12);
    private static final TelegramField FIELD_13 = new TelegramField(100, 13);

    private static Telegram telegramWith(TelegramField field, String value) {
        byte[] bytes = blank();
        put(bytes, field, value);
        return new Telegram(bytes);
    }

    @Test
    @DisplayName("300바이트가 아닌 바이트 배열로 만들면 IllegalArgumentException을 던진다")
    void 본문이_300바이트가_아니면_예외를_던진다() {
        // when, then
        assertThatThrownBy(() -> new Telegram(new byte[299]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("N 필드를 읽을 때")
    class ReadN {

        @Test
        @DisplayName("000123이면 문자열 000123으로 읽는다")
        void 숫자를_그대로_읽는다() {
            // given
            Telegram telegram = telegramWith(FIELD_6, "000123");

            // when
            String result = telegram.readN(FIELD_6);

            // then
            assertThat(result).isEqualTo("000123");
        }

        @Test
        @DisplayName("금액 0000000010000이면 10000으로 읽는다")
        void 금액을_BigDecimal로_읽는다() {
            // given
            Telegram telegram = telegramWith(FIELD_13, "0000000010000");

            // when
            BigDecimal result = telegram.readAmount(FIELD_13);

            // then
            assertThat(result).isEqualByComparingTo("10000");
        }

        // 전문 명세 2절: N은 오른쪽 정렬, 남는 자리는 앞에 0
        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"00 123", "00A123", "-00123"})
        @DisplayName("공백·문자·부호가 섞이면 InvalidTelegramException을 던진다")
        void 숫자가_아니면_예외를_던진다(String value) {
            // given
            Telegram telegram = telegramWith(FIELD_6, value);

            // when, then
            assertThatThrownBy(() -> telegram.readN(FIELD_6))
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
            Telegram telegram = telegramWith(FIELD_12, "김철수");

            // when
            String result = telegram.readAN(FIELD_12);

            // then
            assertThat(result).isEqualTo("김철수");
        }

        @Test
        @DisplayName("모두 SPACE면 빈 문자열로 읽는다")
        void 모두_SPACE면_빈_문자열이다() {
            // given
            Telegram telegram = new Telegram(blank());

            // when
            String result = telegram.readAN(FIELD_12);

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
            Telegram telegram = new Telegram(bytes);

            // when, then
            assertThatThrownBy(() -> telegram.readAN(FIELD_12))
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
            Telegram telegram = telegramWith(FIELD_8, value);

            // when
            LocalDate result = telegram.readDate(FIELD_8);

            // then
            assertThat(result).isEqualTo(expected);
        }

        // 전문 명세 5절 A001: 없는 날짜·시각
        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"20260230", "20250229", "20261301"})
        @DisplayName("없는 날짜면 InvalidTelegramException을 던진다")
        void 없는_날짜면_예외를_던진다(String value) {
            // given
            Telegram telegram = telegramWith(FIELD_8, value);

            // when, then
            assertThatThrownBy(() -> telegram.readDate(FIELD_8))
                    .isInstanceOf(InvalidTelegramException.class);
        }

        @ParameterizedTest(name = "[{0}]")
        @CsvSource({"143005, 14:30:05", "000000, 00:00:00", "235959, 23:59:59"})
        @DisplayName("있는 시각이면 LocalTime으로 읽는다")
        void 있는_시각을_읽는다(String value, LocalTime expected) {
            // given
            Telegram telegram = telegramWith(FIELD_6, value);

            // when
            LocalTime result = telegram.readTime(FIELD_6);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"240000", "126000", "120060"})
        @DisplayName("없는 시각이면 InvalidTelegramException을 던진다")
        void 없는_시각이면_예외를_던진다(String value) {
            // given
            Telegram telegram = telegramWith(FIELD_6, value);

            // when, then
            assertThatThrownBy(() -> telegram.readTime(FIELD_6))
                    .isInstanceOf(InvalidTelegramException.class);
        }
    }

    @Nested
    @DisplayName("N 필드를 쓸 때")
    class WriteN {

        @Test
        @DisplayName("6자리 필드에 123을 쓰면 000123이 된다")
        void 앞을_0으로_채운다() {
            // given
            Telegram telegram = new Telegram(blank());

            // when
            telegram.writeN(FIELD_6, 123);

            // then
            assertThat(read(telegram, FIELD_6)).isEqualTo("000123");
        }

        @Test
        @DisplayName("13자리 필드에 금액 10000을 쓰면 0000000010000이 된다")
        void 금액을_쓴다() {
            // given
            Telegram telegram = new Telegram(blank());

            // when
            telegram.writeAmount(FIELD_13, new BigDecimal("10000"));

            // then
            assertThat(read(telegram, FIELD_13)).isEqualTo("0000000010000");
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(longs = {-1, 1234567})
        @DisplayName("6자리 필드에 음수나 7자리 수를 쓰면 IllegalArgumentException을 던진다")
        void 음수나_자릿수_초과면_예외를_던진다(long value) {
            // given
            Telegram telegram = new Telegram(blank());

            // when, then
            assertThatThrownBy(() -> telegram.writeN(FIELD_6, value))
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
            Telegram telegram = new Telegram(request());

            // when
            telegram.writeAN(FIELD_12, "김철수");

            // then
            assertThat(read(telegram, FIELD_12)).isEqualTo("김철수      ");
        }

        // 전문 명세 4.5: 예금주명1은 한글 6자까지, 넘치면 자름
        @Test
        @DisplayName("12바이트 필드에 남궁가나다라마바를 쓰면 남궁가나다라까지 쓴다")
        void 길이를_넘으면_자른다() {
            // given
            Telegram telegram = new Telegram(blank());

            // when
            telegram.writeAN(FIELD_12, "남궁가나다라마바");

            // then
            assertThat(read(telegram, FIELD_12)).isEqualTo("남궁가나다라");
        }

        @Test
        @DisplayName("12바이트 필드에 13바이트 A남궁가나다라를 쓰면 한글을 반만 쓰지 않고 A남궁가나다 뒤에 SPACE 1개를 붙인다")
        void 글자_경계에서_자른다() {
            // given
            Telegram telegram = new Telegram(blank());

            // when
            telegram.writeAN(FIELD_12, "A남궁가나다라");

            // then
            assertThat(read(telegram, FIELD_12)).isEqualTo("A남궁가나다 ");
        }

        @Test
        @DisplayName("EUC-KR로 쓸 수 없는 이모지를 쓰면 IllegalArgumentException을 던진다")
        void EUC_KR로_쓸_수_없으면_예외를_던진다() {
            // given
            Telegram telegram = new Telegram(blank());

            // when, then
            assertThatThrownBy(() -> telegram.writeAN(FIELD_12, "김😀"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("필드 하나를 쓰면 그 필드 밖의 바이트는 바뀌지 않는다")
        void 필드_밖은_바뀌지_않는다() {
            // given
            byte[] original = new byte[Telegram.LENGTH];
            Arrays.fill(original, (byte) 'X');
            Telegram telegram = new Telegram(original);

            // when
            telegram.writeAN(FIELD_6, "AB");

            // then
            byte[] result = telegram.toBytes();
            assertThat(Arrays.copyOfRange(result, 0, 100)).isEqualTo(Arrays.copyOfRange(original, 0, 100));
            assertThat(Arrays.copyOfRange(result, 106, 300)).isEqualTo(Arrays.copyOfRange(original, 106, 300));
        }
    }

    @Nested
    @DisplayName("응답을 만들 때")
    class ToResponse {

        // 전문 명세 2절: 요청을 복사하고 전문구분코드와 응답코드를 바꾼다
        @ParameterizedTest(name = "[{0} → {1}]")
        @CsvSource({"2000, 2100", "6000, 6100", "7000, 7100"})
        @DisplayName("요청 전문구분이 2000·6000·7000이면 2100·6100·7100으로 바꾸고 응답코드 0000을 채운다")
        void 전문구분을_응답으로_바꾼다(String requestType, String responseType) {
            // given
            byte[] bytes = request();
            put(bytes, CommonPart.TELEGRAM_TYPE_CODE, requestType);
            Telegram telegram = new Telegram(bytes);

            // when
            Telegram response = telegram.toResponse(ResponseCode.SUCCESS);

            // then
            assertThat(read(response, CommonPart.TELEGRAM_TYPE_CODE)).isEqualTo(responseType);
            assertThat(read(response, CommonPart.RESPONSE_CODE)).isEqualTo("0000");
        }

        @Test
        @DisplayName("A001 응답은 전문구분코드와 응답코드를 뺀 292바이트가 요청과 같다")
        void 나머지는_요청과_같다() {
            // given
            byte[] bytes = request();
            put(bytes, new TelegramField(100, 15), "100-000-000002");
            Telegram telegram = new Telegram(bytes);

            // when
            Telegram response = telegram.toResponse(ResponseCode.INVALID_FORMAT);

            // then
            byte[] result = response.toBytes();
            assertThat(read(response, CommonPart.RESPONSE_CODE)).isEqualTo("A001");
            assertThat(Arrays.copyOfRange(result, 0, 24)).isEqualTo(Arrays.copyOfRange(bytes, 0, 24));
            assertThat(Arrays.copyOfRange(result, 28, 52)).isEqualTo(Arrays.copyOfRange(bytes, 28, 52));
            assertThat(Arrays.copyOfRange(result, 56, 300)).isEqualTo(Arrays.copyOfRange(bytes, 56, 300));
        }

        // 전문 명세 2절: 2000·6000·7000이 아니면 그대로 둔다
        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"3000", "ABCD", "2100"})
        @DisplayName("요청 전문구분이 3000·ABCD·2100이면 그대로 두고 응답코드 A002만 채운다")
        void 모르는_전문구분은_그대로_둔다(String requestType) {
            // given
            byte[] bytes = request();
            put(bytes, CommonPart.TELEGRAM_TYPE_CODE, requestType);
            Telegram telegram = new Telegram(bytes);

            // when
            Telegram response = telegram.toResponse(ResponseCode.UNSUPPORTED_TELEGRAM);

            // then
            assertThat(read(response, CommonPart.TELEGRAM_TYPE_CODE)).isEqualTo(requestType);
            assertThat(read(response, CommonPart.RESPONSE_CODE)).isEqualTo("A002");
        }

        @Test
        @DisplayName("응답에 값을 써도 요청 원문은 바뀌지 않는다")
        void 요청은_바뀌지_않는다() {
            // given
            byte[] bytes = request();
            Telegram telegram = new Telegram(bytes);
            Telegram response = telegram.toResponse(ResponseCode.SUCCESS);

            // when
            response.writeAN(FIELD_12, "김철수");

            // then
            assertThat(telegram.toBytes()).isEqualTo(bytes);
        }
    }
}
