package com.firmbanking.domain.account.mapper;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("AccountMapper")
class AccountMapperIntegrationTest {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("V3 기준 데이터로 업체 계좌만 있을 때")
    class BaseData {

        @Test
        @DisplayName("999 / 100-000-000002를 조회하면 예금주명 캐피탈, 생년월일 null, 잔액 10,000,000,000이다")
        void 계좌가_있으면_계좌를_돌려준다() {
            // when
            AccountDetailResponse account = accountMapper.findByBankCodeAndAccountNo("999", "100-000-000002");

            // then
            assertThat(account.getAccountId()).isNotNull();
            assertThat(account.getBankCode()).isEqualTo("999");
            assertThat(account.getAccountNo()).isEqualTo("100-000-000002");
            assertThat(account.getHolderName()).isEqualTo("캐피탈");
            assertThat(account.getHolderBirthDate()).isNull();
            assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("10000000000"));
        }

        @Test
        @DisplayName("999 / 200-999-999999를 조회하면 null이다")
        void 계좌가_없으면_null이다() {
            // when
            AccountDetailResponse account = accountMapper.findByBankCodeAndAccountNo("999", "200-999-999999");

            // then
            assertThat(account).isNull();
        }
    }

    @Nested
    @Transactional
    @DisplayName("잔액 10,000,000인 고객 계좌가 있을 때")
    class Customer {

        @Test
        @DisplayName("version 0인 계좌를 version 0으로 11,000,000으로 updateBalance하면 1행이 바뀌고 잔액 11,000,000, version 1이다")
        void version이_같으면_잔액을_바꾸고_version을_올린다() {
            // given
            Long accountId = insertCustomer(0L);

            // when
            int updated = accountMapper.updateBalance(accountId, new BigDecimal("11000000"), 0L);

            // then
            AccountDetailResponse account = findCustomer();
            assertThat(updated).isEqualTo(1);
            assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("11000000"));
            assertThat(account.getVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("version 1인 계좌를 version 0으로 11,000,000으로 updateBalance하면 0행이 바뀌고 잔액 10,000,000, version 1 그대로다")
        void version이_다르면_바꾸지_않는다() {
            // given
            Long accountId = insertCustomer(1L);

            // when
            int updated = accountMapper.updateBalance(accountId, new BigDecimal("11000000"), 0L);

            // then
            AccountDetailResponse account = findCustomer();
            assertThat(updated).isZero();
            assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("10000000"));
            assertThat(account.getVersion()).isEqualTo(1L);
        }

        private Long insertCustomer(Long version) {
            jdbcTemplate.update("""
                    INSERT INTO account (bank_code, account_no, holder_name, holder_birth_date, balance, version)
                    VALUES ('999', '200-000-000001', '김철수', '900101', 10000000, ?)
                    """, version);
            return findCustomer().getAccountId();
        }

        private AccountDetailResponse findCustomer() {
            return accountMapper.findByBankCodeAndAccountNo("999", "200-000-000001");
        }
    }
}
