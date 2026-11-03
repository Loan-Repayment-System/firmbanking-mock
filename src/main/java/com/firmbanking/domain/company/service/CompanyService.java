package com.firmbanking.domain.company.service;

import com.firmbanking.domain.company.mapper.CompanyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyMapper companyMapper;

    public boolean isRegistered(String companyNo) {
        return companyMapper.existsByCompanyNo(companyNo);
    }
}
