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
 * 실제 소켓으로 6000/100을 보낸다. test 프로필 DB에는 V3 기준 데이터(업체 CAPITAL00001, 업체 계좌 2개)만 있다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("계좌성명 조회 6000/100 소켓 송수신")
class NameInquiryIntegrationTest {

    @Autowired
    private TelegramServer server;

    // 전문 명세 5절 A003: 공통부 업체번호가 등록되지 않음
    @Test
    @DisplayName("업체번호가 CAPITAL00002면 전문구분 6100, 응답코드 A003을 받는다")
    void 업체가_없으면_A003을_받는다() throws IOException {
        // given
        byte[] request = NameInquiryFixture.request("999", "200-000-000001", "900101");
        put(request, CommonField.COMPANY_NO, "CAPITAL00002");

        // when
        byte[] response = TelegramClient.exchange(server.getPort(), request);

        // then
        assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("6100");
        assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A003");
    }

    // 전문 명세 5절 B001: 공통부 은행에 대상 계좌가 없음
    @Test
    @DisplayName("계좌 999 / 200-999-999999가 없으면 전문구분 6100, 응답코드 B001을 받는다")
    void 계좌가_없으면_B001을_받는다() throws IOException {
        // given
        byte[] request = NameInquiryFixture.request("999", "200-999-999999", "900101");

        // when
        byte[] response = TelegramClient.exchange(server.getPort(), request);

        // then
        assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("6100");
        assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("B001");
    }
}
