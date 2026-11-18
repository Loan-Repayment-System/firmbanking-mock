package com.firmbanking.domain.transfer.service;

import com.firmbanking.domain.transfer.dto.request.InquireTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.domain.transfer.dto.response.TransferInquiryResponse;
import com.firmbanking.domain.transfer.mapper.TransferMapper;
import com.firmbanking.domain.transfer.mapper.TransferRequestMapper;
import com.firmbanking.global.code.ResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@DisplayName("TransferInquiryService")
class TransferInquiryServiceTest {

    private TransferMapper transferMapper;
    private TransferRequestMapper transferRequestMapper;
    private TransferInquiryService transferInquiryService;

    @BeforeEach
    void setUp() {
        transferMapper = mock(TransferMapper.class);
        transferRequestMapper = mock(TransferRequestMapper.class);
        transferInquiryService = new TransferInquiryService(transferMapper, transferRequestMapper);
    }

    @Test
    @DisplayName("CAPITAL00001 / 20261029 / 000100의 이체 결과가 있으면 0000과 그 이체 결과를 돌려주고 요청 기록을 조회하지 않는다")
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
        then(transferRequestMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이체 결과가 없고 60초 이내 처리 중이면 D003을 돌려준다")
    void 처리_중이면_D003이다() {
        // given
        given(transferMapper.findByCompanyNoAndSentDateAndMessageNo("CAPITAL00001", "20261029", "000100"))
                .willReturn(null);
        given(transferRequestMapper.existsRecentProcessing(any())).willReturn(true);

        // when
        TransferInquiryResponse result = transferInquiryService.inquire(request());

        // then
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.TRANSFER_IN_PROGRESS);
        assertThat(result.getTransfer()).isNull();
    }

    // 전문 명세 5절 D002: 조회한 거래고유번호의 이체가 없음
    @Test
    @DisplayName("이체 결과가 없고 60초 이내 처리 중인 요청도 없으면 D002를 돌려준다")
    void 원거래가_없으면_D002다() {
        // given
        given(transferMapper.findByCompanyNoAndSentDateAndMessageNo("CAPITAL00001", "20261029", "000100"))
                .willReturn(null);
        given(transferRequestMapper.existsRecentProcessing(any())).willReturn(false);

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
