package com.firmbanking.domain.transfer.dto.request;

import com.firmbanking.global.code.TransferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 이체 요청. 거래고유번호는 업체번호 + 전송일자 + 전문번호다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecuteTransferRequest {

    private String companyNo;
    private String sentDate;
    private String messageNo;
    private String sentTime;
    private TransferType transferType;
    private String withdrawalBankCode;
    private String withdrawalAccountNo;
    private String depositBankCode;
    private String depositAccountNo;
    private BigDecimal amount;
    private String memo;
}
