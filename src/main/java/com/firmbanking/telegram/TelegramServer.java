package com.firmbanking.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 앱이 뜰 때 전문 포트를 열고, 연결마다 TelegramHandler를 고정 스레드 풀에 넘긴다.
 * 풀이 다 차면 새 연결은 큐에서 기다린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramServer implements SmartLifecycle {

    private final TelegramProperties properties;
    private final TelegramHandler handler;

    private ServerSocket serverSocket;
    private ExecutorService workers;
    private volatile boolean running;

    @Override
    public void start() {
        try {
            serverSocket = new ServerSocket(properties.port());
        } catch (IOException e) {
            throw new UncheckedIOException("전문 포트를 열지 못했다: " + properties.port(), e);
        }
        AtomicInteger workerNo = new AtomicInteger();
        workers = Executors.newFixedThreadPool(properties.threadPoolSize(),
                task -> new Thread(task, "telegram-worker-" + workerNo.incrementAndGet()));
        running = true;
        new Thread(this::acceptLoop, "telegram-acceptor").start();
        log.info("전문 서버 시작: 포트 {}", getPort());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                workers.execute(() -> handler.handle(socket));
            } catch (IOException e) {
                // stop()이 서버 소켓을 닫으면 accept가 예외로 끝난다.
                if (running) {
                    log.warn("연결 받기 실패", e);
                }
            }
        }
    }

    /**
     * 새 연결을 그만 받고, 처리 중인 연결은 읽기 타임아웃만큼 기다린다.
     */
    @Override
    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.warn("전문 포트 닫기 실패", e);
        }
        workers.shutdown();
        try {
            if (!workers.awaitTermination(properties.readTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("전문 서버 종료");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 실제로 열린 포트. 설정이 0(빈 포트)이어도 열린 번호를 돌려준다.
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }
}
