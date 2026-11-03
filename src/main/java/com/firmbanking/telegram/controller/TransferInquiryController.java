package com.firmbanking.telegram.controller;

import com.firmbanking.domain.company.service.CompanyService;
import com.firmbanking.domain.transfer.dto.request.InquireTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.domain.transfer.dto.response.TransferInquiryResponse;
import com.firmbanking.domain.transfer.service.TransferInquiryService;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.TelegramController;
import com.firmbanking.telegram.codec.RequestTelegram;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.controller.field.TransferInquiryField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * 이체결과조회 7000/100 (전문 명세 4.3절).
 * 원거래가 있으면 공통부 응답코드는 0000이고, 원거래의 응답코드는 개별부 처리결과에 담는다.
 */
@Component
@RequiredArgsConstructor
public class TransferInquiryController implements TelegramController {

    private static final DateTimeFormatter TRANSFER_TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private final CompanyService companyService;
    private final TransferInquiryService transferInquiryService;

    @Override
    public String telegramTypeCode() {
        return "7000";
    }

    @Override
    public String businessTypeCode() {
        return "100";
    }

    @Override
    public ResponseCode process(RequestTelegram request, ResponseTelegram response) {
        String companyNo = request.getCommonPart().getCompanyNo();
        if (!companyService.isRegistered(companyNo)) {
            return ResponseCode.UNREGISTERED_COMPANY;
        }
        TransferInquiryResponse result = transferInquiryService.inquire(read(request, companyNo));
        if (result.getResponseCode() == ResponseCode.SUCCESS) {
            write(response, result.getTransfer());
        }
        return result.getResponseCode();
    }

    private static InquireTransferRequest read(RequestTelegram request, String companyNo) {
        return InquireTransferRequest.builder()
                .companyNo(companyNo)
                .sentDate(request.readDate(TransferInquiryField.ORIGINAL_SENT_DATE).format(DateTimeFormatter.BASIC_ISO_DATE))
                .messageNo(request.readN(TransferInquiryField.ORIGINAL_MESSAGE_NO))
                .build();
    }

    // 정상처리금액은 원거래가 정상이면 의뢰금액, 처리불능금액은 거절이면 의뢰금액이다.
    private static void write(ResponseTelegram response, TransferDetailResponse transfer) {
        boolean success = ResponseCode.SUCCESS.getCode().equals(transfer.getResponseCode());
        response.writeAN(TransferInquiryField.WITHDRAWAL_ACCOUNT_NO, transfer.getWithdrawalAccountNo());
        response.writeN(TransferInquiryField.DEPOSIT_BANK_CODE, Long.parseLong(transfer.getDepositBankCode()));
        response.writeAN(TransferInquiryField.DEPOSIT_ACCOUNT_NO, transfer.getDepositAccountNo());
        response.writeAmount(TransferInquiryField.REQUESTED_AMOUNT, transfer.getAmount());
        response.writeAmount(TransferInquiryField.PROCESSED_AMOUNT, success ? transfer.getAmount() : BigDecimal.ZERO);
        response.writeAmount(TransferInquiryField.UNPROCESSED_AMOUNT, success ? BigDecimal.ZERO : transfer.getAmount());
        response.writeN(TransferInquiryField.FEE, 0);
        response.writeN(TransferInquiryField.TRANSFER_TIME,
                Long.parseLong(transfer.getCreatedAt().format(TRANSFER_TIME_FORMAT)));
        response.writeAN(TransferInquiryField.PROCESS_RESULT, transfer.getResponseCode());
    }
}
