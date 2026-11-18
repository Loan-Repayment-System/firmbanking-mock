package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.transfer.dto.request.InquireTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.domain.transfer.dto.response.TransferInquiryResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.domain.transfer.mapper.TransferRequestMapper;
import com.firmbanking.global.code.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferInquiryService {

    private final TransferMapper transferMapper;
    private final TransferRequestMapper transferRequestMapper;

    /**
     * 이체 결과가 없을 때 60초 이내 처리 중이면 D003, 그 밖에는 D002다.
     * 처리 중인 채 60초가 지난 요청은 D002로 응답해 재전송으로 복구되게 한다.
     */
    public TransferInquiryResponse inquire(InquireTransferRequest request) {
        TransferDetailResponse transfer = transferMapper.findByCompanyNoAndSentDateAndMessageNo(
                request.getCompanyNo(), request.getSentDate(), request.getMessageNo());
        if (transfer == null) {
            ResponseCode responseCode = transferRequestMapper.existsProcessingWithin60Seconds(request)
                    ? ResponseCode.TRANSFER_IN_PROGRESS : ResponseCode.ORIGINAL_TRANSFER_NOT_FOUND;
            return TransferInquiryResponse.builder().responseCode(responseCode).build();
        }
        return TransferInquiryResponse.builder()
                .responseCode(ResponseCode.SUCCESS)
                .transfer(transfer)
                .build();
    }
}
