package com.firmbanking.domain.transfer.dto.response;

import com.firmbanking.global.code.ResponseCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 이체 결과. 응답코드가 SUCCESS일 때만 balanceAfter(출금 후 출금계좌 잔액)가 있다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResultResponse {

    private ResponseCode responseCode;
    private BigDecimal balanceAfter;
}
