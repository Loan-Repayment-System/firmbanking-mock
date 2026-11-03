package com.firmbanking.telegram;

import com.firmbanking.telegram.codec.TelegramField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 연결 하나: 길이 헤더 4 + 본문 300 읽기 → 디스패치 → 길이 헤더를 붙여 응답 쓰기 → 닫기.
 * 길이 헤더가 0300이 아니거나, 본문을 다 받기 전에 끊기거나 읽기 시간이 지나면 응답 없이 닫는다 (전문 명세 2절).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramHandler {

    private static final Charset CHARSET = Charset.forName("EUC-KR");
    private static final byte[] LENGTH_HEADER =
            String.format("%04d", TelegramField.TELEGRAM_LENGTH).getBytes(StandardCharsets.US_ASCII);

    private final TelegramDispatcher dispatcher;
    private final TelegramProperties properties;

    public void handle(Socket socket) {
        SocketAddress remote = socket.getRemoteSocketAddress();
        try (socket) {
            byte[] request;
            try {
                request = readRequest(socket);
            } catch (IOException e) {
                log.warn("요청을 다 받지 못해 응답 없이 닫는다 {}: {}", remote, e.toString());
                return;
            }
            log.info("수신 {} [{}]", remote, new String(request, CHARSET));
            long startedAt = System.nanoTime();

            byte[] response = dispatcher.dispatch(request);
            try {
                OutputStream out = socket.getOutputStream();
                out.write(LENGTH_HEADER);
                out.write(response);
                out.flush();
            } catch (IOException e) {
                log.warn("응답 쓰기 실패 {}", remote, e);
                return;
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.info("송신 {} {}ms [{}]", remote, elapsedMillis, new String(response, CHARSET));
        } catch (IOException e) {
            log.warn("연결 닫기 실패 {}", remote, e);
        }
    }

    private byte[] readRequest(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        long deadline = System.nanoTime() + properties.readTimeout().toNanos();

        byte[] header = new byte[LENGTH_HEADER.length];
        readFully(socket, in, header, deadline);
        if (!Arrays.equals(header, LENGTH_HEADER)) {
            throw new ProtocolException("길이 헤더가 0300이 아니다: " + new String(header, StandardCharsets.US_ASCII));
        }
        byte[] body = new byte[TelegramField.TELEGRAM_LENGTH];
        readFully(socket, in, body, deadline);
        return body;
    }

    /**
     * read 한 번마다가 아니라 요청 전체에 시간 제한을 둔다. 조금씩 보내는 연결이 스레드를 붙잡지 못하게 한다.
     */
    private static void readFully(Socket socket, InputStream in, byte[] buffer, long deadline) throws IOException {
        int read = 0;
        while (read < buffer.length) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new SocketTimeoutException("읽기 시간 초과");
            }
            // soTimeout 0은 무제한이라 최소 1ms로 둔다.
            socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            int count = in.read(buffer, read, buffer.length - read);
            if (count < 0) {
                throw new EOFException("다 받기 전에 연결이 끊겼다: " + read + "/" + buffer.length + "바이트");
            }
            read += count;
        }
    }
}
