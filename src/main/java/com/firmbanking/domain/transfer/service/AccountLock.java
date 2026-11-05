package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * 출금계좌와 입금계좌에 MySQL Named Lock(GET_LOCK)을 획득하고 작업을 실행한 뒤 잠금을 해제한다.
 * GET_LOCK은 트랜잭션이 아니라 커넥션에 걸리므로, 같은 커넥션을 작업이 끝날 때까지 보유하고 그 커넥션에서 해제한다.
 * 잠금을 기다리는 요청이 이체 트랜잭션의 커넥션을 차지하지 않게 이체용 풀과 따로 둔 잠금 전용 풀을 쓴다.
 */
@Component
public class AccountLock {

    private static final int TIMEOUT_SECONDS = 10;

    private final HikariDataSource dataSource;

    /**
     * 전문 처리 스레드마다 잠금 커넥션을 바로 받도록 풀 크기를 스레드 수와 같게 둔다.
     */
    public AccountLock(JdbcConnectionDetails connectionDetails,
                       @Value("${telegram.thread-pool-size}") int poolSize) {
        dataSource = new HikariDataSource();
        dataSource.setPoolName("account-lock");
        dataSource.setJdbcUrl(connectionDetails.getJdbcUrl());
        dataSource.setUsername(connectionDetails.getUsername());
        dataSource.setPassword(connectionDetails.getPassword());
        dataSource.setMaximumPoolSize(poolSize);
    }

    /**
     * 두 계좌의 잠금을 키 순서로 획득해 데드락을 막는다. 같은 계좌면 하나만 획득한다.
     * 10초 안에 잠금을 얻지 못하면 작업을 실행하지 않고 CannotAcquireLockException을 던진다.
     */
    public <T> T execute(ExecuteTransferRequest request, Supplier<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            List<String> acquired = new ArrayList<>();
            try {
                for (String key : keysOf(request)) {
                    acquire(connection, key);
                    acquired.add(key);
                }
                return action.get();
            } finally {
                for (String key : acquired) {
                    release(connection, key);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("계좌 잠금 커넥션 오류", e);
        }
    }

    private static Set<String> keysOf(ExecuteTransferRequest request) {
        Set<String> keys = new TreeSet<>();
        keys.add(keyOf(request.getWithdrawalBankCode(), request.getWithdrawalAccountNo()));
        keys.add(keyOf(request.getDepositBankCode(), request.getDepositAccountNo()));
        return keys;
    }

    static String keyOf(String bankCode, String accountNo) {
        return "account:" + bankCode + ":" + accountNo;
    }

    /**
     * GET_LOCK은 잠금을 얻으면 1, 시간이 지나면 0, 오류면 NULL이다.
     */
    private static void acquire(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, key);
            statement.setInt(2, TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) != 1) {
                    throw new CannotAcquireLockException("계좌 잠금을 " + TIMEOUT_SECONDS + "초 안에 얻지 못했다: " + key);
                }
            }
        }
    }

    private static void release(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, key);
            statement.executeQuery().close();
        }
    }

    @PreDestroy
    void close() {
        dataSource.close();
    }
}
