package com.firmbanking.domain.transfer.service;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.account.mapper.AccountMapper;
import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.global.code.TransferType;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Sql("/sql/transfer/concurrency-setup.sql")
@Sql(scripts = "/sql/transfer/concurrency-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@DisplayName("TransferService 동시 처리")
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
    @DisplayName("지급 계좌 잔액 100,000,000에서 1만 원 지급 100건을 동시에 보내면 100건 모두 성공하지만 잔액이 99,000,000보다 크다")
    void 동시_지급하면_출금이_사라진다() throws Exception {
        // given
        int requestCount = 100;
        BigDecimal amount = new BigDecimal("10000");
        BigDecimal expectedBalance = new BigDecimal("99000000");

        // when
        List<TransferResultResponse> results = transferConcurrently(requestCount, amount);

        // then
        BigDecimal balance = findPaymentAccountBalance();
        logLostTransfers(expectedBalance, balance, amount);

        assertThat(countSuccessResponses(results)).isEqualTo(requestCount);
        assertThat(countSuccessTransfers()).isEqualTo(requestCount);
        assertThat(balance).isGreaterThan(expectedBalance);
    }

    private BigDecimal findPaymentAccountBalance() {
        return accountMapper.findByBankCodeAndAccountNo(BANK_CODE, PAYMENT_ACCOUNT_NO).getBalance();
    }

    private static void logLostTransfers(BigDecimal expectedBalance, BigDecimal balance, BigDecimal amount) {
        BigDecimal lostAmount = balance.subtract(expectedBalance);
        log.info("사라진 출금: {}원, {}건 (기대 잔액 {}, 실제 잔액 {})",
                lostAmount, lostAmount.divide(amount), expectedBalance, balance);
    }

    private static int countSuccessResponses(List<TransferResultResponse> results) {
        int count = 0;
        for (TransferResultResponse result : results) {
            if (result.getResponseCode() == ResponseCode.SUCCESS) {
                count++;
            }
        }
        return count;
    }

    private List<TransferResultResponse> transferConcurrently(int requestCount, BigDecimal amount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferResultResponse>> futures = new ArrayList<>();
        for (int i = 1; i <= requestCount; i++) {
            ExecuteTransferRequest request = paymentRequest(String.format("%06d", i), amount);
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
                .sentDate("20261104")
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

    private int countSuccessTransfers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE company_no = ? AND response_code = ?",
                Integer.class, COMPANY_NO, ResponseCode.SUCCESS.getCode());
    }
}
