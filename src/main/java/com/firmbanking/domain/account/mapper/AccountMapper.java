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
     * 잔액을 balance로 바꾼다. 새 잔액은 조회한 잔액으로 호출하는 쪽이 계산한다.
     */
    void updateBalance(@Param("accountId") Long accountId, @Param("balance") BigDecimal balance);
}
