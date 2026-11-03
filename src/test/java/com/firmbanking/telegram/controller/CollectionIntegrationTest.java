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
 * 실제 소켓으로 2000/200을 보낸다. 소켓 처리는 다른 스레드에서 커밋되고 거절도 이체 결과를 저장하므로,
 * 이체 서비스까지 가지 않는 경우만 보낸다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("집금이체 2000/200 소켓 송수신")
class CollectionIntegrationTest {

    @Autowired
    private TelegramServer server;

    // 전문 명세 5절 A003: 공통부 업체번호가 등록되지 않음
    @Test
    @DisplayName("업체번호가 CAPITAL00002면 전문구분 2100, 응답코드 A003을 받는다")
    void 업체가_없으면_A003을_받는다() throws IOException {
        // given
        byte[] request = CollectionFixture.request("200-000-000001", 1_000_000, "999", "100-000-000001");
        put(request, CommonField.COMPANY_NO, "CAPITAL00002");

        // when
        byte[] response = TelegramClient.exchange(server.getPort(), request);

        // then
        assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("2100");
        assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A003");
    }

    // 전문 명세 5절 A001: 출금금액이 1 미만
    @Test
    @DisplayName("출금금액이 0이면 전문구분 2100, 응답코드 A001을 받는다")
    void 출금금액이_0이면_A001을_받는다() throws IOException {
        // given
        byte[] request = CollectionFixture.request("200-000-000001", 0, "999", "100-000-000001");

        // when
        byte[] response = TelegramClient.exchange(server.getPort(), request);

        // then
        assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("2100");
        assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A001");
    }
}
