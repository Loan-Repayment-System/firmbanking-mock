package com.firmbanking.domain.transfer.dto.request;

import com.firmbanking.global.code.TransferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTransferRequest {

    private String companyNo;
    private String sentDate;
    private String messageNo;
    private String sentTime;
    private TransferType transferType;
    private String withdrawalAccountNo;
    private String depositBankCode;
    private String depositAccountNo;
    private BigDecimal amount;
    private String memo;
    private String responseCode;
    private BigDecimal balanceAfter;
}
