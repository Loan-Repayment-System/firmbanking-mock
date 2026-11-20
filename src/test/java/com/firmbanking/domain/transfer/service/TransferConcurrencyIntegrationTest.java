package com.firmbanking.domain.transfer.service;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.global.code.AccountTransactionType;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.global.code.TransferRequestStatus;
import com.firmbanking.global.code.TransferType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Sql("/sql/transfer/concurrency-setup.sql")
@Sql(scripts = "/sql/transfer/concurrency-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@DisplayName("TransferService 동시 지급")
class TransferConcurrencyIntegrationTest {

    private static final String COMPANY_NO = "CONCUR00001";
    private static final String BANK_CODE = "999";
    private static final String PAYMENT_ACCOUNT_NO = "900-000-000002";
    private static final String CUSTOMER_ACCOUNT_NO = "900-000-000003";
    private static final int THREAD_COUNT = 32;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("지급 계좌 잔액 100,000,000에서 1만 원 지급 100건을 동시에 보내면 100건 모두 성공하고 출금 거래내역 합계 1,000,000만큼 지급 계좌 잔액이 99,000,000으로 줄어든다")
    void 동시_지급해도_출금이_사라지지_않는다() throws Exception {
        // given
        int requestCount = 100;
        BigDecimal amount = new BigDecimal("10000");

        // when
        List<TransferResultResponse> results = transferConcurrently(requestCount, amount);

        // then
        assertThat(countSuccessResponses(results)).isEqualTo(requestCount);
        assertThat(countSuccessTransfers()).isEqualTo(requestCount);
        assertThat(countPaymentWithdrawalTransactions()).isEqualTo(requestCount);
        assertThat(sumPaymentWithdrawalTransactions()).isEqualByComparingTo("1000000");
        assertThat(findBalance(PAYMENT_ACCOUNT_NO)).isEqualByComparingTo("99000000");
        assertThat(findBalance(CUSTOMER_ACCOUNT_NO)).isEqualByComparingTo("1000000");
    }

    // 잠금 순서가 이체 방향을 따르면 두 요청이 서로의 잠금을 기다려 데드락이 생긴다
    @Test
    @DisplayName("지급 100건과 입금 계좌를 지급 계좌로 적은 집금 100건을 동시에 보내면 200건 모두 예외 없이 응답하고 지급 100건은 성공, 집금 100건은 B004로 거절된다")
    void 반대_방향_이체를_동시에_보내도_데드락이_생기지_않는다() throws Exception {
        // given
        int requestCount = 100;
        BigDecimal amount = new BigDecimal("10000");
        List<ExecuteTransferRequest> requests = new ArrayList<>();
        for (int i = 1; i <= requestCount; i++) {
            requests.add(paymentRequest(String.format("%06d", i), amount));
            requests.add(reversedCollectionRequest(String.format("%06d", requestCount + i), amount));
        }

        // when
        List<TransferResultResponse> results = transferConcurrently(requests);

        // then
        assertThat(results).hasSize(requestCount * 2);
        assertThat(countResponses(results, ResponseCode.SUCCESS)).isEqualTo(requestCount);
        assertThat(countResponses(results, ResponseCode.NOT_COMPANY_ACCOUNT)).isEqualTo(requestCount);
        assertThat(countErrorRequests()).isZero();
        assertThat(findBalance(PAYMENT_ACCOUNT_NO)).isEqualByComparingTo("99000000");
        assertThat(findBalance(CUSTOMER_ACCOUNT_NO)).isEqualByComparingTo("1000000");
    }

    private BigDecimal findBalance(String accountNo) {
        return accountMapper.findByBankCodeAndAccountNo(BANK_CODE, accountNo).getBalance();
    }

    private static int countSuccessResponses(List<TransferResultResponse> results) {
        return countResponses(results, ResponseCode.SUCCESS);
    }

    private static int countResponses(List<TransferResultResponse> results, ResponseCode responseCode) {
        int count = 0;
        for (TransferResultResponse result : results) {
            if (result.getResponseCode() == responseCode) {
                count++;
            }
        }
        return count;
    }

    private List<TransferResultResponse> transferConcurrently(int requestCount, BigDecimal amount) throws Exception {
        List<ExecuteTransferRequest> requests = new ArrayList<>();
        for (int i = 1; i <= requestCount; i++) {
            requests.add(paymentRequest(String.format("%06d", i), amount));
        }
        return transferConcurrently(requests);
    }

    private List<TransferResultResponse> transferConcurrently(List<ExecuteTransferRequest> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferResultResponse>> futures = new ArrayList<>();
        for (ExecuteTransferRequest request : requests) {
            futures.add(executor.submit(() -> {
                start.await();
                return transferService.transfer(request);
            }));
        }
        start.countDown();
        List<TransferResultResponse> results = new ArrayList<>();
        for (Future<TransferResultResponse> future : futures) {
            results.add(future.get());
        }
        executor.shutdown();
        return results;
    }

    private static ExecuteTransferRequest paymentRequest(String messageNo, BigDecimal amount) {
        return ExecuteTransferRequest.builder()
                .companyNo(COMPANY_NO)
                .sentDate("20261119")
                .messageNo(messageNo)
                .sentTime("120000")
                .transferType(TransferType.PAYMENT)
                .withdrawalBankCode(BANK_CODE)
                .withdrawalAccountNo(PAYMENT_ACCOUNT_NO)
                .depositBankCode(BANK_CODE)
                .depositAccountNo(CUSTOMER_ACCOUNT_NO)
                .amount(amount)
                .memo("동시성")
                .build();
    }

    // 입금 계좌가 수납 계좌가 아니라 B004로 거절될 집금. 거절 전에 고객 계좌 → 지급 계좌 순서로 잠근다.
    private static ExecuteTransferRequest reversedCollectionRequest(String messageNo, BigDecimal amount) {
        return ExecuteTransferRequest.builder()
                .companyNo(COMPANY_NO)
                .sentDate("20261119")
                .messageNo(messageNo)
                .sentTime("120000")
                .transferType(TransferType.COLLECTION)
                .withdrawalBankCode(BANK_CODE)
                .withdrawalAccountNo(CUSTOMER_ACCOUNT_NO)
                .depositBankCode(BANK_CODE)
                .depositAccountNo(PAYMENT_ACCOUNT_NO)
                .amount(amount)
                .memo("동시성")
                .build();
    }

    private int countErrorRequests() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_request WHERE company_no = ? AND transfer_request_status_id = ?",
                Integer.class, COMPANY_NO, TransferRequestStatus.ERROR.getId());
    }

    private int countSuccessTransfers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE company_no = ? AND response_code = ?",
                Integer.class, COMPANY_NO, ResponseCode.SUCCESS.getCode());
    }

    private int countPaymentWithdrawalTransactions() {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM account_transaction transaction_row
                            JOIN account ON account.account_id = transaction_row.account_id
                        WHERE account.bank_code = ?
                          AND account.account_no = ?
                          AND transaction_row.account_transaction_type_id = ?
                        """,
                Integer.class, BANK_CODE, PAYMENT_ACCOUNT_NO, AccountTransactionType.WITHDRAWAL.getId());
    }

    private BigDecimal sumPaymentWithdrawalTransactions() {
        return jdbcTemplate.queryForObject("""
                        SELECT SUM(transaction_row.amount)
                        FROM account_transaction transaction_row
                            JOIN account ON account.account_id = transaction_row.account_id
                        WHERE account.bank_code = ?
                          AND account.account_no = ?
                          AND transaction_row.account_transaction_type_id = ?
                        """,
                BigDecimal.class, BANK_CODE, PAYMENT_ACCOUNT_NO, AccountTransactionType.WITHDRAWAL.getId());
    }
}
