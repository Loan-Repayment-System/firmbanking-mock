package com.firmbanking.domain.transfer.dto.response;

import com.firmbanking.global.code.ResponseCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이체결과조회 결과. 응답코드가 SUCCESS일 때만 transfer(원거래)가 있다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferInquiryResponse {

    private ResponseCode responseCode;
    private TransferDetailResponse transfer;
}
