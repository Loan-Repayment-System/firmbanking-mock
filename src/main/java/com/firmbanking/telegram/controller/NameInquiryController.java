package com.firmbanking.telegram.controller;

import com.firmbanking.domain.account.dto.request.InquireAccountNameRequest;
import com.firmbanking.domain.account.dto.response.AccountNameResponse;
import com.firmbanking.domain.account.service.AccountNameService;
import com.firmbanking.domain.company.service.CompanyService;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.TelegramController;
import com.firmbanking.telegram.codec.RequestTelegram;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.controller.field.NameInquiryField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 계좌성명 조회 6000/100 (전문 명세 4.5절).
 */
@Component
@RequiredArgsConstructor
public class NameInquiryController implements TelegramController {

    private final CompanyService companyService;
    private final AccountNameService accountNameService;

    @Override
    public String telegramTypeCode() {
        return "6000";
    }

    @Override
    public String businessTypeCode() {
        return "100";
    }

    @Override
    public ResponseCode process(RequestTelegram request, ResponseTelegram response) {
        if (!companyService.isRegistered(request.getCommonPart().getCompanyNo())) {
            return ResponseCode.UNREGISTERED_COMPANY;
        }
        AccountNameResponse result = accountNameService.inquire(read(request));
        if (result.getResponseCode() == ResponseCode.SUCCESS) {
            write(response, result);
        }
        return result.getResponseCode();
    }

    private static InquireAccountNameRequest read(RequestTelegram request) {
        InquireAccountNameRequest inquiry = InquireAccountNameRequest.builder()
                .bankCode(request.readN(NameInquiryField.BANK_CODE))
                .accountNo(request.readAN(NameInquiryField.ACCOUNT_NO))
                .realNameNo(request.readAN(NameInquiryField.REAL_NAME_NO))
                .build();
        // 금액은 형식만 검사하고 쓰지 않는다.
        request.readAmount(NameInquiryField.AMOUNT);
        return inquiry;
    }

    private static void write(ResponseTelegram response, AccountNameResponse result) {
        response.writeAN(NameInquiryField.HOLDER_NAME, result.getHolderName());
    }
}
