package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.global.code.TransferType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("TransferService")
class TransferServiceTest {

    private static final String COMPANY_NO = "CAPITAL00001";
    private static final String SENT_DATE = "20261029";
    private static final String MESSAGE_NO = "000123";
    private static final BigDecimal AMOUNT = new BigDecimal("1000000");
    private static final BigDecimal BALANCE_AFTER = new BigDecimal("9999000000");

    private TransferMapper transferMapper;
    private TransferProcessor transferProcessor;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferMapper = mock(TransferMapper.class);
        transferProcessor = mock(TransferProcessor.class);
        transferService = new TransferService(transferMapper, transferProcessor);
    }

    @Test
    @DisplayName("같은 거래고유번호의 이체 결과가 없으면 TransferProcessor의 결과를 돌려준다")
    void 저장된_결과가_없으면_처리한다() {
        // given
        givenSaved(null);
        TransferResultResponse processed = success();
        given(transferProcessor.process(any())).willReturn(processed);

        // when
        TransferResultResponse result = transferService.transfer(request());

        // then
        assertThat(result).isSameAs(processed);
    }

    @Nested
    @DisplayName("같은 거래고유번호의 이체 결과가 있을 때")
    class Saved {

        // 프로그램 설계 1절: 전송시간은 중복 비교에 쓰지 않는다
        @Test
        @DisplayName("저장된 결과가 0000 / 잔액 9,999,000,000이고 전송시간만 다르면 0000 / 9,999,000,000을 돌려주고 처리하지 않는다")
        void 전송시간만_다르면_저장된_결과다() {
            // given
            givenSaved(saved(ResponseCode.SUCCESS, BALANCE_AFTER));

            // when
            TransferResultResponse result = transferService.transfer(request());

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
            assertThat(result.getBalanceAfter()).isEqualByComparingTo(BALANCE_AFTER);
            then(transferProcessor).should(never()).process(any());
        }

        // 전문 명세 5절: B003은 같은 번호로 재전송하면 같은 응답
        @Test
        @DisplayName("저장된 결과가 B003이고 내용이 같으면 B003을 돌려주고 처리하지 않는다")
        void 저장된_거절이면_같은_거절이다() {
            // given
            givenSaved(saved(ResponseCode.INSUFFICIENT_BALANCE, null));

            // when
            TransferResultResponse result = transferService.transfer(request());

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.INSUFFICIENT_BALANCE);
            then(transferProcessor).should(never()).process(any());
        }

        // 전문 명세 5절 D001: 같은 거래고유번호에 내용이 다른 요청
        @ParameterizedTest(name = "{0}이 다르면 D001")
        @MethodSource("com.firmbanking.domain.transfer.service.TransferServiceTest#differentRequests")
        @DisplayName("거래 종류·출금계좌번호·입금은행코드·입금계좌번호·출금금액·적요 중 하나가 다르면 D001을 돌려주고 처리하지 않는다")
        void 내용이_다르면_D001이다(String field, ExecuteTransferRequest request) {
            // given
            givenSaved(saved(ResponseCode.SUCCESS, BALANCE_AFTER));

            // when
            TransferResultResponse result = transferService.transfer(request);

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.DUPLICATE_TRANSACTION_NO);
            then(transferProcessor).should(never()).process(any());
        }
    }

    // 프로그램 설계 2절: 동시에 같은 전문이 들어와 나중 쪽이 키 중복에 걸린 경우
    @Nested
    @DisplayName("처리 중 키 중복 예외가 날 때")
    class DuplicateKey {

        @BeforeEach
        void setUp() {
            given(transferProcessor.process(any())).willThrow(new DuplicateKeyException("uq_transfer"));
        }

        @Test
        @DisplayName("다시 조회한 결과가 0000 / 9,999,000,000이고 내용이 같으면 0000 / 9,999,000,000을 돌려준다")
        void 다시_조회한_결과를_돌려준다() {
            // given
            given(transferMapper.findByCompanyNoAndSentDateAndMessageNo(COMPANY_NO, SENT_DATE, MESSAGE_NO))
                    .willReturn(null, saved(ResponseCode.SUCCESS, BALANCE_AFTER));

            // when
            TransferResultResponse result = transferService.transfer(request());

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
            assertThat(result.getBalanceAfter()).isEqualByComparingTo(BALANCE_AFTER);
        }

        @Test
        @DisplayName("다시 조회한 결과의 출금금액이 2,000,000으로 다르면 D001을 돌려준다")
        void 다시_조회한_내용이_다르면_D001이다() {
            // given
            given(transferMapper.findByCompanyNoAndSentDateAndMessageNo(COMPANY_NO, SENT_DATE, MESSAGE_NO))
                    .willReturn(null, saved(ResponseCode.SUCCESS, BALANCE_AFTER, new BigDecimal("2000000")));

            // when
            TransferResultResponse result = transferService.transfer(request());

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.DUPLICATE_TRANSACTION_NO);
        }
    }

    @Nested
    @DisplayName("처리 중 잔액 변경이 충돌할 때")
    class Conflict {

        @BeforeEach
        void setUp() {
            givenSaved(null);
        }

        @Test
        @DisplayName("두 번 충돌하고 세 번째에 0000이면 0000을 돌려주고 TransferProcessor를 3번 호출한다")
        void 충돌하면_다시_처리한다() {
            // given
            TransferResultResponse processed = success();
            given(transferProcessor.process(any()))
                    .willThrow(conflict(), conflict())
                    .willReturn(processed);

            // when
            TransferResultResponse result = transferService.transfer(request());

            // then
            assertThat(result).isSameAs(processed);
            then(transferProcessor).should(times(3)).process(any());
        }

        @Test
        @DisplayName("매번 충돌하면 최대 시도 횟수만큼 호출한 뒤 OptimisticLockingFailureException을 던진다")
        void 최대_횟수까지_충돌하면_예외를_던진다() {
            // given
            given(transferProcessor.process(any())).willThrow(conflict());

            // when, then
            assertThatThrownBy(() -> transferService.transfer(request()))
                    .isInstanceOf(OptimisticLockingFailureException.class);
            then(transferProcessor).should(times(TransferService.MAX_ATTEMPTS)).process(any());
        }

        private static OptimisticLockingFailureException conflict() {
            return new OptimisticLockingFailureException("version");
        }
    }

    static Stream<Arguments> differentRequests() {
        return Stream.of(
                Arguments.of("거래 종류", request(TransferType.COLLECTION, "100-000-000002", "999", "200-000-000001", AMOUNT, "대출금")),
                Arguments.of("출금계좌번호", request(TransferType.PAYMENT, "100-000-000001", "999", "200-000-000001", AMOUNT, "대출금")),
                Arguments.of("입금은행코드", request(TransferType.PAYMENT, "100-000-000002", "004", "200-000-000001", AMOUNT, "대출금")),
                Arguments.of("입금계좌번호", request(TransferType.PAYMENT, "100-000-000002", "999", "200-000-000002", AMOUNT, "대출금")),
                Arguments.of("출금금액", request(TransferType.PAYMENT, "100-000-000002", "999", "200-000-000001", new BigDecimal("2000000"), "대출금")),
                Arguments.of("적요", request(TransferType.PAYMENT, "100-000-000002", "999", "200-000-000001", AMOUNT, "대출금2")));
    }

    private void givenSaved(TransferDetailResponse saved) {
        given(transferMapper.findByCompanyNoAndSentDateAndMessageNo(COMPANY_NO, SENT_DATE, MESSAGE_NO)).willReturn(saved);
    }

    private static ExecuteTransferRequest request() {
        return request(TransferType.PAYMENT, "100-000-000002", "999", "200-000-000001", AMOUNT, "대출금");
    }

    private static ExecuteTransferRequest request(TransferType transferType, String withdrawalAccountNo,
                                                  String depositBankCode, String depositAccountNo,
                                                  BigDecimal amount, String memo) {
        return ExecuteTransferRequest.builder()
                .companyNo(COMPANY_NO)
                .sentDate(SENT_DATE)
                .messageNo(MESSAGE_NO)
                .sentTime("143005")
                .transferType(transferType)
                .withdrawalBankCode("999")
                .withdrawalAccountNo(withdrawalAccountNo)
                .depositBankCode(depositBankCode)
                .depositAccountNo(depositAccountNo)
                .amount(amount)
                .memo(memo)
                .build();
    }

    private static TransferDetailResponse saved(ResponseCode responseCode, BigDecimal balanceAfter) {
        return saved(responseCode, balanceAfter, AMOUNT);
    }

    // 처음 요청의 전송시간은 090000이다.
    private static TransferDetailResponse saved(ResponseCode responseCode, BigDecimal balanceAfter, BigDecimal amount) {
        return TransferDetailResponse.builder()
                .transferId(1L)
                .companyNo(COMPANY_NO)
                .sentDate(SENT_DATE)
                .messageNo(MESSAGE_NO)
                .sentTime("090000")
                .transferTypeId(TransferType.PAYMENT)
                .withdrawalAccountNo("100-000-000002")
                .depositBankCode("999")
                .depositAccountNo("200-000-000001")
                .amount(amount)
                .memo("대출금")
                .responseCode(responseCode.getCode())
                .balanceAfter(balanceAfter)
                .build();
    }

    private static TransferResultResponse success() {
        return TransferResultResponse.builder()
                .responseCode(ResponseCode.SUCCESS)
                .balanceAfter(BALANCE_AFTER)
                .build();
    }
}
