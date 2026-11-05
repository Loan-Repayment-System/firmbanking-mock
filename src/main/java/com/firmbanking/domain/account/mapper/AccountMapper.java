package com.firmbanking.domain.account.mapper;

import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface AccountMapper {

    AccountDetailResponse findByBankCodeAndAccountNo(@Param("bankCode") String bankCode,
                                                     @Param("accountNo") String accountNo);

    /**
     * 잔액이 amount 이상일 때만 amount를 뺀다. 잔액이 모자라면 0을 돌려준다.
     */
    int updateWithdrawal(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    void updateDeposit(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);
}
