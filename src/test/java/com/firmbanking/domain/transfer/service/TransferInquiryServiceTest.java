package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.transfer.dto.request.InquireTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.domain.transfer.dto.response.TransferInquiryResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.global.code.ResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("TransferInquiryService")
class TransferInquiryServiceTest {

    private TransferMapper transferMapper;
    private TransferInquiryService transferInquiryService;

    @BeforeEach
    void setUp() {
        transferMapper = mock(TransferMapper.class);
        transferInquiryService = new TransferInquiryService(transferMapper);
    }

    @Test
    @DisplayName("CAPITAL00001 / 20261029 / 000100의 이체 결과가 있으면 0000과 그 이체 결과를 돌려준다")
    void 원거래가_있으면_0000이다() {
        // given
        TransferDetailResponse transfer = TransferDetailResponse.builder().transferId(1L).responseCode("B003").build();
        given(transferMapper.findByCompanyNoAndSentDateAndMessageNo("CAPITAL00001", "20261029", "000100"))
                .willReturn(transfer);

        // when
        TransferInquiryResponse result = transferInquiryService.inquire(request());

        // then
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.SUCCESS);
        assertThat(result.getTransfer()).isSameAs(transfer);
    }

    // 전문 명세 5절 D002: 조회한 거래고유번호의 이체가 없음
    @Test
    @DisplayName("CAPITAL00001 / 20261029 / 000100의 이체 결과가 없으면 D002를 돌려준다")
    void 원거래가_없으면_D002다() {
        // given
        given(transferMapper.findByCompanyNoAndSentDateAndMessageNo("CAPITAL00001", "20261029", "000100"))
                .willReturn(null);

        // when
        TransferInquiryResponse result = transferInquiryService.inquire(request());

        // then
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.ORIGINAL_TRANSFER_NOT_FOUND);
        assertThat(result.getTransfer()).isNull();
    }

    private static InquireTransferRequest request() {
        return InquireTransferRequest.builder()
                .companyNo("CAPITAL00001")
                .sentDate("20261029")
                .messageNo("000100")
                .build();
    }
}
