package com.firmbanking.telegram.controller;

import com.firmbanking.domain.account.dto.request.InquireAccountNameRequest;
import com.firmbanking.domain.account.dto.response.AccountNameResponse;
import com.firmbanking.domain.account.service.AccountNameService;
import com.firmbanking.domain.company.service.CompanyService;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.codec.InvalidTelegramException;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.codec.TelegramConverter;
import com.firmbanking.telegram.controller.field.NameInquiryField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.firmbanking.telegram.codec.TelegramFixture.put;
import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("NameInquiryController")
class NameInquiryControllerTest {

    private CompanyService companyService;
    private AccountNameService accountNameService;
    private NameInquiryController controller;

    @BeforeEach
    void setUp() {
        companyService = mock(CompanyService.class);
        accountNameService = mock(AccountNameService.class);
        controller = new NameInquiryController(companyService, accountNameService);
        given(companyService.isRegistered("CAPITAL00001")).willReturn(true);
    }

    // 전문 명세 5절 A003: 공통부 업체번호가 등록되지 않음
    @Test
    @DisplayName("업체가 등록되지 않았으면 A003을 돌려주고 계좌성명 조회를 호출하지 않는다")
    void 업체가_없으면_A003이다() {
        // given
        given(companyService.isRegistered("CAPITAL00001")).willReturn(false);
        byte[] request = NameInquiryFixture.request("999", "200-000-000001", "900101");

        // when
        ResponseCode responseCode = process(request);

        // then
        assertThat(responseCode).isEqualTo(ResponseCode.UNREGISTERED_COMPANY);
        then(accountNameService).should(never()).inquire(any());
    }

    @Nested
    @DisplayName("개별부 형식이 틀렸을 때")
    class InvalidIndividualPart {

        // 전문 명세 4.5절: 금액은 형식만 검사한다
        @Test
        @DisplayName("금액이 000000000001X면 InvalidTelegramException을 던지고 계좌성명 조회를 호출하지 않는다")
        void 금액이_숫자가_아니면_예외를_던진다() {
            // given
            byte[] request = NameInquiryFixture.request("999", "200-000-000001", "900101");
            put(request, NameInquiryField.AMOUNT, "000000000001X");

            // when, then
            assertThatThrownBy(() -> process(request)).isInstanceOf(InvalidTelegramException.class);
            then(accountNameService).should(never()).inquire(any());
        }

        @Test
        @DisplayName("은행코드가 99A면 InvalidTelegramException을 던진다")
        void 은행코드가_숫자가_아니면_예외를_던진다() {
            // given
            byte[] request = NameInquiryFixture.request("99A", "200-000-000001", "900101");

            // when, then
            assertThatThrownBy(() -> process(request)).isInstanceOf(InvalidTelegramException.class);
        }
    }

    @Nested
    @DisplayName("계좌성명 조회가 SUCCESS를 돌려줄 때")
    class Success {

        @Test
        @DisplayName("예금주명이 김철수면 0000이고 예금주명1은 김철수 뒤에 SPACE이며, 999 / 200-000-000001 / 900101로 조회한다")
        void 예금주명을_쓴다() {
            // given
            byte[] request = NameInquiryFixture.request("999", "200-000-000001", "900101");
            given(accountNameService.inquire(any())).willReturn(success("김철수"));

            // when
            ResponseTelegram response = ResponseTelegram.copyOf(request);
            ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

            // then
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            assertThat(read(response.complete(responseCode), NameInquiryField.HOLDER_NAME))
                    .isEqualTo("김철수      ");
            ArgumentCaptor<InquireAccountNameRequest> captor = ArgumentCaptor.forClass(InquireAccountNameRequest.class);
            then(accountNameService).should().inquire(captor.capture());
            assertThat(captor.getValue().getBankCode()).isEqualTo("999");
            assertThat(captor.getValue().getAccountNo()).isEqualTo("200-000-000001");
            assertThat(captor.getValue().getRealNameNo()).isEqualTo("900101");
        }

        // 전문 명세 4.5절: 예금주명1은 한글 6자까지, 넘치면 자른다
        @Test
        @DisplayName("예금주명이 남궁가나다라마바면 예금주명1은 남궁가나다라(12바이트)다")
        void 예금주명이_길면_자른다() {
            // given
            byte[] request = NameInquiryFixture.request("999", "200-000-000003", "000229");
            given(accountNameService.inquire(any())).willReturn(success("남궁가나다라마바"));

            // when
            ResponseTelegram response = ResponseTelegram.copyOf(request);
            ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

            // then
            assertThat(read(response.complete(responseCode), NameInquiryField.HOLDER_NAME)).isEqualTo("남궁가나다라");
        }
    }

    @Test
    @DisplayName("계좌성명 조회가 B001을 돌려주면 B001이고 예금주명1은 요청 그대로 SPACE다")
    void 거절이면_예금주명을_쓰지_않는다() {
        // given
        byte[] request = NameInquiryFixture.request("999", "200-999-999999", "900101");
        given(accountNameService.inquire(any())).willReturn(
                AccountNameResponse.builder().responseCode(ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND).build());

        // when
        ResponseTelegram response = ResponseTelegram.copyOf(request);
        ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

        // then
        assertThat(responseCode).isEqualTo(ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);
        assertThat(read(response.complete(responseCode), NameInquiryField.HOLDER_NAME)).isEqualTo(" ".repeat(12));
    }

    private ResponseCode process(byte[] request) {
        return controller.process(TelegramConverter.read(request), ResponseTelegram.copyOf(request));
    }

    private static AccountNameResponse success(String holderName) {
        return AccountNameResponse.builder()
                .responseCode(ResponseCode.SUCCESS)
                .holderName(holderName)
                .build();
    }
}
