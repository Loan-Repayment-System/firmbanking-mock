package com.firmbanking.domain.transfer.service;

import com.firmbanking.TestcontainersConfig;
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
@Sql("/sql/transfer/collection-concurrency-setup.sql")
@Sql(scripts = "/sql/transfer/collection-concurrency-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@DisplayName("TransferService 동시 집금")
class CollectionConcurrencyIntegrationTest {

    private static final String COMPANY_NO = "CONCUR00001";
    private static final String BANK_CODE = "999";
    private static final String COLLECTION_ACCOUNT_NO = "900-000-000001";
    private static final String CUSTOMER_ACCOUNT_NO = "900-000-000003";
    private static final int THREAD_COUNT = 32;

    @Autowired
    private TransferService transferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("고객 계좌 잔액 100,000에서 1만 원 집금 100건을 동시에 보내면 10건보다 많이 성공한다")
    void 동시_집금하면_잔액보다_많이_출금된다() throws Exception {
        // given
        int requestCount = 100;
        BigDecimal amount = new BigDecimal("10000");
        int expectedSuccessCount = 10;

        // when
        List<TransferResultResponse> results = collectConcurrently(requestCount, amount);

        // then
        int successCount = countSuccessResponses(results);
        logOverdrawnTransfers(expectedSuccessCount, successCount, amount);

        assertThat(successCount).isGreaterThan(expectedSuccessCount);
        assertThat(countSuccessTransfers()).isGreaterThan(expectedSuccessCount);
    }

    private static void logOverdrawnTransfers(int expectedSuccessCount, int successCount, BigDecimal amount) {
        int overdrawnCount = successCount - expectedSuccessCount;
        log.info("잔액보다 많이 성공한 집금: {}원, {}건 (기대 성공 {}건, 실제 성공 {}건)",
                amount.multiply(BigDecimal.valueOf(overdrawnCount)), overdrawnCount, expectedSuccessCount, successCount);
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

    private List<TransferResultResponse> collectConcurrently(int requestCount, BigDecimal amount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferResultResponse>> futures = new ArrayList<>();
        for (int i = 1; i <= requestCount; i++) {
            ExecuteTransferRequest request = collectionRequest(String.format("%06d", i), amount);
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

    private static ExecuteTransferRequest collectionRequest(String messageNo, BigDecimal amount) {
        return ExecuteTransferRequest.builder()
                .companyNo(COMPANY_NO)
                .sentDate("20261104")
                .messageNo(messageNo)
                .sentTime("120000")
                .transferType(TransferType.COLLECTION)
                .withdrawalBankCode(BANK_CODE)
                .withdrawalAccountNo(CUSTOMER_ACCOUNT_NO)
                .depositBankCode(BANK_CODE)
                .depositAccountNo(COLLECTION_ACCOUNT_NO)
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
