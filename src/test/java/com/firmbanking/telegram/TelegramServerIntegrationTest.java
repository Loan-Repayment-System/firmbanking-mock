package com.firmbanking.telegram;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.telegram.codec.CommonField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * test 프로필: 빈 포트, 읽기 타임아웃 1초. 등록된 거래가 없어서 정상 공통부 요청은 A002로 응답한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("TelegramServer")
class TelegramServerIntegrationTest {

    private static final byte[] HEADER = "0300".getBytes(StandardCharsets.US_ASCII);
    private static final int CLIENT_TIMEOUT_MILLIS = 5_000;

    @Autowired
    private TelegramServer server;

    @Nested
    @DisplayName("길이 헤더와 본문 300바이트를 다 보냈을 때")
    class CompleteRequest {

        // 전문 명세 2절: 응답도 길이 헤더 + 본문 300바이트, 거래마다 연결 종료
        @Test
        @DisplayName("2000/100 요청이면 헤더 0300과 전문구분 2100, 응답코드 A002인 300바이트를 받고 서버가 연결을 닫는다")
        void 응답을_보내고_연결을_닫는다() throws IOException {
            try (Socket socket = connect()) {
                // given
                byte[] expected = request();
                put(expected, CommonField.TELEGRAM_TYPE_CODE, "2100");
                put(expected, CommonField.RESPONSE_CODE, "A002");

                // when
                send(socket, HEADER, request());

                // then
                byte[] received = readUntilClosed(socket);
                assertThat(Arrays.copyOfRange(received, 0, 4)).isEqualTo(HEADER);
                assertThat(Arrays.copyOfRange(received, 4, received.length)).isEqualTo(expected);
            }
        }

        // 전문 명세 5절 A001: 전송일자가 없는 날짜
        @Test
        @DisplayName("전송일자가 20261340이면 응답코드 A001인 300바이트를 받는다")
        void 공통부_형식_오류면_A001로_응답한다() throws IOException {
            try (Socket socket = connect()) {
                // given
                byte[] request = request();
                put(request, CommonField.SEND_DATE, "20261340");

                // when
                send(socket, HEADER, request);

                // then
                byte[] received = readUntilClosed(socket);
                assertThat(received).hasSize(304);
                assertThat(new String(received, 4 + CommonField.RESPONSE_CODE.offset(), 4, StandardCharsets.US_ASCII))
                        .isEqualTo("A001");
            }
        }
    }

    @Nested
    @DisplayName("요청을 다 받지 못했을 때")
    class IncompleteRequest {

        // 전문 명세 2절: 길이 헤더가 0300이 아니면 응답 없이 닫는다
        @ParameterizedTest(name = "길이 헤더 {0}")
        @ValueSource(strings = {"0299", "0301", "ABCD"})
        @DisplayName("길이 헤더가 0300이 아니면 응답 없이 연결을 닫는다")
        void 길이_헤더가_틀리면_응답_없이_닫는다(String header) throws IOException {
            try (Socket socket = connect()) {
                // when
                send(socket, header.getBytes(StandardCharsets.US_ASCII));

                // then
                assertThat(readUntilClosed(socket)).isEmpty();
            }
        }

        // 전문 명세 2절: 본문 300바이트를 다 받기 전에 연결이 끊기면 응답 없이 닫는다
        @Test
        @DisplayName("본문 299바이트를 보내고 출력을 닫으면 응답 없이 연결을 닫는다")
        void 본문을_다_보내기_전에_끊으면_응답_없이_닫는다() throws IOException {
            try (Socket socket = connect()) {
                // given
                send(socket, HEADER, Arrays.copyOf(request(), 299));

                // when
                socket.shutdownOutput();

                // then
                assertThat(readUntilClosed(socket)).isEmpty();
            }
        }

        // 전문 명세 2절: 본문 300바이트를 다 받기 전에 읽기 시간이 지나면 응답 없이 닫는다
        @Test
        @DisplayName("본문 100바이트만 보내고 기다리면 읽기 타임아웃 1초 뒤 응답 없이 연결을 닫는다")
        void 본문을_보내다_멈추면_타임아웃_뒤_응답_없이_닫는다() throws IOException {
            try (Socket socket = connect()) {
                // given
                send(socket, HEADER, Arrays.copyOf(request(), 100));

                // when
                byte[] received = readUntilClosed(socket);

                // then
                assertThat(received).isEmpty();
            }
        }

        // 읽기 타임아웃은 read 한 번이 아니라 요청 전체에 건다
        @Test
        @DisplayName("본문을 300ms마다 1바이트씩 보내도 읽기 타임아웃 1초가 지나면 보내는 도중에 연결을 닫는다")
        void 조금씩_보내도_전체_읽기_시간이_지나면_닫는다() throws Exception {
            try (Socket socket = connect()) {
                // given
                send(socket, HEADER);

                // when
                boolean closedWhileSending = sendOneByteEvery300ms(socket, 20);

                // then
                assertThat(closedWhileSending).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("다른 연결이 본문을 보내지 않고 붙잡고 있을 때")
    class AnotherConnectionWaiting {

        // 프로그램 설계 6.2: 연결마다 스레드 풀에서 처리한다
        @Test
        @DisplayName("새 연결의 요청은 기다리지 않고 응답을 받고, 붙잡고 있는 연결은 아직 열려 있다")
        void 다른_연결을_기다리지_않고_응답한다() throws IOException {
            try (Socket waiting = connect(); Socket socket = connect()) {
                // given
                send(waiting, HEADER);

                // when
                send(socket, HEADER, request());

                // then
                assertThat(readUntilClosed(socket)).hasSize(304);
                waiting.setSoTimeout(100);
                assertThatThrownBy(() -> waiting.getInputStream().read())
                        .isInstanceOf(SocketTimeoutException.class);
            }
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort());
        socket.setSoTimeout(CLIENT_TIMEOUT_MILLIS);
        return socket;
    }

    private static void send(Socket socket, byte[]... chunks) throws IOException {
        OutputStream out = socket.getOutputStream();
        for (byte[] chunk : chunks) {
            out.write(chunk);
        }
        out.flush();
    }

    /**
     * 서버가 닫을 때까지 받은 바이트. 서버가 닫은 뒤 보낸 바이트에는 RST가 와서 SocketException으로 끝날 수 있다.
     * 서버가 닫지 않으면 클라이언트 타임아웃(5초)으로 실패한다.
     */
    private static byte[] readUntilClosed(Socket socket) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        InputStream in = socket.getInputStream();
        try {
            int b;
            while ((b = in.read()) != -1) {
                received.write(b);
            }
        } catch (SocketException e) {
            // 연결 리셋도 서버가 닫은 것이다.
        }
        return received.toByteArray();
    }

    /**
     * 서버가 닫아 쓰기가 실패하면 true, count바이트를 다 보내면 false다.
     */
    private static boolean sendOneByteEvery300ms(Socket socket, int count) throws InterruptedException {
        try {
            OutputStream out = socket.getOutputStream();
            for (int i = 0; i < count; i++) {
                out.write(' ');
                out.flush();
                Thread.sleep(300);
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
