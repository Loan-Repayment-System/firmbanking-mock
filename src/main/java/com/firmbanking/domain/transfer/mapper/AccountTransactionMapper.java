package com.firmbanking.domain.transfer.mapper;

import com.firmbanking.domain.transfer.dto.request.CreateAccountTransactionRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountTransactionMapper {

    /**
     * 생성된 id를 request의 accountTransactionId에 채운다.
     */
    void insert(CreateAccountTransactionRequest request);
}
