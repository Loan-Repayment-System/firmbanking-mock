package com.firmbanking.telegram.controller;

import com.firmbanking.domain.company.service.CompanyService;
import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.domain.transfer.service.TransferService;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.global.code.TransferType;
import com.firmbanking.telegram.codec.InvalidTelegramException;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.codec.TelegramConverter;
import com.firmbanking.telegram.controller.field.PaymentField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("PaymentController")
class PaymentControllerTest {

    private CompanyService companyService;
    private TransferService transferService;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        companyService = mock(CompanyService.class);
        transferService = mock(TransferService.class);
        controller = new PaymentController(companyService, transferService);
        given(companyService.isRegistered("CAPITAL00001")).willReturn(true);
    }

    // 전문 명세 5절 A003: 공통부 업체번호가 등록되지 않음
    @Test
    @DisplayName("업체가 등록되지 않았으면 A003을 돌려주고 이체를 호출하지 않으며 출금후잔액부호·출금후잔액·수수료는 요청 그대로다")
    void 업체가_없으면_A003이다() {
        // given
        given(companyService.isRegistered("CAPITAL00001")).willReturn(false);
        byte[] request = PaymentFixture.request("100-000-000002", 1_000_000, "999", "200-000-000001");

        // when
        ResponseTelegram response = ResponseTelegram.copyOf(request);
        ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

        // then
        assertThat(responseCode).isEqualTo(ResponseCode.UNREGISTERED_COMPANY);
        then(transferService).should(never()).transfer(any());
        assertUnwritten(response.complete(responseCode));
    }

    // 전문 명세 5절 A001: 출금금액이 1 미만
    @Test
    @DisplayName("출금금액이 0이면 InvalidTelegramException을 던지고 이체를 호출하지 않는다")
    void 출금금액이_0이면_예외를_던진다() {
        // given
        byte[] request = PaymentFixture.request("100-000-000002", 0, "999", "200-000-000001");

        // when, then
        assertThatThrownBy(() -> controller.process(TelegramConverter.read(request), ResponseTelegram.copyOf(request)))
                .isInstanceOf(InvalidTelegramException.class);
        then(transferService).should(never()).transfer(any());
    }

    @Nested
    @DisplayName("이체가 SUCCESS와 잔액 9,999,000,000을 돌려줄 때")
    class Success {

        private byte[] request;

        @BeforeEach
        void setUp() {
            request = PaymentFixture.request("100-000-000002", 1_000_000, "999", "200-000-000001");
            given(transferService.transfer(any())).willReturn(TransferResultResponse.builder()
                    .responseCode(ResponseCode.SUCCESS)
                    .balanceAfter(new BigDecimal("9999000000"))
                    .build());
        }

        @Test
        @DisplayName("공통부 거래고유번호·전송시간·은행코드와 개별부 값으로 지급 이체를 요청한다")
        void 요청_값으로_이체한다() {
            // when
            controller.process(TelegramConverter.read(request), ResponseTelegram.copyOf(request));

            // then
            ArgumentCaptor<ExecuteTransferRequest> captor = ArgumentCaptor.forClass(ExecuteTransferRequest.class);
            then(transferService).should().transfer(captor.capture());
            ExecuteTransferRequest transfer = captor.getValue();
            assertThat(transfer.getCompanyNo()).isEqualTo("CAPITAL00001");
            assertThat(transfer.getSentDate()).isEqualTo("20261029");
            assertThat(transfer.getMessageNo()).isEqualTo("000123");
            assertThat(transfer.getSentTime()).isEqualTo("143005");
            assertThat(transfer.getTransferType()).isEqualTo(TransferType.PAYMENT);
            assertThat(transfer.getWithdrawalBankCode()).isEqualTo("999");
            assertThat(transfer.getWithdrawalAccountNo()).isEqualTo("100-000-000002");
            assertThat(transfer.getDepositBankCode()).isEqualTo("999");
            assertThat(transfer.getDepositAccountNo()).isEqualTo("200-000-000001");
            assertThat(transfer.getAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
            assertThat(transfer.getMemo()).isEqualTo("대출금");
        }

        // 전문 명세 4.1절: 출금후잔액부호·출금후잔액·수수료는 응답
        @Test
        @DisplayName("0000이고 출금후잔액부호 +, 출금후잔액 0009999000000, 수수료 000000000이다")
        void 출금후잔액과_수수료를_쓴다() {
            // when
            ResponseTelegram response = ResponseTelegram.copyOf(request);
            ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

            // then
            byte[] bytes = response.complete(responseCode);
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            assertThat(read(bytes, PaymentField.BALANCE_SIGN)).isEqualTo("+");
            assertThat(read(bytes, PaymentField.BALANCE_AFTER)).isEqualTo("0009999000000");
            assertThat(read(bytes, PaymentField.FEE)).isEqualTo("000000000");
        }
    }

    @Test
    @DisplayName("이체가 B003을 돌려주면 B003이고 출금후잔액부호·출금후잔액·수수료는 요청 그대로다")
    void 거절이면_출금후잔액을_쓰지_않는다() {
        // given
        byte[] request = PaymentFixture.request("100-000-000002", 1_000_000, "999", "200-000-000001");
        given(transferService.transfer(any())).willReturn(
                TransferResultResponse.builder().responseCode(ResponseCode.INSUFFICIENT_BALANCE).build());

        // when
        ResponseTelegram response = ResponseTelegram.copyOf(request);
        ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

        // then
        assertThat(responseCode).isEqualTo(ResponseCode.INSUFFICIENT_BALANCE);
        assertUnwritten(response.complete(responseCode));
    }

    private static void assertUnwritten(byte[] bytes) {
        assertThat(read(bytes, PaymentField.BALANCE_SIGN)).isEqualTo(" ");
        assertThat(read(bytes, PaymentField.BALANCE_AFTER)).isEqualTo("0000000000000");
        assertThat(read(bytes, PaymentField.FEE)).isEqualTo("000000000");
    }
}
