package com.firmbanking.telegram.controller;

import com.firmbanking.domain.company.service.CompanyService;
import com.firmbanking.domain.consent.dto.request.ApplyWithdrawalConsentRequest;
import com.firmbanking.domain.consent.service.WithdrawalConsentService;
import com.firmbanking.global.code.ConsentStatus;
import com.firmbanking.global.code.ResponseCode;
import com.firmbanking.telegram.TelegramController;
import com.firmbanking.telegram.codec.InvalidTelegramException;
import com.firmbanking.telegram.codec.RequestTelegram;
import com.firmbanking.telegram.codec.ResponseTelegram;
import com.firmbanking.telegram.controller.field.WithdrawalConsentField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 수납출금계좌 등록 2000/550 (전문 명세 4.4절).
 * 업체 확인에서 거절되면 처리여부·불능코드를 채우지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawalConsentController implements TelegramController {

    private final CompanyService companyService;
    private final WithdrawalConsentService withdrawalConsentService;

    @Override
    public String telegramTypeCode() {
        return "2000";
    }

    @Override
    public String businessTypeCode() {
        return "550";
    }

    @Override
    public ResponseCode process(RequestTelegram request, ResponseTelegram response) {
        String companyNo = request.getCommonPart().getCompanyNo();
        if (!companyService.isRegistered(companyNo)) {
            return ResponseCode.UNREGISTERED_COMPANY;
        }
        ResponseCode responseCode = withdrawalConsentService.apply(read(request, companyNo));
        write(response, responseCode);
        return responseCode;
    }

    private static ApplyWithdrawalConsentRequest read(RequestTelegram request, String companyNo) {
        return ApplyWithdrawalConsentRequest.builder()
                .companyNo(companyNo)
                .bankCode(request.readN(WithdrawalConsentField.BANK_CODE))
                .accountNo(request.readAN(WithdrawalConsentField.ACCOUNT_NO))
                .consentStatus(consentStatus(request.readN(WithdrawalConsentField.APPLICATION_TYPE)))
                .payerNo(request.readAN(WithdrawalConsentField.PAYER_NO))
                .appliedDate(request.readAN(WithdrawalConsentField.APPLIED_DATE))
                .build();
    }

    // 신청구분 1 신규, 2 해지
    private static ConsentStatus consentStatus(String applicationType) {
        return switch (applicationType) {
            case "1" -> ConsentStatus.REGISTERED;
            case "2" -> ConsentStatus.CANCELLED;
            default -> throw new InvalidTelegramException("신청구분은 1 또는 2다: " + applicationType);
        };
    }

    private static void write(ResponseTelegram response, ResponseCode responseCode) {
        boolean success = responseCode == ResponseCode.SUCCESS;
        response.writeN(WithdrawalConsentField.PROCESS_SEQUENCE, 0);
        response.writeAN(WithdrawalConsentField.PROCESSED, success ? "Y" : "N");
        response.writeAN(WithdrawalConsentField.FAILURE_CODE, success ? "" : responseCode.getCode());
    }
}
