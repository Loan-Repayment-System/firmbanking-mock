package com.firmbanking.domain.transfer.dto.response;

import com.firmbanking.global.code.TransferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferDetailResponse {

    private Long transferId;
    private String companyNo;
    private String sentDate;
    private String messageNo;
    private String sentTime;
    private TransferType transferTypeId;
    private String withdrawalAccountNo;
    private String depositBankCode;
    private String depositAccountNo;
    private BigDecimal amount;
    private String memo;
    private String responseCode;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
}
