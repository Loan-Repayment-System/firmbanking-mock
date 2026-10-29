package com.firmbanking;

import com.firmbanking.global.code.CodeEnum;
import com.firmbanking.global.code.ConsentStatus;
import com.firmbanking.global.code.TransferType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Flyway 마이그레이션")
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("빈 DB에 마이그레이션을 적용하면")
    class AfterMigration {

        @Test
        @DisplayName("업체 CAPITAL00001의 수납 계좌는 잔액 0, 지급 계좌는 잔액 10,000,000,000으로 들어간다")
        void 업체와_수납_지급_계좌가_들어간다() {
            // given
            String sql = """
                    SELECT collection.bank_code   AS collection_bank_code,
                           collection.account_no  AS collection_account_no,
                           collection.balance     AS collection_balance,
                           payment.bank_code      AS payment_bank_code,
                           payment.account_no     AS payment_account_no,
                           payment.balance        AS payment_balance
                    FROM company c
                        JOIN account collection ON collection.account_id = c.collection_account_id
                        JOIN account payment ON payment.account_id = c.payment_account_id
                    WHERE c.company_no = 'CAPITAL00001'
                    """;

            // when
            Map<String, Object> company = jdbcTemplate.queryForMap(sql);

            // then
            assertThat(company.get("collection_bank_code")).isEqualTo("999");
            assertThat(company.get("collection_account_no")).isEqualTo("100-000-000001");
            assertThat((BigDecimal) company.get("collection_balance")).isEqualByComparingTo("0");
            assertThat(company.get("payment_bank_code")).isEqualTo("999");
            assertThat(company.get("payment_account_no")).isEqualTo("100-000-000002");
            assertThat((BigDecimal) company.get("payment_balance")).isEqualByComparingTo("10000000000");
        }

        // CLAUDE.md §8: 코드성 테이블의 id는 enum과 1:1이다
        @Test
        @DisplayName("transfer_type과 consent_status의 id가 enum의 id와 정확히 일치한다")
        void 코드_테이블_id가_enum과_일치한다() {
            // when
            List<Integer> transferTypeIds = jdbcTemplate.queryForList("SELECT transfer_type_id FROM transfer_type", Integer.class);
            List<Integer> consentStatusIds = jdbcTemplate.queryForList("SELECT consent_status_id FROM consent_status", Integer.class);

            // then
            assertThat(transferTypeIds).containsExactlyInAnyOrderElementsOf(idsOf(TransferType.values()));
            assertThat(consentStatusIds).containsExactlyInAnyOrderElementsOf(idsOf(ConsentStatus.values()));
        }

        @Test
        @DisplayName("test 프로필에는 고객 시드가 없어 계좌는 업체 계좌 2개뿐이다")
        void test_프로필에는_고객_시드가_없다() {
            // when
            List<String> accountNos = jdbcTemplate.queryForList("SELECT account_no FROM account", String.class);

            // then
            assertThat(accountNos).containsExactlyInAnyOrder("100-000-000001", "100-000-000002");
        }

        private List<Integer> idsOf(CodeEnum[] constants) {
            return Arrays.stream(constants).map(CodeEnum::getId).toList();
        }
    }

    @Nested
    @Transactional
    @DisplayName("같은 업체·전송일자·전문번호의 이체 결과가 있을 때")
    class WhenTransferExists {

        private static final String INSERT_TRANSFER = """
                INSERT INTO transfer (company_no, sent_date, message_no, sent_time, transfer_type_id,
                                      withdrawal_account_no, deposit_bank_code, deposit_account_no,
                                      amount, memo, response_code, balance_after)
                VALUES ('CAPITAL00001', '20261029', '000123', '143005', 1,
                        '100-000-000002', '999', '200-000-000001',
                        10000, '대출금', '0000', 9999990000)
                """;

        // 거래고유번호 = 업체번호 + 전송일자 + 전문번호. 같은 전문이 두 번 저장되지 않아야 한다
        @Test
        @DisplayName("같은 키로 한 번 더 INSERT하면 DuplicateKeyException이 난다")
        void 같은_거래고유번호로_INSERT하면_DuplicateKeyException이_난다() {
            // given
            jdbcTemplate.update(INSERT_TRANSFER);

            // when, then
            assertThatThrownBy(() -> jdbcTemplate.update(INSERT_TRANSFER))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }
}
