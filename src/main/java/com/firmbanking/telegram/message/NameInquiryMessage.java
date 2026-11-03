package com.firmbanking.telegram.message;

import com.firmbanking.domain.account.dto.request.InquireAccountNameRequest;
import com.firmbanking.domain.account.dto.response.AccountNameResponse;
import com.firmbanking.domain.account.service.AccountNameService;
import com.firmbanking.domain.company.service.CompanyService;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.TelegramTransaction;
import com.firmbanking.telegram.codec.RequestTelegram;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.codec.TelegramField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 계좌성명 조회 6000/100 (전문 명세 4.5절).
 */
@Component
@RequiredArgsConstructor
public class NameInquiryMessage implements TelegramTransaction {

    static final TelegramField ACCOUNT_NO = new TelegramField(100, 15);
    static final TelegramField REAL_NAME_NO = new TelegramField(115, 13);
    static final TelegramField HOLDER_NAME = new TelegramField(128, 12);
    static final TelegramField BANK_CODE = new TelegramField(140, 3);
    static final TelegramField AMOUNT = new TelegramField(143, 13);

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
        InquireAccountNameRequest inquiry = InquireAccountNameRequest.builder()
                .bankCode(request.readN(BANK_CODE))
                .accountNo(request.readAN(ACCOUNT_NO))
                .realNameNo(request.readAN(REAL_NAME_NO))
                .build();
        // 금액은 형식만 검사하고 쓰지 않는다.
        request.readAmount(AMOUNT);

        AccountNameResponse result = accountNameService.inquire(inquiry);
        if (result.getResponseCode() == ResponseCode.SUCCESS) {
            response.writeAN(HOLDER_NAME, result.getHolderName());
        }
        return result.getResponseCode();
    }
}
