package com.firmbanking.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 전문 서버 설정. readTimeout은 길이 헤더부터 본문 끝까지 요청 하나를 읽는 데 주는 전체 시간이다.
 */
@ConfigurationProperties("telegram")
public record TelegramProperties(int port, Duration readTimeout, int threadPoolSize) {
}
