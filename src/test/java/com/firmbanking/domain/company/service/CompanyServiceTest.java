package com.firmbanking.domain.company.service;

import com.firmbanking.domain.company.mapper.CompanyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("CompanyService")
class CompanyServiceTest {

    private CompanyMapper companyMapper;
    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyMapper = mock(CompanyMapper.class);
        companyService = new CompanyService(companyMapper);
    }

    @Test
    @DisplayName("매퍼가 CAPITAL00001이 있다고 하면 등록된 업체다")
    void 업체가_있으면_true를_돌려준다() {
        // given
        given(companyMapper.existsByCompanyNo("CAPITAL00001")).willReturn(true);

        // when
        boolean registered = companyService.isRegistered("CAPITAL00001");

        // then
        assertThat(registered).isTrue();
    }

    @Test
    @DisplayName("매퍼가 CAPITAL00002가 없다고 하면 등록되지 않은 업체다")
    void 업체가_없으면_false를_돌려준다() {
        // given
        given(companyMapper.existsByCompanyNo("CAPITAL00002")).willReturn(false);

        // when
        boolean registered = companyService.isRegistered("CAPITAL00002");

        // then
        assertThat(registered).isFalse();
    }
}
