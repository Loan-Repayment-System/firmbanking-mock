package com.firmbanking.domain.company.mapper;

import com.firmbanking.TestcontainersConfig;
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
    }
}
