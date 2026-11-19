package com.firmbanking.domain.transfer.service;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("AccountLock")
class AccountLockIntegrationTest {

    private static final String BANK_CODE = "999";
    private static final String PAYMENT_ACCOUNT_NO = "100-000-000002";
    private static final String CUSTOMER_ACCOUNT_NO = "200-000-000001";
    private static final String PAYMENT_KEY = AccountLock.keyOf(BANK_CODE, PAYMENT_ACCOUNT_NO);
    private static final String CUSTOMER_KEY = AccountLock.keyOf(BANK_CODE, CUSTOMER_ACCOUNT_NO);

    @Autowired
    private AccountLock accountLock;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("아무도 잠그지 않은 지급 계좌와 고객 계좌로 실행하면 작업 결과를 돌려주고 끝난 뒤 두 잠금이 해제되어 있다")
    void 작업_결과를_돌려주고_잠금을_해제한다() {
        // when
        String result = accountLock.execute(request(PAYMENT_ACCOUNT_NO, CUSTOMER_ACCOUNT_NO), () -> "결과");

        // then
        assertThat(result).isEqualTo("결과");
        assertThat(isFreeLock(PAYMENT_KEY)).isTrue();
        assertThat(isFreeLock(CUSTOMER_KEY)).isTrue();
    }

    @Test
    @DisplayName("작업이 IllegalStateException을 던지면 그 예외가 그대로 나가고 두 잠금이 해제되어 있다")
    void 작업이_실패해도_잠금을_해제한다() {
        // when, then
        assertThatThrownBy(() -> accountLock.execute(request(PAYMENT_ACCOUNT_NO, CUSTOMER_ACCOUNT_NO), () -> {
            throw new IllegalStateException("작업 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(isFreeLock(PAYMENT_KEY)).isTrue();
        assertThat(isFreeLock(CUSTOMER_KEY)).isTrue();
    }

    // 같은 키를 두 번 획득하고 한 번만 해제하면 잠금이 남는다
    @Test
    @DisplayName("출금계좌와 입금계좌가 같으면 작업을 실행하고 끝난 뒤 잠금이 해제되어 있다")
    void 같은_계좌면_한_번만_획득한다() {
        // given
        AtomicBoolean executed = new AtomicBoolean();

        // when
        accountLock.execute(request(PAYMENT_ACCOUNT_NO, PAYMENT_ACCOUNT_NO), () -> executed.getAndSet(true));

        // then
        assertThat(executed).isTrue();
        assertThat(isFreeLock(PAYMENT_KEY)).isTrue();
    }

    @Test
    @DisplayName("다른 커넥션이 지급 계좌 잠금을 보유하고 있으면 10초 뒤 CannotAcquireLockException을 던지고 작업을 실행하지 않는다")
    void 잠금을_얻지_못하면_실행하지_않는다() throws Exception {
        // given
        AtomicBoolean executed = new AtomicBoolean();
        try (Connection other = dataSource.getConnection()) {
            execute(other, "SELECT GET_LOCK(?, 0)", PAYMENT_KEY);

            // when, then
            assertThatThrownBy(() -> accountLock.execute(request(PAYMENT_ACCOUNT_NO, CUSTOMER_ACCOUNT_NO),
                    () -> executed.getAndSet(true)))
                    .isInstanceOf(CannotAcquireLockException.class);
            assertThat(executed).isFalse();

            execute(other, "SELECT RELEASE_LOCK(?)", PAYMENT_KEY);
        }
    }

    private boolean isFreeLock(String key) {
        return jdbcTemplate.queryForObject("SELECT IS_FREE_LOCK(?)", Integer.class, key) == 1;
    }

    private static void execute(Connection connection, String sql, String key) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.executeQuery().close();
        }
    }

    private static ExecuteTransferRequest request(String withdrawalAccountNo, String depositAccountNo) {
        return ExecuteTransferRequest.builder()
                .withdrawalBankCode(BANK_CODE)
                .withdrawalAccountNo(withdrawalAccountNo)
                .depositBankCode(BANK_CODE)
                .depositAccountNo(depositAccountNo)
                .build();
    }
}
