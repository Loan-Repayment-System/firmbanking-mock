package com.firmbanking.domain.company.mapper;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.domain.company.dto.response.CompanyDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("CompanyMapper")
class CompanyMapperIntegrationTest {

    @Autowired
    private CompanyMapper companyMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Nested
    @DisplayName("V3 기준 데이터로 업체 CAPITAL00001만 있을 때")
    class BaseData {

        @Test
        @DisplayName("CAPITAL00001을 조회하면 true다")
        void 등록된_업체번호면_true다() {
            // when
            boolean exists = companyMapper.existsByCompanyNo("CAPITAL00001");

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("CAPITAL00002를 조회하면 false다")
        void 등록되지_않은_업체번호면_false다() {
            // when
            boolean exists = companyMapper.existsByCompanyNo("CAPITAL00002");

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("CAPITAL00001을 findByCompanyNo하면 지급 계좌 id는 100-000-000002, 수납 계좌 id는 100-000-000001의 id다")
        void 업체의_업체_계좌를_돌려준다() {
            // when
            CompanyDetailResponse company = companyMapper.findByCompanyNo("CAPITAL00001");

            // then
            assertThat(company.getCompanyNo()).isEqualTo("CAPITAL00001");
            assertThat(company.getCompanyName()).isEqualTo("캐피탈");
            assertThat(company.getPaymentAccountId())
                    .isEqualTo(accountMapper.findByBankCodeAndAccountNo("999", "100-000-000002").getAccountId());
            assertThat(company.getCollectionAccountId())
                    .isEqualTo(accountMapper.findByBankCodeAndAccountNo("999", "100-000-000001").getAccountId());
        }

        @Test
        @DisplayName("CAPITAL00002를 findByCompanyNo하면 null이다")
        void 없는_업체번호면_null이다() {
            // when
            CompanyDetailResponse company = companyMapper.findByCompanyNo("CAPITAL00002");

            // then
            assertThat(company).isNull();
        }
    }
}
