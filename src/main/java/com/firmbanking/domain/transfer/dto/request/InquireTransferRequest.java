package com.firmbanking.domain.transfer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이체결과조회 요청. 원거래의 거래고유번호(업체번호 + 전송일자 + 전문번호)다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquireTransferRequest {

    private String companyNo;
    private String sentDate;
    private String messageNo;
}
