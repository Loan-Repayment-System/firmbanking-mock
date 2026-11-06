package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.account.dto.response.AccountDetailResponse;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.domain.company.dto.response.CompanyDetailResponse;
import com.firmbanking.domain.company.mapper.CompanyMapper;
import com.firmbanking.domain.consent.dto.response.WithdrawalConsentDetailResponse;
import com.firmbanking.domain.consent.mapper.WithdrawalConsentMapper;
import com.firmbanking.domain.transfer.dto.request.CreateTransferRequest;
import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.global.code.ConsentStatus;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.global.code.TransferType;
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

@DisplayName("TransferProcessor")
class TransferProcessorTest {

    private static final String COMPANY_NO = "CAPITAL00001";
    private static final String BANK_CODE = "999";
    private static final String COLLECTION_ACCOUNT_NO = "100-000-000001";
    private static final String PAYMENT_ACCOUNT_NO = "100-000-000002";
    private static final String CUSTOMER_ACCOUNT_NO = "200-000-000001";
    private static final Long COLLECTION_ACCOUNT_ID = 1L;
    private static final Long PAYMENT_ACCOUNT_ID = 2L;
    private static final Long CUSTOMER_ACCOUNT_ID = 10L;

    private AccountMapper accountMapper;
    private CompanyMapper companyMapper;
    private TransferMapper transferMapper;
    private WithdrawalConsentMapper withdrawalConsentMapper;
    private TransferProcessor transferProcessor;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        companyMapper = mock(CompanyMapper.class);
        transferMapper = mock(TransferMapper.class);
        withdrawalConsentMapper = mock(WithdrawalConsentMapper.class);
        transferProcessor = new TransferProcessor(accountMapper, companyMapper, transferMapper, withdrawalConsentMapper);
        given(companyMapper.findByCompanyNo(COMPANY_NO)).willReturn(CompanyDetailResponse.builder()
                .companyNo(COMPANY_NO)
                .companyName("캐피탈")
                .collectionAccountId(COLLECTION_ACCOUNT_ID)
                .paymentAccountId(PAYMENT_ACCOUNT_ID)
                .build());
    }

    @Nested
    @DisplayName("계좌가 없을 때")
    class AccountNotFound {

        // 전문 명세 5절 B001: 공통부 은행에 출금계좌가 없음
        @Test
        @DisplayName("출금계좌 999 / 100-999-999999가 없으면 B001을 돌려주고 잔액을 바꾸지 않으며 B001 / 잔액 null로 저장한다")
        void 출금계좌가_없으면_B001이다() {
            // given
            givenAccount(CUSTOMER_ACCOUNT_ID, CUSTOMER_ACCOUNT_NO, "10000000");

            // when
            TransferResultResponse result = transferProcessor.process(request("100-999-999999", CUSTOMER_ACCOUNT_NO, "1000000"));

            // then
            assertRejected(result, ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);
        }

        // 프로그램 설계 1절: 출금계좌를 입금계좌보다 먼저 확인한다
        @Test
        @DisplayName("출금계좌와 입금계좌가 둘 다 없으면 B001을 돌려준다")
        void 둘_다_없으면_B001이다() {
            // when
            TransferResultResponse result = transferProcessor.process(request("100-999-999999", "200-999-999999", "1000000"));

            // then
            assertRejected(result, ResponseCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);
        }

        // 전문 명세 5절 B002: 입금은행코드 + 입금계좌번호의 계좌가 없음
        @Test
        @DisplayName("입금계좌 999 / 200-999-999999가 없으면 B002를 돌려주고 잔액을 바꾸지 않으며 B002 / 잔액 null로 저장한다")
        void 입금계좌가_없으면_B002다() {
            // given
            givenAccount(PAYMENT_ACCOUNT_ID, PAYMENT_ACCOUNT_NO, "10000000000");

            // when
            TransferResultResponse result = transferProcessor.process(request(PAYMENT_ACCOUNT_NO, "200-999-999999", "1000000"));

            // then
            assertRejected(result, ResponseCode.DEPOSIT_ACCOUNT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("출금계좌가 지급 계좌가 아닐 때")
    class NotPaymentAccount {

        @BeforeEach
        void setUp() {
            givenAccount(CUSTOMER_ACCOUNT_ID, CUSTOMER_ACCOUNT_NO, "10000000");
        }

        // 전문 명세 5절 B004: 지급 출금계좌가 업체 지급 계좌가 아님
        @Test
        @DisplayName("출금계좌가 수납 계좌 100-000-000001이면 B004를 돌려주고 잔액을 바꾸지 않으며 B004 / 잔액 null로 저장한다")
        void 수납_계좌면_B004다() {
            // given
            givenAccount(COLLECTION_ACCOUNT_ID, COLLECTION_ACCOUNT_NO, "100000000");

            // when
            TransferResultResponse result = transferProcessor.process(request(COLLECTION_ACCOUNT_NO, CUSTOMER_ACCOUNT_NO, "1000000"));

            // then
            assertRejected(result, ResponseCode.NOT_COMPANY_ACCOUNT);
        }

        // 프로그램 설계 1절: 거래별 검사(B004)가 잔액 확인(B003)보다 먼저다
        @Test
        @DisplayName("출금계좌가 잔액 0인 수납 계좌면 잔액이 모자라도 B004를 돌려준다")
        void 잔액도_모자라면_B004다() {
            // given
            givenAccount(COLLECTION_ACCOUNT_ID, COLLECTION_ACCOUNT_NO, "0");

            // when
            TransferResultResponse result = transferProcessor.process(request(COLLECTION_ACCOUNT_NO, CUSTOMER_ACCOUNT_NO, "1000000"));

            // then
            assertRejected(result, ResponseCode.NOT_COMPANY_ACCOUNT);
        }
    }

    @Nested
    @DisplayName("출금계좌가 지급 계좌이고 입금계좌가 고객 계좌 1,000만 원일 때")
    class Payment {

        @BeforeEach
        void setUp() {
            givenAccount(CUSTOMER_ACCOUNT_ID, CUSTOMER_ACCOUNT_NO, "10000000");
        }

        // 전문 명세 5절 B003: 출금계좌 잔액이 출금금액보다 적음
        @Test
        @DisplayName("지급 계좌 잔액 1,000,000에서 2,000,000을 지급하면 B003을 돌려주고 잔액을 바꾸지 않으며 B003 / 잔액 null로 저장한다")
        void 잔액이_모자라면_B003이다() {
            // given
            givenAccount(PAYMENT_ACCOUNT_ID, PAYMENT_ACCOUNT_NO, "1000000");

            // when
            TransferResultResponse result = transferProcessor.process(request(PAYMENT_ACCOUNT_NO, CUSTOMER_ACCOUNT_NO, "2000000"));

            // then
            assertRejected(result, ResponseCode.INSUFFICIENT_BALANCE);
        }

        @Test
        @DisplayName("지급 계좌 잔액 1,000,000에서 1,000,000을 지급하면 0000 / 잔액 0이다")
        void 잔액과_같으면_0000이다() {
            // given
            givenAccount(PAYMENT_ACCOUNT_ID, PAYMENT_ACCOUNT_NO, "1000000");

            // when
            TransferResultResponse result = transferProcessor.process(request(PAYMENT_ACCOUNT_NO, CUSTOMER_ACCOUNT_NO, "1000000"));

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
            assertThat(result.getBalanceAfter()).isEqualByComparingTo(BigDecimal.ZERO);
            then(accountMapper).should().updateBalance(PAYMENT_ACCOUNT_ID, new BigDecimal("0"));
        }

        @Test
        @DisplayName("지급 계좌 잔액 10,000,000,000에서 1,000,000을 지급하면 지급 계좌 9,999,000,000, 고객 계좌 11,000,000으로 바꾸고 0000 / 9,999,000,000으로 저장한다")
        void 출금하고_입금한다() {
            // given
            givenAccount(PAYMENT_ACCOUNT_ID, PAYMENT_ACCOUNT_NO, "10000000000");

            // when
            TransferResultResponse result = transferProcessor.process(request(PAYMENT_ACCOUNT_NO, CUSTOMER_ACCOUNT_NO, "1000000"));

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
            assertThat(result.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("9999000000"));
            then(accountMapper).should().updateBalance(PAYMENT_ACCOUNT_ID, new BigDecimal("9999000000"));
            then(accountMapper).should().updateBalance(CUSTOMER_ACCOUNT_ID, new BigDecimal("11000000"));
            CreateTransferRequest saved = captureSaved();
            assertThat(saved.getCompanyNo()).isEqualTo(COMPANY_NO);
            assertThat(saved.getSentDate()).isEqualTo("20261029");
            assertThat(saved.getMessageNo()).isEqualTo("000123");
            assertThat(saved.getSentTime()).isEqualTo("143005");
            assertThat(saved.getTransferType()).isEqualTo(TransferType.PAYMENT);
            assertThat(saved.getWithdrawalAccountNo()).isEqualTo(PAYMENT_ACCOUNT_NO);
            assertThat(saved.getDepositBankCode()).isEqualTo(BANK_CODE);
            assertThat(saved.getDepositAccountNo()).isEqualTo(CUSTOMER_ACCOUNT_NO);
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
            assertThat(saved.getMemo()).isEqualTo("대출금");
            assertThat(saved.getResponseCode()).isEqualTo("0000");
            assertThat(saved.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("9999000000"));
        }
    }

    @Test
    @DisplayName("지급은 출금 동의를 조회하지 않는다")
    void 지급은_출금_동의를_보지_않는다() {
        // given
        givenAccount(PAYMENT_ACCOUNT_ID, PAYMENT_ACCOUNT_NO, "10000000000");
        givenAccount(CUSTOMER_ACCOUNT_ID, CUSTOMER_ACCOUNT_NO, "10000000");

        // when
        transferProcessor.process(request(PAYMENT_ACCOUNT_NO, CUSTOMER_ACCOUNT_NO, "1000000"));

        // then
        then(withdrawalConsentMapper).shouldHaveNoInteractions();
    }

    // 따로 쓰면 입금이 조회한 잔액으로 출금을 덮어써 돈이 생긴다
    @Test
    @DisplayName("입금계좌가 지급 계좌 자신이면 잔액을 바꾸지 않고 0000 / 10,000,000,000으로 저장한다")
    void 같은_계좌면_잔액을_바꾸지_않는다() {
        // given
        givenAccount(PAYMENT_ACCOUNT_ID, PAYMENT_ACCOUNT_NO, "10000000000");

        // when
        TransferResultResponse result = transferProcessor.process(request(PAYMENT_ACCOUNT_NO, PAYMENT_ACCOUNT_NO, "1000000"));

        // then
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
        assertThat(result.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("10000000000"));
        then(accountMapper).should(never()).updateBalance(any(), any());
        CreateTransferRequest saved = captureSaved();
        assertThat(saved.getResponseCode()).isEqualTo("0000");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("10000000000"));
    }

    @Nested
    @DisplayName("집금일 때")
    class Collection {

        @Nested
        @DisplayName("입금계좌가 수납 계좌가 아닐 때")
        class NotCollectionAccount {

            @BeforeEach
            void setUp() {
                givenAccount(CUSTOMER_ACCOUNT_ID, CUSTOMER_ACCOUNT_NO, "10000000");
                givenAccount(PAYMENT_ACCOUNT_ID, PAYMENT_ACCOUNT_NO, "10000000000");
            }

            // 전문 명세 5절 B004: 집금 입금계좌가 업체 수납 계좌가 아님
            @Test
            @DisplayName("입금계좌가 지급 계좌 100-000-000002면 B004를 돌려주고 잔액을 바꾸지 않으며 B004 / 잔액 null로 저장한다")
            void 지급_계좌면_B004다() {
                // given
                givenConsent(CUSTOMER_ACCOUNT_ID, ConsentStatus.REGISTERED);

                // when
                TransferResultResponse result = transferProcessor.process(
                        collection(CUSTOMER_ACCOUNT_NO, PAYMENT_ACCOUNT_NO, "1000000"));

                // then
                assertRejected(result, ResponseCode.NOT_COMPANY_ACCOUNT);
            }

            // 프로그램 설계 1절: 수납 계좌 확인(B004)이 출금 동의 확인(C001)보다 먼저다
            @Test
            @DisplayName("출금 동의도 없으면 B004를 돌려준다")
            void 출금_동의도_없으면_B004다() {
                // when
                TransferResultResponse result = transferProcessor.process(
                        collection(CUSTOMER_ACCOUNT_NO, PAYMENT_ACCOUNT_NO, "1000000"));

                // then
                assertRejected(result, ResponseCode.NOT_COMPANY_ACCOUNT);
            }
        }

        @Nested
        @DisplayName("고객 계좌 1,000만 원에서 수납 계좌 0원으로 집금할 때")
        class ToCollectionAccount {

            @BeforeEach
            void setUp() {
                givenAccount(CUSTOMER_ACCOUNT_ID, CUSTOMER_ACCOUNT_NO, "10000000");
                givenAccount(COLLECTION_ACCOUNT_ID, COLLECTION_ACCOUNT_NO, "0");
            }

            // 전문 명세 5절 C001: 집금 출금계좌가 등록되지 않았거나 해지됨
            @Test
            @DisplayName("출금 동의 행이 없으면 C001을 돌려주고 잔액을 바꾸지 않으며 C001 / 잔액 null로 저장한다")
            void 동의가_없으면_C001이다() {
                // when
                TransferResultResponse result = transferProcessor.process(
                        collection(CUSTOMER_ACCOUNT_NO, COLLECTION_ACCOUNT_NO, "1000000"));

                // then
                assertRejected(result, ResponseCode.CONSENT_NOT_REGISTERED);
            }

            @Test
            @DisplayName("출금 동의가 해지 상태면 C001을 돌려준다")
            void 해지_상태면_C001이다() {
                // given
                givenConsent(CUSTOMER_ACCOUNT_ID, ConsentStatus.CANCELLED);

                // when
                TransferResultResponse result = transferProcessor.process(
                        collection(CUSTOMER_ACCOUNT_NO, COLLECTION_ACCOUNT_NO, "1000000"));

                // then
                assertRejected(result, ResponseCode.CONSENT_NOT_REGISTERED);
            }

            // 프로그램 설계 1절: 거래별 검사(C001)가 잔액 확인(B003)보다 먼저다
            @Test
            @DisplayName("출금 동의가 없으면 20,000,000을 집금해 잔액이 모자라도 C001을 돌려준다")
            void 잔액도_모자라면_C001이다() {
                // when
                TransferResultResponse result = transferProcessor.process(
                        collection(CUSTOMER_ACCOUNT_NO, COLLECTION_ACCOUNT_NO, "20000000"));

                // then
                assertRejected(result, ResponseCode.CONSENT_NOT_REGISTERED);
            }

            @Nested
            @DisplayName("출금 동의가 등록 상태일 때")
            class Registered {

                @BeforeEach
                void setUp() {
                    givenConsent(CUSTOMER_ACCOUNT_ID, ConsentStatus.REGISTERED);
                }

                // 전문 명세 5절 B003: 출금계좌 잔액이 출금금액보다 적음
                @Test
                @DisplayName("20,000,000을 집금하면 B003을 돌려주고 잔액을 바꾸지 않으며 B003 / 잔액 null로 저장한다")
                void 잔액이_모자라면_B003이다() {
                    // when
                    TransferResultResponse result = transferProcessor.process(
                            collection(CUSTOMER_ACCOUNT_NO, COLLECTION_ACCOUNT_NO, "20000000"));

                    // then
                    assertRejected(result, ResponseCode.INSUFFICIENT_BALANCE);
                }

                // 전문 명세 4.2절: 입금후잔액은 입금 후 캐피탈 수납 계좌 잔액
                @Test
                @DisplayName("1,000,000을 집금하면 고객 계좌 9,000,000, 수납 계좌 1,000,000으로 바꾸고 집금 / 0000 / 1,000,000으로 저장한다")
                void 출금하고_입금한다() {
                    // when
                    TransferResultResponse result = transferProcessor.process(
                            collection(CUSTOMER_ACCOUNT_NO, COLLECTION_ACCOUNT_NO, "1000000"));

                    // then
                    assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
                    assertThat(result.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("1000000"));
                    then(accountMapper).should().updateBalance(CUSTOMER_ACCOUNT_ID, new BigDecimal("9000000"));
                    then(accountMapper).should().updateBalance(COLLECTION_ACCOUNT_ID, new BigDecimal("1000000"));
                    CreateTransferRequest saved = captureSaved();
                    assertThat(saved.getTransferType()).isEqualTo(TransferType.COLLECTION);
                    assertThat(saved.getWithdrawalAccountNo()).isEqualTo(CUSTOMER_ACCOUNT_NO);
                    assertThat(saved.getDepositAccountNo()).isEqualTo(COLLECTION_ACCOUNT_NO);
                    assertThat(saved.getResponseCode()).isEqualTo("0000");
                    assertThat(saved.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("1000000"));
                }
            }
        }

        // 따로 쓰면 입금이 조회한 잔액으로 출금을 덮어써 돈이 생긴다
        @Test
        @DisplayName("출금계좌가 출금 동의한 수납 계좌 자신(잔액 5,000,000)이면 잔액을 바꾸지 않고 0000 / 5,000,000으로 저장한다")
        void 같은_계좌면_잔액을_바꾸지_않는다() {
            // given
            givenAccount(COLLECTION_ACCOUNT_ID, COLLECTION_ACCOUNT_NO, "5000000");
            givenConsent(COLLECTION_ACCOUNT_ID, ConsentStatus.REGISTERED);

            // when
            TransferResultResponse result = transferProcessor.process(
                    collection(COLLECTION_ACCOUNT_NO, COLLECTION_ACCOUNT_NO, "1000000"));

            // then
            assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
            assertThat(result.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("5000000"));
            then(accountMapper).should(never()).updateBalance(any(), any());
            assertThat(captureSaved().getBalanceAfter()).isEqualByComparingTo(new BigDecimal("5000000"));
        }
    }

    private void givenAccount(Long accountId, String accountNo, String balance) {
        given(accountMapper.findByBankCodeAndAccountNoForUpdate(BANK_CODE, accountNo)).willReturn(AccountDetailResponse.builder()
                .accountId(accountId)
                .bankCode(BANK_CODE)
                .accountNo(accountNo)
                .holderName("예금주")
                .balance(new BigDecimal(balance))
                .build());
    }

    private void givenConsent(Long accountId, ConsentStatus status) {
        given(withdrawalConsentMapper.findByCompanyNoAndAccountId(COMPANY_NO, accountId)).willReturn(
                WithdrawalConsentDetailResponse.builder()
                        .consentId(20L)
                        .companyNo(COMPANY_NO)
                        .accountId(accountId)
                        .consentStatusId(status)
                        .payerNo("L0001")
                        .appliedDate("20261103")
                        .build());
    }

    private void assertRejected(TransferResultResponse result, ResponseCode responseCode) {
        assertThat(result.getResponseCode()).isEqualTo(responseCode);
        assertThat(result.getBalanceAfter()).isNull();
        then(accountMapper).should(never()).updateBalance(any(), any());
        CreateTransferRequest saved = captureSaved();
        assertThat(saved.getResponseCode()).isEqualTo(responseCode.getCode());
        assertThat(saved.getBalanceAfter()).isNull();
    }

    private CreateTransferRequest captureSaved() {
        ArgumentCaptor<CreateTransferRequest> captor = ArgumentCaptor.forClass(CreateTransferRequest.class);
        then(transferMapper).should().insert(captor.capture());
        return captor.getValue();
    }

    private static ExecuteTransferRequest request(String withdrawalAccountNo, String depositAccountNo, String amount) {
        return request(TransferType.PAYMENT, withdrawalAccountNo, depositAccountNo, amount);
    }

    private static ExecuteTransferRequest collection(String withdrawalAccountNo, String depositAccountNo, String amount) {
        return request(TransferType.COLLECTION, withdrawalAccountNo, depositAccountNo, amount);
    }

    private static ExecuteTransferRequest request(TransferType transferType, String withdrawalAccountNo,
                                                  String depositAccountNo, String amount) {
        return ExecuteTransferRequest.builder()
                .companyNo(COMPANY_NO)
                .sentDate("20261029")
                .messageNo("000123")
                .sentTime("143005")
                .transferType(transferType)
                .withdrawalBankCode(BANK_CODE)
                .withdrawalAccountNo(withdrawalAccountNo)
                .depositBankCode(BANK_CODE)
                .depositAccountNo(depositAccountNo)
                .amount(new BigDecimal(amount))
                .memo("대출금")
                .build();
    }
}
