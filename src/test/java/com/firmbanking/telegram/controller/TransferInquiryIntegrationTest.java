package com.firmbanking.telegram.controller;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.telegram.TelegramClient;
import com.firmbanking.telegram.TelegramServer;
import com.firmbanking.telegram.codec.CommonField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 소켓으로 7000/100을 보낸다. 결과조회는 데이터를 바꾸지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("이체결과조회 7000/100 소켓 송수신")
class TransferInquiryIntegrationTest {

    @Autowired
    private TelegramServer server;

    // 전문 명세 5절 A003: 공통부 업체번호가 등록되지 않음
    @Test
    @DisplayName("업체번호가 CAPITAL00002면 전문구분 7100, 응답코드 A003을 받는다")
    void 업체가_없으면_A003을_받는다() throws IOException {
        // given
        byte[] request = TransferInquiryFixture.request("20261029", "000100");
        put(request, CommonField.COMPANY_NO, "CAPITAL00002");

        // when
        byte[] response = TelegramClient.exchange(server.getPort(), request);

        // then
        assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("7100");
        assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A003");
    }

    // 전문 명세 5절 D002: 조회한 거래고유번호의 이체가 없음
    @Test
    @DisplayName("원거래 20261029 / 999999가 없으면 전문구분 7100, 응답코드 D002를 받는다")
    void 원거래가_없으면_D002를_받는다() throws IOException {
        // given
        byte[] request = TransferInquiryFixture.request("20261029", "999999");

        // when
        byte[] response = TelegramClient.exchange(server.getPort(), request);

        // then
        assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("7100");
        assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("D002");
    }

    // 전문 명세 5절 A001: 원거래전송일자가 없는 날짜
    @Test
    @DisplayName("원거래전송일자가 20261340이면 전문구분 7100, 응답코드 A001을 받는다")
    void 원거래전송일자가_없는_날짜면_A001을_받는다() throws IOException {
        // given
        byte[] request = TransferInquiryFixture.request("20261340", "000100");

        // when
        byte[] response = TelegramClient.exchange(server.getPort(), request);

        // then
        assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("7100");
        assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A001");
    }
}
