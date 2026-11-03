package com.firmbanking.telegram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 테스트용 전문 클라이언트. 길이 헤더 0300 + 본문을 보내고, 서버가 닫을 때까지 받은 응답에서 헤더를 뺀 본문을 돌려준다.
 */
public final class TelegramClient {

    private static final byte[] HEADER = "0300".getBytes(StandardCharsets.US_ASCII);
    private static final int TIMEOUT_MILLIS = 5_000;

    private TelegramClient() {
    }

    public static byte[] exchange(int port, byte[] body) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();
            out.write(HEADER);
            out.write(body);
            out.flush();

            ByteArrayOutputStream received = new ByteArrayOutputStream();
            InputStream in = socket.getInputStream();
            in.transferTo(received);
            byte[] bytes = received.toByteArray();
            return Arrays.copyOfRange(bytes, HEADER.length, bytes.length);
        }
    }
}
