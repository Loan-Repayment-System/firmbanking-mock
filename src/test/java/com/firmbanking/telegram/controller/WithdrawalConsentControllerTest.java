package com.firmbanking.telegram.controller;

import com.firmbanking.domain.company.service.CompanyService;
import com.firmbanking.domain.consent.dto.request.ApplyWithdrawalConsentRequest;
import com.firmbanking.domain.consent.service.WithdrawalConsentService;
import com.firmbanking.global.code.ConsentStatus;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.codec.InvalidTelegramException;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.codec.TelegramConverter;
import com.firmbanking.telegram.controller.field.WithdrawalConsentField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("WithdrawalConsentController")
class WithdrawalConsentControllerTest {

    private CompanyService companyService;
    private WithdrawalConsentService withdrawalConsentService;
    private WithdrawalConsentController controller;

    @BeforeEach
    void setUp() {
        companyService = mock(CompanyService.class);
        withdrawalConsentService = mock(WithdrawalConsentService.class);
        controller = new WithdrawalConsentController(companyService, withdrawalConsentService);
        given(companyService.isRegistered("CAPITAL00001")).willReturn(true);
    }

    // 전문 명세 5절 A003: 공통부 업체번호가 등록되지 않음. A 계열은 개별부 응답 필드를 채우지 않는다
    @Test
    @DisplayName("업체가 등록되지 않았으면 A003이고 출금 동의를 호출하지 않으며 처리여부·불능코드는 SPACE다")
    void 업체가_없으면_A003이다() {
        // given
        given(companyService.isRegistered("CAPITAL00001")).willReturn(false);
        byte[] request = WithdrawalConsentFixture.request("999", "200-000-000001", "1");

        // when
        ResponseTelegram response = ResponseTelegram.copyOf(request);
        ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

        // then
        assertThat(responseCode).isEqualTo(ResponseCode.UNREGISTERED_COMPANY);
        then(withdrawalConsentService).should(never()).apply(any());
        byte[] bytes = response.complete(responseCode);
        assertThat(read(bytes, WithdrawalConsentField.PROCESSED)).isEqualTo(" ");
        assertThat(read(bytes, WithdrawalConsentField.FAILURE_CODE)).isEqualTo("    ");
    }

    // 전문 명세 5절 A001: 신청구분이 1·2가 아님
    @Test
    @DisplayName("신청구분이 3이면 InvalidTelegramException을 던지고 출금 동의를 호출하지 않는다")
    void 신청구분이_1_2가_아니면_예외를_던진다() {
        // given
        byte[] request = WithdrawalConsentFixture.request("999", "200-000-000001", "3");

        // when, then
        assertThatThrownBy(() -> controller.process(TelegramConverter.read(request), ResponseTelegram.copyOf(request)))
                .isInstanceOf(InvalidTelegramException.class);
        then(withdrawalConsentService).should(never()).apply(any());
    }

    @Nested
    @DisplayName("출금 동의가 0000을 돌려줄 때")
    class Success {

        @BeforeEach
        void setUp() {
            given(withdrawalConsentService.apply(any())).willReturn(ResponseCode.SUCCESS);
        }

        @Test
        @DisplayName("신청구분 1이면 0000, 처리순번 0000000, 처리여부 Y, 불능코드 SPACE이고 요청 값과 등록으로 출금 동의를 호출한다")
        void 신규면_등록으로_호출한다() {
            // given
            byte[] request = WithdrawalConsentFixture.request("999", "200-000-000001", "1");

            // when
            ResponseTelegram response = ResponseTelegram.copyOf(request);
            ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

            // then
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            byte[] bytes = response.complete(responseCode);
            assertThat(read(bytes, WithdrawalConsentField.PROCESS_SEQUENCE)).isEqualTo("0000000");
            assertThat(read(bytes, WithdrawalConsentField.PROCESSED)).isEqualTo("Y");
            assertThat(read(bytes, WithdrawalConsentField.FAILURE_CODE)).isEqualTo("    ");
            ApplyWithdrawalConsentRequest applied = captureApplied();
            assertThat(applied.getCompanyNo()).isEqualTo("CAPITAL00001");
            assertThat(applied.getBankCode()).isEqualTo("999");
            assertThat(applied.getAccountNo()).isEqualTo("200-000-000001");
            assertThat(applied.getConsentStatus()).isEqualTo(ConsentStatus.REGISTERED);
            assertThat(applied.getPayerNo()).isEqualTo("L0001");
            assertThat(applied.getAppliedDate()).isEqualTo("20261103");
        }

        @Test
        @DisplayName("신청구분 2면 해지로 출금 동의를 호출한다")
        void 해지면_해지로_호출한다() {
            // given
            byte[] request = WithdrawalConsentFixture.request("999", "200-000-000001", "2");

            // when
            controller.process(TelegramConverter.read(request), ResponseTelegram.copyOf(request));

            // then
            assertThat(captureApplied().getConsentStatus()).isEqualTo(ConsentStatus.CANCELLED);
        }
    }

    // 전문 명세 4.4절: 불능이면 불능코드는 공통부 응답코드와 같은 값
    @Test
    @DisplayName("출금 동의가 B001을 돌려주면 B001, 처리여부 N, 불능코드 B001이다")
    void 거절이면_불능으로_쓴다() {
        // given
        byte[] request = WithdrawalConsentFixture.request("999", "200-999-999999", "1");
        given(withdrawalConsentService.apply(any())).willReturn(ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);

        // when
        ResponseTelegram response = ResponseTelegram.copyOf(request);
        ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

        // then
        assertThat(responseCode).isEqualTo(ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);
        byte[] bytes = response.complete(responseCode);
        assertThat(read(bytes, WithdrawalConsentField.PROCESSED)).isEqualTo("N");
        assertThat(read(bytes, WithdrawalConsentField.FAILURE_CODE)).isEqualTo("B001");
    }

    private ApplyWithdrawalConsentRequest captureApplied() {
        ArgumentCaptor<ApplyWithdrawalConsentRequest> captor = ArgumentCaptor.forClass(ApplyWithdrawalConsentRequest.class);
        then(withdrawalConsentService).should().apply(captor.capture());
        return captor.getValue();
    }
}
