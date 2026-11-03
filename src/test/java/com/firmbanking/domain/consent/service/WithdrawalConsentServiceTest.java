package com.firmbanking.domain.consent.service;

import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.domain.consent.dto.request.ApplyWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.request.CreateWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.request.UpdateWithdrawalConsentRequest;
import com.firmbanking.domain.consent.dto.response.WithdrawalConsentDetailResponse;
import com.firmbanking.domain.consent.mapper.WithdrawalConsentMapper;
import com.firmbanking.global.code.ConsentStatus;
import com.firmbanking.global.code.ResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("WithdrawalConsentService")
class WithdrawalConsentServiceTest {

    private static final String COMPANY_NO = "CAPITAL00001";
    private static final String BANK_CODE = "999";
    private static final String ACCOUNT_NO = "200-000-000001";
    private static final Long ACCOUNT_ID = 10L;
    private static final Long CONSENT_ID = 20L;

    private AccountMapper accountMapper;
    private WithdrawalConsentMapper withdrawalConsentMapper;
    private WithdrawalConsentService withdrawalConsentService;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        withdrawalConsentMapper = mock(WithdrawalConsentMapper.class);
        withdrawalConsentService = new WithdrawalConsentService(accountMapper, withdrawalConsentMapper);
    }

    // 전문 명세 5절 B001: 공통부 은행에 대상 계좌가 없음
    @Test
    @DisplayName("계좌가 없으면 신규 요청에 B001을 돌려주고 동의를 저장하지 않는다")
    void 계좌가_없으면_B001이다() {
        // given
        given(accountMapper.findByBankCodeAndAccountNo(BANK_CODE, ACCOUNT_NO)).willReturn(null);

        // when
        ResponseCode responseCode = withdrawalConsentService.apply(request(ConsentStatus.REGISTERED));

        // then
        assertThat(responseCode).isEqualTo(ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);
        then(withdrawalConsentMapper).should(never()).insert(any());
        then(withdrawalConsentMapper).should(never()).updateStatus(any());
    }

    @Nested
    @DisplayName("동의 행이 없을 때")
    class NoConsent {

        @BeforeEach
        void setUp() {
            givenConsent(null);
        }

        @Test
        @DisplayName("신규 요청이면 0000을 돌려주고 업체번호, 계좌 ID, 등록, L0001, 20261103으로 INSERT한다")
        void 신규면_등록으로_INSERT한다() {
            // when
            ResponseCode responseCode = withdrawalConsentService.apply(request(ConsentStatus.REGISTERED));

            // then
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            ArgumentCaptor<CreateWithdrawalConsentRequest> captor =
                    ArgumentCaptor.forClass(CreateWithdrawalConsentRequest.class);
            then(withdrawalConsentMapper).should().insert(captor.capture());
            assertThat(captor.getValue().getCompanyNo()).isEqualTo(COMPANY_NO);
            assertThat(captor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(captor.getValue().getConsentStatus()).isEqualTo(ConsentStatus.REGISTERED);
            assertThat(captor.getValue().getPayerNo()).isEqualTo("L0001");
            assertThat(captor.getValue().getAppliedDate()).isEqualTo("20261103");
        }

        // 동의한 적 없는 계좌의 해지는 해지된 상태와 같게 본다
        @Test
        @DisplayName("해지 요청이면 0000을 돌려주고 아무것도 저장하지 않는다")
        void 해지면_저장하지_않는다() {
            // when
            ResponseCode responseCode = withdrawalConsentService.apply(request(ConsentStatus.CANCELLED));

            // then
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            then(withdrawalConsentMapper).should(never()).insert(any());
            then(withdrawalConsentMapper).should(never()).updateStatus(any());
        }
    }

    @Nested
    @DisplayName("등록 상태일 때")
    class RegisteredConsent {

        @BeforeEach
        void setUp() {
            givenConsent(ConsentStatus.REGISTERED);
        }

        // 전문 명세 5절 0000: 등록은 이미 그 상태여도 0000
        @Test
        @DisplayName("신규 요청이면 0000을 돌려주고 UPDATE하지 않는다")
        void 신규면_바꾸지_않는다() {
            // when
            ResponseCode responseCode = withdrawalConsentService.apply(request(ConsentStatus.REGISTERED));

            // then
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            then(withdrawalConsentMapper).should(never()).updateStatus(any());
        }

        @Test
        @DisplayName("해지 요청이면 0000을 돌려주고 해지, L0001, 20261103으로 UPDATE한다")
        void 해지면_해지로_UPDATE한다() {
            // when
            ResponseCode responseCode = withdrawalConsentService.apply(request(ConsentStatus.CANCELLED));

            // then
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            assertUpdated(ConsentStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("해지 상태일 때")
    class CancelledConsent {

        @BeforeEach
        void setUp() {
            givenConsent(ConsentStatus.CANCELLED);
        }

        @Test
        @DisplayName("신규 요청이면 0000을 돌려주고 등록, L0001, 20261103으로 UPDATE한다")
        void 신규면_등록으로_UPDATE한다() {
            // when
            ResponseCode responseCode = withdrawalConsentService.apply(request(ConsentStatus.REGISTERED));

            // then
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            assertUpdated(ConsentStatus.REGISTERED);
        }

        // 전문 명세 5절 0000: 등록은 이미 그 상태여도 0000
        @Test
        @DisplayName("해지 요청이면 0000을 돌려주고 UPDATE하지 않는다")
        void 해지면_바꾸지_않는다() {
            // when
            ResponseCode responseCode = withdrawalConsentService.apply(request(ConsentStatus.CANCELLED));

            // then
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            then(withdrawalConsentMapper).should(never()).updateStatus(any());
        }
    }

    private void givenConsent(ConsentStatus status) {
        given(accountMapper.findByBankCodeAndAccountNo(BANK_CODE, ACCOUNT_NO)).willReturn(AccountDetailResponse.builder()
                .accountId(ACCOUNT_ID)
                .bankCode(BANK_CODE)
                .accountNo(ACCOUNT_NO)
                .holderName("김철수")
                .holderBirthDate("900101")
                .balance(BigDecimal.ZERO)
                .build());
        WithdrawalConsentDetailResponse consent = status == null ? null : WithdrawalConsentDetailResponse.builder()
                .consentId(CONSENT_ID)
                .companyNo(COMPANY_NO)
                .accountId(ACCOUNT_ID)
                .consentStatusId(status)
                .payerNo("L0000")
                .appliedDate("20261001")
                .build();
        given(withdrawalConsentMapper.findByCompanyNoAndAccountId(COMPANY_NO, ACCOUNT_ID)).willReturn(consent);
    }

    private void assertUpdated(ConsentStatus status) {
        ArgumentCaptor<UpdateWithdrawalConsentRequest> captor = ArgumentCaptor.forClass(UpdateWithdrawalConsentRequest.class);
        then(withdrawalConsentMapper).should().updateStatus(captor.capture());
        assertThat(captor.getValue().getConsentId()).isEqualTo(CONSENT_ID);
        assertThat(captor.getValue().getConsentStatus()).isEqualTo(status);
        assertThat(captor.getValue().getPayerNo()).isEqualTo("L0001");
        assertThat(captor.getValue().getAppliedDate()).isEqualTo("20261103");
    }

    private static ApplyWithdrawalConsentRequest request(ConsentStatus status) {
        return ApplyWithdrawalConsentRequest.builder()
                .companyNo(COMPANY_NO)
                .bankCode(BANK_CODE)
                .accountNo(ACCOUNT_NO)
                .consentStatus(status)
                .payerNo("L0001")
                .appliedDate("20261103")
                .build();
    }
}
