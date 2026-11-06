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
     * 조회한 행에 배타 잠금을 건다. 같은 행을 잠그려는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다.
     */
    AccountDetailResponse findByBankCodeAndAccountNoForUpdate(@Param("bankCode") String bankCode,
                                                              @Param("accountNo") String accountNo);

    /**
     * 잔액을 balance로 바꾼다. 새 잔액은 조회한 잔액으로 호출하는 쪽이 계산한다.
     */
    void updateBalance(@Param("accountId") Long accountId, @Param("balance") BigDecimal balance);
}
