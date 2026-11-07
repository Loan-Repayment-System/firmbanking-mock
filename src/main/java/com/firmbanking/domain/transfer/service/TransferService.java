package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.global.code.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 이체. 같은 거래고유번호의 이체 결과가 있으면 처리하지 않고 저장된 결과로 응답한다 (프로그램 설계 2절).
 * 키 중복 예외는 TransferProcessor의 트랜잭션이 롤백된 뒤에 잡아야 하므로 트랜잭션 밖에 둔다.
 * 계좌 잠금도 트랜잭션이 커밋된 뒤에 해제해야 하므로 트랜잭션 밖에서 획득한다.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferMapper transferMapper;
    private final TransferProcessor transferProcessor;
    private final AccountLock accountLock;

    public TransferResultResponse transfer(ExecuteTransferRequest request) {
        TransferDetailResponse saved = findSaved(request);
        if (saved != null) {
            return resultOf(saved, request);
        }
        try {
            return accountLock.execute(request, () -> transferProcessor.process(request));
        } catch (DuplicateKeyException e) {
            // 같은 거래고유번호가 동시에 들어와 다른 쪽이 먼저 저장했다. 이쪽 출금은 롤백됐다.
            return resultOf(findSaved(request), request);
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
