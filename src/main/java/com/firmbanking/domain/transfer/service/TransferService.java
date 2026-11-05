package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.global.code.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 이체. 같은 거래고유번호의 이체 결과가 있으면 처리하지 않고 저장된 결과로 응답한다 (프로그램 설계 2절).
 * 키 중복 예외는 TransferProcessor의 트랜잭션이 롤백된 뒤에 잡아야 하므로 트랜잭션 밖에 둔다.
 * 잔액 변경이 충돌하면 새 트랜잭션에서 다시 처리한다. 같은 트랜잭션에서 다시 조회하면 처음 읽은 스냅샷이 그대로 나온다.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    static final int MAX_ATTEMPTS = 50;
    private static final int MIN_RETRY_WAIT_MILLIS = 10;
    private static final int MAX_RETRY_WAIT_MILLIS = 50;

    private final TransferMapper transferMapper;
    private final TransferProcessor transferProcessor;

    public TransferResultResponse transfer(ExecuteTransferRequest request) {
        TransferDetailResponse saved = findSaved(request);
        if (saved != null) {
            return resultOf(saved, request);
        }
        try {
            return processWithRetry(request);
        } catch (DuplicateKeyException e) {
            // 같은 거래고유번호가 동시에 들어와 다른 쪽이 먼저 저장했다. 이쪽 출금은 롤백됐다.
            return resultOf(findSaved(request), request);
        }
    }

    /**
     * 충돌하면 랜덤 시간을 기다린 뒤 다시 처리한다. MAX_ATTEMPTS번 모두 충돌하면 예외를 그대로 던진다.
     */
    private TransferResultResponse processWithRetry(ExecuteTransferRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transferProcessor.process(request);
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                waitBeforeRetry(e);
            }
        }
    }

    // 충돌한 요청들이 같은 시점에 다시 조회해 또 충돌하지 않도록 기다리는 시간을 다르게 준다.
    private static void waitBeforeRetry(OptimisticLockingFailureException conflict) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(MIN_RETRY_WAIT_MILLIS, MAX_RETRY_WAIT_MILLIS + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw conflict;
        }
    }

    private TransferDetailResponse findSaved(ExecuteTransferRequest request) {
        return transferMapper.findByCompanyNoAndSentDateAndMessageNo(
                request.getCompanyNo(), request.getSentDate(), request.getMessageNo());
    }

    /**
     * 전송시간 말고 내용이 하나라도 다르면 D001, 같으면 저장된 결과다.
     */
    private static TransferResultResponse resultOf(TransferDetailResponse saved, ExecuteTransferRequest request) {
        if (!hasSameContent(saved, request)) {
            return TransferResultResponse.builder().responseCode(ResponseCode.DUPLICATE_TRANSACTION_NO).build();
        }
        return TransferResultResponse.builder()
                .responseCode(ResponseCode.fromCode(saved.getResponseCode()))
                .balanceAfter(saved.getBalanceAfter())
                .build();
    }

    private static boolean hasSameContent(TransferDetailResponse saved, ExecuteTransferRequest request) {
        return saved.getTransferTypeId() == request.getTransferType()
                && Objects.equals(saved.getWithdrawalAccountNo(), request.getWithdrawalAccountNo())
                && Objects.equals(saved.getDepositBankCode(), request.getDepositBankCode())
                && Objects.equals(saved.getDepositAccountNo(), request.getDepositAccountNo())
                && saved.getAmount().compareTo(request.getAmount()) == 0
                && Objects.equals(saved.getMemo(), request.getMemo());
    }
}
