package com.firmbanking.domain.account.service;

import com.firmbanking.domain.account.dto.request.InquireAccountNameRequest;
import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
import com.firmbanking.domain.account.dto.response.AccountNameResponse;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.global.code.ResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("AccountNameService")
class AccountNameServiceTest {

    private static final String BANK_CODE = "999";
    private static final String ACCOUNT_NO = "200-000-000001";

    private AccountMapper accountMapper;
    private AccountNameService accountNameService;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        accountNameService = new AccountNameService(accountMapper);
    }

    @Nested
    @DisplayName("김철수 계좌의 생년월일이 900101일 때")
    class CustomerAccount {

        @BeforeEach
        void setUp() {
            given(accountMapper.findByBankCodeAndAccountNo(BANK_CODE, ACCOUNT_NO))
                    .willReturn(account("김철수", "900101"));
        }

        @Test
        @DisplayName("실명번호 900101로 조회하면 SUCCESS와 예금주명 김철수를 돌려준다")
        void 생년월일이_같으면_예금주명을_돌려준다() {
            // when
            AccountNameResponse result = accountNameService.inquire(request("900101"));

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
            assertThat(result.getHolderName()).isEqualTo("김철수");
        }

        // 프로그램 설계 5절: 실명번호 앞 6자리와 생년월일을 비교한다
        @Test
        @DisplayName("실명번호 9001011234567로 조회하면 앞 6자리만 비교해 SUCCESS를 돌려준다")
        void 실명번호_앞_6자리만_비교한다() {
            // when
            AccountNameResponse result = accountNameService.inquire(request("9001011234567"));

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
        }

        // 전문 명세 5절 B005: 계좌는 있지만 생년월일이 다름
        @Test
        @DisplayName("실명번호 851231로 조회하면 B005를 돌려준다")
        void 생년월일이_다르면_B005다() {
            // when
            AccountNameResponse result = accountNameService.inquire(request("851231"));

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.REAL_NAME_MISMATCH);
        }
    }

    // 전문 명세 5절 B001: 공통부 은행에 대상 계좌가 없음
    @Test
    @DisplayName("계좌가 없으면 B001을 돌려준다")
    void 계좌가_없으면_B001이다() {
        // given
        given(accountMapper.findByBankCodeAndAccountNo(BANK_CODE, ACCOUNT_NO)).willReturn(null);

        // when
        AccountNameResponse result = accountNameService.inquire(request("900101"));

        // then
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("생년월일이 NULL인 업체 계좌를 실명번호 900101로 조회하면 B005를 돌려준다")
    void 생년월일이_없으면_B005다() {
        // given
        given(accountMapper.findByBankCodeAndAccountNo(BANK_CODE, ACCOUNT_NO)).willReturn(account("캐피탈", null));

        // when
        AccountNameResponse result = accountNameService.inquire(request("900101"));

        // then
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.REAL_NAME_MISMATCH);
    }

    private static InquireAccountNameRequest request(String realNameNo) {
        return InquireAccountNameRequest.builder()
                .bankCode(BANK_CODE)
                .accountNo(ACCOUNT_NO)
                .realNameNo(realNameNo)
                .build();
    }

    private static AccountDetailResponse account(String holderName, String holderBirthDate) {
        return AccountDetailResponse.builder()
                .accountId(1L)
                .bankCode(BANK_CODE)
                .accountNo(ACCOUNT_NO)
                .holderName(holderName)
                .holderBirthDate(holderBirthDate)
                .balance(BigDecimal.ZERO)
                .build();
    }
}
