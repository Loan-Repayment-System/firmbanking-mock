package com.firmbanking.telegram.controller;

import com.firmbanking.domain.company.service.CompanyService;
import com.firmbanking.domain.transfer.dto.request.InquireTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.domain.transfer.dto.response.TransferInquiryResponse;
import com.firmbanking.domain.transfer.service.TransferInquiryService;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.global.code.TransferType;
import com.firmbanking.telegram.codec.InvalidTelegramException;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.codec.TelegramConverter;
import com.firmbanking.telegram.controller.field.TransferInquiryField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.firmbanking.telegram.codec.TelegramFixture.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@DisplayName("TransferInquiryController")
class TransferInquiryControllerTest {

    private CompanyService companyService;
    private TransferInquiryService transferInquiryService;
    private TransferInquiryController controller;

    @BeforeEach
    void setUp() {
        companyService = mock(CompanyService.class);
        transferInquiryService = mock(TransferInquiryService.class);
        controller = new TransferInquiryController(companyService, transferInquiryService);
        given(companyService.isRegistered("CAPITAL00001")).willReturn(true);
    }

    // 전문 명세 5절 A003: 공통부 업체번호가 등록되지 않음
    @Test
    @DisplayName("업체가 등록되지 않았으면 A003을 돌려주고 결과조회를 호출하지 않으며 응답 필드는 요청 그대로다")
    void 업체가_없으면_A003이다() {
        // given
        given(companyService.isRegistered("CAPITAL00001")).willReturn(false);
        byte[] request = TransferInquiryFixture.request("20261029", "000100");

        // when
        ResponseTelegram response = ResponseTelegram.copyOf(request);
        ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

        // then
        assertThat(responseCode).isEqualTo(ResponseCode.UNREGISTERED_COMPANY);
        then(transferInquiryService).should(never()).inquire(any());
        assertUnwritten(response.complete(responseCode));
    }

    // 전문 명세 5절 A001: 원거래전송일자가 없는 날짜
    @Test
    @DisplayName("원거래전송일자가 20261340이면 InvalidTelegramException을 던지고 결과조회를 호출하지 않는다")
    void 원거래전송일자가_없는_날짜면_예외를_던진다() {
        // given
        byte[] request = TransferInquiryFixture.request("20261340", "000100");

        // when, then
        assertThatThrownBy(() -> controller.process(TelegramConverter.read(request), ResponseTelegram.copyOf(request)))
                .isInstanceOf(InvalidTelegramException.class);
        then(transferInquiryService).should(never()).inquire(any());
    }

    @Test
    @DisplayName("원거래 20261029 / 000100을 조회하면 CAPITAL00001 / 20261029 / 000100으로 결과조회를 호출한다")
    void 원거래_거래고유번호로_조회한다() {
        // given
        byte[] request = TransferInquiryFixture.request("20261029", "000100");
        given(transferInquiryService.inquire(any())).willReturn(found("0000"));

        // when
        controller.process(TelegramConverter.read(request), ResponseTelegram.copyOf(request));

        // then
        ArgumentCaptor<InquireTransferRequest> captor = ArgumentCaptor.forClass(InquireTransferRequest.class);
        then(transferInquiryService).should().inquire(captor.capture());
        assertThat(captor.getValue().getCompanyNo()).isEqualTo("CAPITAL00001");
        assertThat(captor.getValue().getSentDate()).isEqualTo("20261029");
        assertThat(captor.getValue().getMessageNo()).isEqualTo("000100");
    }

    @Nested
    @DisplayName("원거래가 있을 때")
    class Found {

        private final byte[] request = TransferInquiryFixture.request("20261029", "000100");

        // 전문 명세 4.3절: 정상이면 정상처리금액은 의뢰금액, 처리불능금액은 0
        @Test
        @DisplayName("원거래가 0000이면 0000이고 원거래 계좌, 의뢰·정상처리금액 1,000,000, 처리불능금액 0, 수수료 0, 이체시간 143007, 처리결과 0000이다")
        void 정상_원거래를_쓴다() {
            // given
            given(transferInquiryService.inquire(any())).willReturn(found("0000"));

            // when
            ResponseTelegram response = ResponseTelegram.copyOf(request);
            ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

            // then
            byte[] bytes = response.complete(responseCode);
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            assertThat(read(bytes, TransferInquiryField.WITHDRAWAL_ACCOUNT_NO)).isEqualTo("100-000-000002 ");
            assertThat(read(bytes, TransferInquiryField.DEPOSIT_BANK_CODE)).isEqualTo("999");
            assertThat(read(bytes, TransferInquiryField.DEPOSIT_ACCOUNT_NO)).isEqualTo("200-000-000001 ");
            assertThat(read(bytes, TransferInquiryField.REQUESTED_AMOUNT)).isEqualTo("0000001000000");
            assertThat(read(bytes, TransferInquiryField.PROCESSED_AMOUNT)).isEqualTo("0000001000000");
            assertThat(read(bytes, TransferInquiryField.UNPROCESSED_AMOUNT)).isEqualTo("0000000000000");
            assertThat(read(bytes, TransferInquiryField.FEE)).isEqualTo("000000000");
            assertThat(read(bytes, TransferInquiryField.TRANSFER_TIME)).isEqualTo("143007");
            assertThat(read(bytes, TransferInquiryField.PROCESS_RESULT)).isEqualTo("0000");
        }

        // 전문 명세 4.3절: 정상이 아니면 정상처리금액은 0, 처리불능금액은 의뢰금액
        @Test
        @DisplayName("원거래가 B003이면 공통부는 0000이고 정상처리금액 0, 처리불능금액 1,000,000, 처리결과 B003이다")
        void 거절된_원거래를_쓴다() {
            // given
            given(transferInquiryService.inquire(any())).willReturn(found("B003"));

            // when
            ResponseTelegram response = ResponseTelegram.copyOf(request);
            ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

            // then
            byte[] bytes = response.complete(responseCode);
            assertThat(responseCode).isEqualTo(ResponseCode.SUCCESS);
            assertThat(read(bytes, TransferInquiryField.PROCESSED_AMOUNT)).isEqualTo("0000000000000");
            assertThat(read(bytes, TransferInquiryField.UNPROCESSED_AMOUNT)).isEqualTo("0000001000000");
            assertThat(read(bytes, TransferInquiryField.PROCESS_RESULT)).isEqualTo("B003");
        }
    }

    // 전문 명세 5절 D002: 조회한 거래고유번호의 이체가 없음
    @Test
    @DisplayName("결과조회가 D002를 돌려주면 D002이고 응답 필드는 요청 그대로다")
    void 원거래가_없으면_D002다() {
        // given
        byte[] request = TransferInquiryFixture.request("20261029", "999999");
        given(transferInquiryService.inquire(any())).willReturn(
                TransferInquiryResponse.builder().responseCode(ResponseCode.ORIGINAL_TRANSFER_NOT_FOUND).build());

        // when
        ResponseTelegram response = ResponseTelegram.copyOf(request);
        ResponseCode responseCode = controller.process(TelegramConverter.read(request), response);

        // then
        assertThat(responseCode).isEqualTo(ResponseCode.ORIGINAL_TRANSFER_NOT_FOUND);
        assertUnwritten(response.complete(responseCode));
    }

    private static void assertUnwritten(byte[] bytes) {
        assertThat(read(bytes, TransferInquiryField.WITHDRAWAL_ACCOUNT_NO)).isBlank();
        assertThat(read(bytes, TransferInquiryField.REQUESTED_AMOUNT)).isEqualTo("0000000000000");
        assertThat(read(bytes, TransferInquiryField.TRANSFER_TIME)).isEqualTo("000000");
        assertThat(read(bytes, TransferInquiryField.PROCESS_RESULT)).isBlank();
    }

    private static TransferInquiryResponse found(String responseCode) {
        return TransferInquiryResponse.builder()
                .responseCode(ResponseCode.SUCCESS)
                .transfer(TransferDetailResponse.builder()
                        .transferId(1L)
                        .companyNo("CAPITAL00001")
                        .sentDate("20261029")
                        .messageNo("000100")
                        .sentTime("143005")
                        .transferTypeId(TransferType.PAYMENT)
                        .withdrawalAccountNo("100-000-000002")
                        .depositBankCode("999")
                        .depositAccountNo("200-000-000001")
                        .amount(new BigDecimal("1000000"))
                        .memo("대출금")
                        .responseCode(responseCode)
                        .createdAt(LocalDateTime.of(2026, 10, 29, 14, 30, 7))
                        .build())
                .build();
    }
}
