package com.firmbanking.telegram.controller;

import com.firmbanking.domain.company.service.CompanyService;
import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferResultResponse;
import com.firmbanking.domain.transfer.service.TransferService;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.global.code.TransferType;
import com.firmbanking.telegram.TelegramController;
import com.firmbanking.telegram.codec.CommonField;
import com.firmbanking.telegram.codec.CommonPart;
import com.firmbanking.telegram.codec.InvalidTelegramException;
import com.firmbanking.telegram.codec.RequestTelegram;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.controller.field.CollectionField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 집금이체 2000/200 (전문 명세 4.2절).
 * 입금후잔액부호·입금후잔액·수수료는 정상일 때만 채운다.
 */
@Component
@RequiredArgsConstructor
public class CollectionController implements TelegramController {

    private final CompanyService companyService;
    private final TransferService transferService;

    @Override
    public String telegramTypeCode() {
        return "2000";
    }

    @Override
    public String businessTypeCode() {
        return "200";
    }

    @Override
    public ResponseCode process(RequestTelegram request, ResponseTelegram response) {
        if (!companyService.isRegistered(request.getCommonPart().getCompanyNo())) {
            return ResponseCode.UNREGISTERED_COMPANY;
        }
        TransferResultResponse result = transferService.transfer(read(request));
        if (result.getResponseCode() == ResponseCode.SUCCESS) {
            write(response, result);
        }
        return result.getResponseCode();
    }

    private static ExecuteTransferRequest read(RequestTelegram request) {
        BigDecimal amount = request.readAmount(CollectionField.AMOUNT);
        if (amount.signum() <= 0) {
            throw new InvalidTelegramException("출금금액은 1 이상이다: " + amount);
        }
        CommonPart commonPart = request.getCommonPart();
        // 집금 출금계좌는 공통부 은행의 계좌다.
        return ExecuteTransferRequest.builder()
                .companyNo(commonPart.getCompanyNo())
                .sentDate(request.readN(CommonField.SEND_DATE))
                .messageNo(commonPart.getTelegramNo())
                .sentTime(request.readN(CommonField.SEND_TIME))
                .transferType(TransferType.COLLECTION)
                .withdrawalBankCode(commonPart.getBankCode())
                .withdrawalAccountNo(request.readAN(CollectionField.WITHDRAWAL_ACCOUNT_NO))
                .depositBankCode(request.readN(CollectionField.DEPOSIT_BANK_CODE))
                .depositAccountNo(request.readAN(CollectionField.DEPOSIT_ACCOUNT_NO))
                .amount(amount)
                .memo(request.readAN(CollectionField.MEMO))
                .build();
    }

    private static void write(ResponseTelegram response, TransferResultResponse result) {
        response.writeAN(CollectionField.BALANCE_SIGN, "+");
        response.writeAmount(CollectionField.BALANCE_AFTER, result.getBalanceAfter());
        response.writeN(CollectionField.FEE, 0);
    }
}
