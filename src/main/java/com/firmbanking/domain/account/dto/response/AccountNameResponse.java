package com.firmbanking.domain.account.dto.response;

import com.firmbanking.global.code.ResponseCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계좌성명 조회 결과. 응답코드가 SUCCESS일 때만 holderName이 있다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountNameResponse {

    private ResponseCode responseCode;
    private String holderName;
}
