package com.firmbanking.domain.transfer.mapper;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.domain.transfer.dto.request.CreateAccountTransactionRequest;
import com.firmbanking.global.code.AccountTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("AccountTransactionMapper")
class AccountTransactionMapperIntegrationTest {

    @Autowired
    private AccountTransactionMapper accountTransactionMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long accountId;

    @BeforeEach
    void setUp() {
        accountId = accountMapper.findByBankCodeAndAccountNo("999", "100-000-000002").getAccountId();
    }

    @Test
    @DisplayName("지급 계좌의 출금 1,000,000 / 잔액 9,999,000,000을 INSERT하면 요청의 accountTransactionId가 채워진다")
    void 생성된_id가_요청에_채워진다() {
        // given
        CreateAccountTransactionRequest request = withdrawalRequest();

        // when
        accountTransactionMapper.insert(request);

        // then
        assertThat(request.getAccountTransactionId()).isNotNull();
    }

    @Nested
    @DisplayName("지급 계좌의 출금 1,000,000 / 잔액 9,999,000,000을 INSERT했을 때")
    class Inserted {

        private CreateAccountTransactionRequest request;

        @BeforeEach
        void setUp() {
            request = withdrawalRequest();
            accountTransactionMapper.insert(request);
        }

        @Test
        @DisplayName("채워진 id로 행을 조회하면 지급 계좌 / 출금(1) / 1,000,000 / 9,999,000,000이다")
        void 넣은_값으로_저장된다() {
            // when
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT account_id, account_transaction_type_id, amount, balance_after
                    FROM account_transaction
                    WHERE account_transaction_id = ?
                    """, request.getAccountTransactionId());

            // then
            assertThat(((Number) row.get("account_id")).longValue()).isEqualTo(accountId);
            assertThat(((Number) row.get("account_transaction_type_id")).intValue())
                    .isEqualTo(AccountTransactionType.WITHDRAWAL.getId());
            assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo(new BigDecimal("1000000"));
            assertThat((BigDecimal) row.get("balance_after")).isEqualByComparingTo(new BigDecimal("9999000000"));
        }
    }

    private CreateAccountTransactionRequest withdrawalRequest() {
        return CreateAccountTransactionRequest.builder()
                .accountId(accountId)
                .accountTransactionType(AccountTransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1000000"))
                .balanceAfter(new BigDecimal("9999000000"))
                .build();
    }
}
