package com.firmbanking.domain.account.mapper;

import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    AccountDetailResponse findByBankCodeAndAccountNo(@Param("bankCode") String bankCode,
                                                     @Param("accountNo") String accountNo);
}
