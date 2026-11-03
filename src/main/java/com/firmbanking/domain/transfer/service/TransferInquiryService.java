package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.transfer.dto.request.InquireTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.domain.transfer.dto.response.TransferInquiryResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.global.code.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferInquiryService {

    private final TransferMapper transferMapper;

    /**
     * 원거래가 없으면 D002다. 원거래가 아직 처리 중(커밋 전)이어도 D002다.
     */
    public TransferInquiryResponse inquire(InquireTransferRequest request) {
        TransferDetailResponse transfer = transferMapper.findByCompanyNoAndSentDateAndMessageNo(
                request.getCompanyNo(), request.getSentDate(), request.getMessageNo());
        if (transfer == null) {
            return TransferInquiryResponse.builder().responseCode(ResponseCode.ORIGINAL_TRANSFER_NOT_FOUND).build();
        }
        return TransferInquiryResponse.builder()
                .responseCode(ResponseCode.SUCCESS)
                .transfer(transfer)
                .build();
    }
}
