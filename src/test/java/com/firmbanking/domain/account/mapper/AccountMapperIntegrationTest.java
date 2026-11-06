package com.firmbanking.domain.account.mapper;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
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
    class CustomerAccount {

        private Long accountId;

        @BeforeEach
        void setUp() {
            jdbcTemplate.update("""
                    INSERT INTO account (bank_code, account_no, holder_name, holder_birth_date, balance)
                    VALUES ('999', '200-000-000001', '김철수', '900101', 10000000)
                    """);
            accountId = accountMapper.findByBankCodeAndAccountNo("999", "200-000-000001").getAccountId();
        }

        @Test
        @DisplayName("1,000,000을 updateWithdrawal하면 바뀐 행이 1이고 잔액이 9,000,000이다")
        void 잔액이_충분하면_출금한다() {
            // when
            int updatedRows = accountMapper.updateWithdrawal(accountId, new BigDecimal("1000000"));

            // then
            assertThat(updatedRows).isEqualTo(1);
            assertThat(findBalance()).isEqualByComparingTo(new BigDecimal("9000000"));
        }

        @Test
        @DisplayName("잔액과 같은 10,000,000을 updateWithdrawal하면 바뀐 행이 1이고 잔액이 0이다")
        void 잔액과_같으면_출금한다() {
            // when
            int updatedRows = accountMapper.updateWithdrawal(accountId, new BigDecimal("10000000"));

            // then
            assertThat(updatedRows).isEqualTo(1);
            assertThat(findBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("잔액보다 많은 11,000,000을 updateWithdrawal하면 바뀐 행이 0이고 잔액이 10,000,000 그대로다")
        void 잔액이_모자라면_출금하지_않는다() {
            // when
            int updatedRows = accountMapper.updateWithdrawal(accountId, new BigDecimal("11000000"));

            // then
            assertThat(updatedRows).isZero();
            assertThat(findBalance()).isEqualByComparingTo(new BigDecimal("10000000"));
        }

        @Test
        @DisplayName("1,000,000을 updateDeposit하면 잔액이 11,000,000이다")
        void 입금한다() {
            // when
            accountMapper.updateDeposit(accountId, new BigDecimal("1000000"));

            // then
            assertThat(findBalance()).isEqualByComparingTo(new BigDecimal("11000000"));
        }

        private BigDecimal findBalance() {
            return accountMapper.findByBankCodeAndAccountNo("999", "200-000-000001").getBalance();
        }
    }
}
