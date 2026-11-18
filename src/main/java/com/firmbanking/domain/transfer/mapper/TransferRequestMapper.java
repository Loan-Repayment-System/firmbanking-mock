package com.firmbanking.domain.transfer.mapper;

import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.request.InquireTransferRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 상태를 바꾸는 메서드는 바뀐 행 수를 돌려준다.
 */
@Mapper
public interface TransferRequestMapper {

    /**
     * 같은 거래고유번호가 이미 있으면 DuplicateKeyException이다.
     */
    void insertProcessing(ExecuteTransferRequest request);

    /**
     * 처리 중인 채 60초가 지났으면 오류로 바꾼다.
     */
    int updateStaleProcessingToError(ExecuteTransferRequest request);

    int updateErrorToProcessing(ExecuteTransferRequest request);

    int updateProcessingToError(ExecuteTransferRequest request);

    /**
     * 상태를 보지 않는다. 오류로 바뀐 뒤 늦게 끝난 원거래도 완료로 확정한다.
     */
    void updateToCompleted(ExecuteTransferRequest request);

    boolean existsRecentProcessing(InquireTransferRequest request);
}
