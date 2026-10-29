package com.firmbanking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("local")
@DisplayName("로컬 시드")
class LocalSeedIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("local 프로필로 띄우면 고객 계좌 3개가 예금주·생년월일·잔액과 함께 들어간다")
    void local_프로필은_고객_계좌_3개를_넣는다() {
        // when
        List<CustomerAccount> accounts = jdbcTemplate.query("""
                SELECT bank_code, account_no, holder_name, holder_birth_date, balance
                FROM account
                WHERE account_no LIKE '200-%'
                ORDER BY account_no
                """, new DataClassRowMapper<>(CustomerAccount.class));

        // then
        assertThat(accounts).hasSize(3);
        assertCustomer(accounts.get(0), "200-000-000001", "김철수", "900101", "10000000");
        assertCustomer(accounts.get(1), "200-000-000002", "이영희", "851231", "10000");
        assertCustomer(accounts.get(2), "200-000-000003", "남궁가나다라마바", "000229", "1000000");
    }

    private void assertCustomer(CustomerAccount account, String accountNo, String holderName,
                                String holderBirthDate, String balance) {
        assertThat(account.bankCode()).isEqualTo("999");
        assertThat(account.accountNo()).isEqualTo(accountNo);
        assertThat(account.holderName()).isEqualTo(holderName);
        assertThat(account.holderBirthDate()).isEqualTo(holderBirthDate);
        assertThat(account.balance()).isEqualByComparingTo(balance);
    }

    record CustomerAccount(String bankCode, String accountNo, String holderName,
                           String holderBirthDate, BigDecimal balance) {
    }
}
