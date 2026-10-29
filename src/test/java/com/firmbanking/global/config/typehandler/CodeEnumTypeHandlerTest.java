package com.firmbanking.global.config.typehandler;

import com.firmbanking.global.code.TransferType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeEnumTypeHandler")
class CodeEnumTypeHandlerTest {

    private static final String COLUMN = "transfer_type_id";

    private final CodeEnumTypeHandler<TransferType> handler = new CodeEnumTypeHandler<>(TransferType.class);

    @Mock
    private ResultSet resultSet;

    @Mock
    private PreparedStatement preparedStatement;

    @Test
    @DisplayName("컬럼 값이 1이면 PAYMENT로 읽는다")
    void id_1을_PAYMENT로_읽는다() throws SQLException {
        // given
        given(resultSet.getInt(COLUMN)).willReturn(1);
        given(resultSet.wasNull()).willReturn(false);

        // when
        TransferType result = handler.getResult(resultSet, COLUMN);

        // then
        assertThat(result).isEqualTo(TransferType.PAYMENT);
    }

    @Test
    @DisplayName("COLLECTION을 파라미터로 쓰면 id 2를 넣는다")
    void COLLECTION을_id_2로_쓴다() throws SQLException {
        // when
        handler.setParameter(preparedStatement, 1, TransferType.COLLECTION, null);

        // then
        then(preparedStatement).should().setInt(1, 2);
    }

    // MyBatis BaseTypeHandler는 핸들러 예외를 ResultMapException으로 감싼다
    @Test
    @DisplayName("enum에 없는 id 9를 읽으면 IllegalArgumentException이 원인인 예외를 던진다")
    void 없는_id를_읽으면_예외를_던진다() throws SQLException {
        // given
        given(resultSet.getInt(COLUMN)).willReturn(9);
        given(resultSet.wasNull()).willReturn(false);

        // when, then
        assertThatThrownBy(() -> handler.getResult(resultSet, COLUMN))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("컬럼 값이 NULL이면 null로 읽는다")
    void NULL을_null로_읽는다() throws SQLException {
        // given
        given(resultSet.getInt(COLUMN)).willReturn(0);
        given(resultSet.wasNull()).willReturn(true);

        // when
        TransferType result = handler.getResult(resultSet, COLUMN);

        // then
        assertThat(result).isNull();
    }
}
