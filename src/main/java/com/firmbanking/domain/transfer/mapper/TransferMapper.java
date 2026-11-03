package com.firmbanking.domain.transfer.mapper;

import com.firmbanking.domain.transfer.dto.request.CreateTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TransferMapper {

    TransferDetailResponse findByCompanyNoAndSentDateAndMessageNo(@Param("companyNo") String companyNo,
                                                                  @Param("sentDate") String sentDate,
                                                                  @Param("messageNo") String messageNo);

    /**
     * 같은 거래고유번호가 이미 있으면 DuplicateKeyException이다.
     */
    void insert(CreateTransferRequest request);
}
