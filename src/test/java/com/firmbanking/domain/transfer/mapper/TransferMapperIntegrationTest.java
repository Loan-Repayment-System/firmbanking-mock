package com.firmbanking.domain.transfer.mapper;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.transfer.dto.request.CreateTransferRequest;
import com.firmbanking.domain.transfer.dto.response.TransferDetailResponse;
import com.firmbanking.global.code.TransferType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("TransferMapper")
class TransferMapperIntegrationTest {

    private static final String COMPANY_NO = "CAPITAL00001";
    private static final String SENT_DATE = "20261029";

    @Autowired
    private TransferMapper transferMapper;

    @Test
    @DisplayName("CAPITAL00001 / 20261029 / 000123의 이체 결과가 없으면 null이다")
    void 이체_결과가_없으면_null이다() {
        // when
        TransferDetailResponse transfer =
                transferMapper.findByCompanyNoAndSentDateAndMessageNo(COMPANY_NO, SENT_DATE, "000123");

        // then
        assertThat(transfer).isNull();
    }

    @Nested
    @DisplayName("000123을 0000 / 잔액 9,999,000,000으로 INSERT했을 때")
    class Success {

        @BeforeEach
        void setUp() {
            transferMapper.insert(request("000123", "0000", new BigDecimal("9999000000")));
        }

        @Test
        @DisplayName("CAPITAL00001 / 20261029 / 000123으로 조회하면 넣은 값이 그대로다")
        void 넣은_값으로_조회된다() {
            // when
            TransferDetailResponse transfer =
                    transferMapper.findByCompanyNoAndSentDateAndMessageNo(COMPANY_NO, SENT_DATE, "000123");

            // then
            assertThat(transfer.getTransferId()).isNotNull();
            assertThat(transfer.getCompanyNo()).isEqualTo(COMPANY_NO);
            assertThat(transfer.getSentDate()).isEqualTo(SENT_DATE);
            assertThat(transfer.getMessageNo()).isEqualTo("000123");
            assertThat(transfer.getSentTime()).isEqualTo("143005");
            assertThat(transfer.getTransferTypeId()).isEqualTo(TransferType.PAYMENT);
            assertThat(transfer.getWithdrawalAccountNo()).isEqualTo("100-000-000002");
            assertThat(transfer.getDepositBankCode()).isEqualTo("999");
            assertThat(transfer.getDepositAccountNo()).isEqualTo("200-000-000001");
            assertThat(transfer.getAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
            assertThat(transfer.getMemo()).isEqualTo("대출금");
            assertThat(transfer.getResponseCode()).isEqualTo("0000");
            assertThat(transfer.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("9999000000"));
        }

        // ERD transfer: 처리 시각은 created_at을 쓴다
        @Test
        @DisplayName("조회하면 DB가 채운 처리 시각 createdAt이 있다")
        void 처리_시각이_채워진다() {
            // when
            TransferDetailResponse transfer =
                    transferMapper.findByCompanyNoAndSentDateAndMessageNo(COMPANY_NO, SENT_DATE, "000123");

            // then
            assertThat(transfer.getCreatedAt()).isNotNull();
        }

        // ERD transfer: UNIQUE (company_no, sent_date, message_no)
        @Test
        @DisplayName("같은 CAPITAL00001 / 20261029 / 000123으로 한 번 더 INSERT하면 DuplicateKeyException을 던진다")
        void 같은_거래고유번호면_키_중복이다() {
            // when, then
            assertThatThrownBy(() -> transferMapper.insert(request("000123", "B003", null)))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Test
    @DisplayName("000124를 B003 / 잔액 null로 INSERT하고 조회하면 응답코드 B003, 잔액 null이다")
    void 거절은_잔액이_null이다() {
        // given
        transferMapper.insert(request("000124", "B003", null));

        // when
        TransferDetailResponse transfer =
                transferMapper.findByCompanyNoAndSentDateAndMessageNo(COMPANY_NO, SENT_DATE, "000124");

        // then
        assertThat(transfer.getResponseCode()).isEqualTo("B003");
        assertThat(transfer.getBalanceAfter()).isNull();
    }

    private static CreateTransferRequest request(String messageNo, String responseCode, BigDecimal balanceAfter) {
        return CreateTransferRequest.builder()
                .companyNo(COMPANY_NO)
                .sentDate(SENT_DATE)
                .messageNo(messageNo)
                .sentTime("143005")
                .transferType(TransferType.PAYMENT)
                .withdrawalAccountNo("100-000-000002")
                .depositBankCode("999")
                .depositAccountNo("200-000-000001")
                .amount(new BigDecimal("1000000"))
                .memo("대출금")
                .responseCode(responseCode)
                .balanceAfter(balanceAfter)
                .build();
    }
}
