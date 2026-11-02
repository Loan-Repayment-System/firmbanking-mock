package com.firmbanking.telegram.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;

import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CommonPart")
class CommonPartTest {

    @Test
    @DisplayName("정상 공통부면 2000/100, CAPITAL00001, 000123, 2026-10-29 14:30:05로 읽는다")
    void 정상_공통부를_읽는다() {
        // given
        Telegram telegram = new Telegram(request());

        // when
        CommonPart result = CommonPart.from(telegram);

        // then
        assertThat(result.getTelegramTypeCode()).isEqualTo("2000");
        assertThat(result.getBusinessTypeCode()).isEqualTo("100");
        assertThat(result.getCompanyNo()).isEqualTo("CAPITAL00001");
        assertThat(result.getBankCode()).isEqualTo("999");
        assertThat(result.getTelegramNo()).isEqualTo("000123");
        assertThat(result.getSendDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(result.getSendTime()).isEqualTo(LocalTime.of(14, 30, 5));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("전문구분코드", CommonField.TELEGRAM_TYPE_CODE, "20A0"),
                Arguments.of("업무구분코드", CommonField.BUSINESS_TYPE_CODE, "1 0"),
                Arguments.of("은행코드", CommonField.BANK_CODE, "ABC"),
                Arguments.of("송신회수", CommonField.SEND_COUNT, " "),
                Arguments.of("전문번호", CommonField.TELEGRAM_NO, "00012X"),
                Arguments.of("전송일자", CommonField.SEND_DATE, "20261332"),
                Arguments.of("전송시간", CommonField.SEND_TIME, "250000"));
    }

    // 전문 명세 5절 A001: 필드 형식이 틀리거나 전송일자·전송시간이 없는 날짜·시각
    @ParameterizedTest(name = "{0} = [{2}]")
    @MethodSource("invalidFields")
    @DisplayName("N 필드가 숫자가 아니거나 전송일자·전송시간이 없는 값이면 InvalidTelegramException을 던진다")
    void 형식이_틀리면_예외를_던진다(String name, TelegramField field, String value) {
        // given
        byte[] bytes = request();
        put(bytes, field, value);
        Telegram telegram = new Telegram(bytes);

        // when, then
        assertThatThrownBy(() -> CommonPart.from(telegram))
                .isInstanceOf(InvalidTelegramException.class);
    }

    // 전문 명세 5절 A001: AN 필드가 EUC-KR이 아님
    @Test
    @DisplayName("업체번호에 EUC-KR이 아닌 바이트가 있으면 InvalidTelegramException을 던진다")
    void 업체번호가_EUC_KR이_아니면_예외를_던진다() {
        // given
        byte[] bytes = request();
        put(bytes, CommonField.COMPANY_NO, new byte[]{(byte) 0xFF, (byte) 0xFF});
        Telegram telegram = new Telegram(bytes);

        // when, then
        assertThatThrownBy(() -> CommonPart.from(telegram))
                .isInstanceOf(InvalidTelegramException.class);
    }

    @Test
    @DisplayName("식별코드 SETTLEBNK, 은행코드 004, 송신회수 2, 요청 응답코드 0000처럼 고정값이 달라도 읽는다")
    void 고정값은_검사하지_않는다() {
        // given
        byte[] bytes = request();
        put(bytes, CommonField.IDENTIFICATION_CODE, "SETTLEBNK");
        put(bytes, CommonField.BANK_CODE, "004");
        put(bytes, CommonField.SEND_COUNT, "2");
        put(bytes, CommonField.RESPONSE_CODE, "0000");
        Telegram telegram = new Telegram(bytes);

        // when
        CommonPart result = CommonPart.from(telegram);

        // then
        assertThat(result.getBankCode()).isEqualTo("004");
    }
}
