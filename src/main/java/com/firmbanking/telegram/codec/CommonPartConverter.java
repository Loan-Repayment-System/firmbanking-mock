package com.firmbanking.telegram.codec;

/**
 * 전문 바이트 → CommonPart. N 필드가 숫자인지와 전송일자·전송시간이 있는 값인지만 검사하고, 고정값은 검사하지 않는다.
 */
public final class CommonPartConverter {

    private CommonPartConverter() {
    }

    public static CommonPart read(byte[] bytes) {
        CommonPart commonPart = CommonPart.builder()
                .companyNo(FieldDecoder.readAN(bytes, CommonField.COMPANY_NO))
                .bankCode(FieldDecoder.readN(bytes, CommonField.BANK_CODE))
                .telegramTypeCode(FieldDecoder.readN(bytes, CommonField.TELEGRAM_TYPE_CODE))
                .businessTypeCode(FieldDecoder.readN(bytes, CommonField.BUSINESS_TYPE_CODE))
                .telegramNo(FieldDecoder.readN(bytes, CommonField.TELEGRAM_NO))
                .sendDate(FieldDecoder.readDate(bytes, CommonField.SEND_DATE))
                .sendTime(FieldDecoder.readTime(bytes, CommonField.SEND_TIME))
                .build();
        // 송신회수는 쓰지 않지만 N 형식은 검사한다
        FieldDecoder.readN(bytes, CommonField.SEND_COUNT);
        return commonPart;
    }
}
