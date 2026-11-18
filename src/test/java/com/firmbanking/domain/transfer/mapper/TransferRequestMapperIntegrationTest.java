package com.firmbanking.domain.transfer.mapper;

import com.firmbanking.TestcontainersConfig;
import com.firmbanking.domain.transfer.dto.request.ExecuteTransferRequest;
import com.firmbanking.domain.transfer.dto.request.InquireTransferRequest;
import com.firmbanking.global.code.TransferRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("TransferRequestMapper")
class TransferRequestMapperIntegrationTest {

    private static final String COMPANY_NO = "CAPITAL00001";
    private static final String SENT_DATE = "20261029";
    private static final String MESSAGE_NO = "000123";
    private static final ExecuteTransferRequest REQUEST = ExecuteTransferRequest.builder()
            .companyNo(COMPANY_NO)
            .sentDate(SENT_DATE)
            .messageNo(MESSAGE_NO)
            .build();
    private static final InquireTransferRequest INQUIRY = InquireTransferRequest.builder()
            .companyNo(COMPANY_NO)
            .sentDate(SENT_DATE)
            .messageNo(MESSAGE_NO)
            .build();

    @Autowired
    private TransferRequestMapper transferRequestMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("요청 기록이 없을 때 처리 중으로 INSERT하면 처리 중 1행이 저장된다")
    void 처리_중으로_저장한다() {
        // when
        transferRequestMapper.insertProcessing(REQUEST);

        // then
        assertThat(status()).isEqualTo(TransferRequestStatus.PROCESSING.getId());
    }

    @Test
    @DisplayName("요청 기록이 없으면 60초 이내 처리 중 여부는 false다")
    void 기록이_없으면_처리_중이_아니다() {
        // when
        boolean exists = transferRequestMapper.existsRecentProcessing(INQUIRY);

        // then
        assertThat(exists).isFalse();
    }

    @Nested
    @DisplayName("처리 중 기록을 방금 저장했을 때")
    class Processing {

        @BeforeEach
        void setUp() {
            transferRequestMapper.insertProcessing(REQUEST);
        }

        // UNIQUE (company_no, sent_date, message_no)
        @Test
        @DisplayName("같은 거래고유번호로 INSERT하면 DuplicateKeyException을 던진다")
        void 같은_거래고유번호면_키_중복이다() {
            // when, then
            assertThatThrownBy(() -> transferRequestMapper.insertProcessing(REQUEST))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        @DisplayName("60초 지난 처리 중을 오류로 바꾸면 0행이 바뀌고 처리 중을 유지한다")
        void 방금_저장했으면_오류로_바꾸지_않는다() {
            // when
            int updated = transferRequestMapper.updateStaleProcessingToError(REQUEST);

            // then
            assertThat(updated).isZero();
            assertThat(status()).isEqualTo(TransferRequestStatus.PROCESSING.getId());
        }

        @Test
        @DisplayName("오류를 처리 중으로 바꾸면 0행이 바뀐다")
        void 처리_중은_다시_시작하지_않는다() {
            // when
            int updated = transferRequestMapper.updateErrorToProcessing(REQUEST);

            // then
            assertThat(updated).isZero();
        }

        @Test
        @DisplayName("처리 중을 오류로 바꾸면 1행이 바뀌고 오류가 된다")
        void 처리_중을_오류로_바꾼다() {
            // when
            int updated = transferRequestMapper.updateProcessingToError(REQUEST);

            // then
            assertThat(updated).isEqualTo(1);
            assertThat(status()).isEqualTo(TransferRequestStatus.ERROR.getId());
        }

        @Test
        @DisplayName("60초 이내 처리 중 여부는 true다")
        void 방금_저장했으면_처리_중이다() {
            // when
            boolean exists = transferRequestMapper.existsRecentProcessing(INQUIRY);

            // then
            assertThat(exists).isTrue();
        }

        @Nested
        @DisplayName("저장한 지 61초가 지났을 때")
        class Stale {

            @BeforeEach
            void setUp() {
                jdbcTemplate.update("""
                        UPDATE transfer_request
                        SET updated_at = NOW() - INTERVAL 61 SECOND
                        WHERE company_no = ? AND sent_date = ? AND message_no = ?
                        """, COMPANY_NO, SENT_DATE, MESSAGE_NO);
            }

            @Test
            @DisplayName("60초 지난 처리 중을 오류로 바꾸면 1행이 바뀌고 오류가 된다")
            void 오류로_바꾼다() {
                // when
                int updated = transferRequestMapper.updateStaleProcessingToError(REQUEST);

                // then
                assertThat(updated).isEqualTo(1);
                assertThat(status()).isEqualTo(TransferRequestStatus.ERROR.getId());
            }

            @Test
            @DisplayName("60초 이내 처리 중 여부는 false다")
            void 처리_중이_아니다() {
                // when
                boolean exists = transferRequestMapper.existsRecentProcessing(INQUIRY);

                // then
                assertThat(exists).isFalse();
            }
        }

        @Nested
        @DisplayName("오류 상태로 바뀌었을 때")
        class Error {

            @BeforeEach
            void setUp() {
                setStatus(TransferRequestStatus.ERROR);
            }

            @Test
            @DisplayName("오류를 처리 중으로 바꾸면 1행이 바뀌고 처리 중이 된다")
            void 처리_중으로_바꾼다() {
                // when
                int updated = transferRequestMapper.updateErrorToProcessing(REQUEST);

                // then
                assertThat(updated).isEqualTo(1);
                assertThat(status()).isEqualTo(TransferRequestStatus.PROCESSING.getId());
            }

            // 오류로 바뀐 뒤 늦게 끝난 원거래도 완료로 확정한다
            @Test
            @DisplayName("완료로 바꾸면 완료가 된다")
            void 늦게_끝난_원거래를_완료로_바꾼다() {
                // when
                transferRequestMapper.updateToCompleted(REQUEST);

                // then
                assertThat(status()).isEqualTo(TransferRequestStatus.COMPLETED.getId());
            }

            @Test
            @DisplayName("60초 이내 처리 중 여부는 false다")
            void 처리_중이_아니다() {
                // when
                boolean exists = transferRequestMapper.existsRecentProcessing(INQUIRY);

                // then
                assertThat(exists).isFalse();
            }
        }

        @Test
        @DisplayName("완료 상태에서 처리 중을 오류로 바꾸면 0행이 바뀌고 완료를 유지한다")
        void 완료는_오류로_바꾸지_않는다() {
            // given
            setStatus(TransferRequestStatus.COMPLETED);

            // when
            int updated = transferRequestMapper.updateProcessingToError(REQUEST);

            // then
            assertThat(updated).isZero();
            assertThat(status()).isEqualTo(TransferRequestStatus.COMPLETED.getId());
        }
    }

    private void setStatus(TransferRequestStatus status) {
        jdbcTemplate.update("""
                UPDATE transfer_request
                SET transfer_request_status_id = ?
                WHERE company_no = ? AND sent_date = ? AND message_no = ?
                """, status.getId(), COMPANY_NO, SENT_DATE, MESSAGE_NO);
    }

    private Integer status() {
        return jdbcTemplate.queryForObject("""
                SELECT transfer_request_status_id
                FROM transfer_request
                WHERE company_no = ? AND sent_date = ? AND message_no = ?
                """, Integer.class, COMPANY_NO, SENT_DATE, MESSAGE_NO);
    }
}
