package com.firmbanking.domain.consent.mapper;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.consent.dto.request.CreateWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.request.UpdateWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.response.WithdrawalConsentDetailResponse;
import com.firmbanking.global.code.ConsentStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("WithdrawalConsentMapper")
class WithdrawalConsentMapperIntegrationTest {

    private static final String COMPANY_NO = "CAPITAL00001";

    @Autowired
    private WithdrawalConsentMapper withdrawalConsentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long accountId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO account (bank_code, account_no, holder_name, holder_birth_date, balance)
                VALUES ('999', '200-000-000001', '김철수', '900101', 10000000)
                """);
        accountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM account WHERE bank_code = '999' AND account_no = '200-000-000001'",
                Long.class);
    }

    @Test
    @DisplayName("고객 계좌 200-000-000001에 동의 행이 없으면 null이다")
    void 동의가_없으면_null이다() {
        // when
        WithdrawalConsentDetailResponse consent =
                withdrawalConsentMapper.findByCompanyNoAndAccountId(COMPANY_NO, accountId);

        // then
        assertThat(consent).isNull();
    }

    @Nested
    @DisplayName("등록 / L0001 / 20261103으로 INSERT했을 때")
    class Registered {

        @BeforeEach
        void setUp() {
            withdrawalConsentMapper.insert(CreateWithdrawalConsentRequest.builder()
                    .companyNo(COMPANY_NO)
                    .accountId(accountId)
                    .consentStatus(ConsentStatus.REGISTERED)
                    .payerNo("L0001")
                    .appliedDate("20261103")
                    .build());
        }

        @Test
        @DisplayName("조회하면 등록, L0001, 20261103이다")
        void 넣은_값으로_조회된다() {
            // when
            WithdrawalConsentDetailResponse consent =
                    withdrawalConsentMapper.findByCompanyNoAndAccountId(COMPANY_NO, accountId);

            // then
            assertThat(consent.getConsentId()).isNotNull();
            assertThat(consent.getCompanyNo()).isEqualTo(COMPANY_NO);
            assertThat(consent.getAccountId()).isEqualTo(accountId);
            assertThat(consent.getConsentStatusId()).isEqualTo(ConsentStatus.REGISTERED);
            assertThat(consent.getPayerNo()).isEqualTo("L0001");
            assertThat(consent.getAppliedDate()).isEqualTo("20261103");
        }

        @Test
        @DisplayName("해지 / L0002 / 20261104로 UPDATE하면 해지, L0002, 20261104로 조회된다")
        void 상태와_기록값이_바뀐다() {
            // given
            Long consentId = withdrawalConsentMapper.findByCompanyNoAndAccountId(COMPANY_NO, accountId).getConsentId();

            // when
            withdrawalConsentMapper.updateStatus(UpdateWithdrawalConsentRequest.builder()
                    .consentId(consentId)
                    .consentStatus(ConsentStatus.CANCELLED)
                    .payerNo("L0002")
                    .appliedDate("20261104")
                    .build());

            // then
            WithdrawalConsentDetailResponse consent =
                    withdrawalConsentMapper.findByCompanyNoAndAccountId(COMPANY_NO, accountId);
            assertThat(consent.getConsentStatusId()).isEqualTo(ConsentStatus.CANCELLED);
            assertThat(consent.getPayerNo()).isEqualTo("L0002");
            assertThat(consent.getAppliedDate()).isEqualTo("20261104");
        }
    }
}
