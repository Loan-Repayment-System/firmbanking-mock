package com.firmbanking.telegram.controller;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.telegram.TelegramClient;
import com.firmbanking.telegram.TelegramServer;
import com.firmbanking.telegram.codec.CommonField;
import com.firmbanking.telegram.controller.field.WithdrawalConsentField;
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
 * 실제 소켓으로 2000/550을 보낸다. 소켓 처리는 다른 스레드에서 커밋되므로 데이터를 바꾸지 않는 경우만 보낸다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("수납출금계좌 등록 2000/550 소켓 송수신")
class WithdrawalConsentIntegrationTest {

    @Autowired
    private TelegramServer server;

    // 전문 명세 5절 A003: 공통부 업체번호가 등록되지 않음
    @Test
    @DisplayName("업체번호가 CAPITAL00002면 전문구분 2100, 응답코드 A003을 받는다")
    void 업체가_없으면_A003을_받는다() throws IOException {
        // given
        byte[] request = WithdrawalConsentFixture.request("999", "200-000-000001", "1");
        put(request, CommonField.COMPANY_NO, "CAPITAL00002");

        // when
        byte[] response = TelegramClient.exchange(server.getPort(), request);

        // then
        assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("2100");
        assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("A003");
    }

    // 전문 명세 5절 B001: 공통부 은행에 대상 계좌가 없음
    @Test
    @DisplayName("계좌 999 / 200-999-999999가 없으면 전문구분 2100, 응답코드 B001, 처리여부 N, 불능코드 B001을 받는다")
    void 계좌가_없으면_B001을_받는다() throws IOException {
        // given
        byte[] request = WithdrawalConsentFixture.request("999", "200-999-999999", "1");

        // when
        byte[] response = TelegramClient.exchange(server.getPort(), request);

        // then
        assertThat(read(response, CommonField.TELEGRAM_TYPE_CODE)).isEqualTo("2100");
        assertThat(read(response, CommonField.RESPONSE_CODE)).isEqualTo("B001");
        assertThat(read(response, WithdrawalConsentField.PROCESSED)).isEqualTo("N");
        assertThat(read(response, WithdrawalConsentField.FAILURE_CODE)).isEqualTo("B001");
    }
}
