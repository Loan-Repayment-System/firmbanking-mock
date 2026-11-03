package com.firmbanking.domain.company.mapper;

import com.firmbanking.domain.company.dto.response.CompanyDetailResponse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CompanyMapper {

    boolean existsByCompanyNo(String companyNo);

    CompanyDetailResponse findByCompanyNo(String companyNo);
}
