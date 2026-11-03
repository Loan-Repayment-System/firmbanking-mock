package com.firmbanking.domain.account.service;

import com.firmbanking.domain.account.dto.request.InquireAccountNameRequest;
import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
import com.firmbanking.domain.account.dto.response.AccountNameResponse;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.global.code.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountNameService {

    private final AccountMapper accountMapper;

    /**
     * 계좌가 없으면 B001, 실명번호가 생년월일로 시작하지 않으면 B005다. 생년월일이 없는 업체 계좌도 B005다.
     */
    public AccountNameResponse inquire(InquireAccountNameRequest request) {
        AccountDetailResponse account =
                accountMapper.findByBankCodeAndAccountNo(request.getBankCode(), request.getAccountNo());
        if (account == null) {
            return reject(ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);
        }
        String birthDate = account.getHolderBirthDate();
        if (birthDate == null || !request.getRealNameNo().startsWith(birthDate)) {
            return reject(ResponseCode.REAL_NAME_MISMATCH);
        }
        return AccountNameResponse.builder()
                .responseCode(ResponseCode.SUCCESS)
                .holderName(account.getHolderName())
                .build();
    }

    private static AccountNameResponse reject(ResponseCode responseCode) {
        return AccountNameResponse.builder()
                .responseCode(responseCode)
                .build();
    }
}
