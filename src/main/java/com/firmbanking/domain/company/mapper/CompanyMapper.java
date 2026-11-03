package com.firmbanking.domain.company.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CompanyMapper {

    boolean existsByCompanyNo(String companyNo);
}
