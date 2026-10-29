package com.firmbanking.global.config.typehandler;

import com.firmbanking.global.code.CodeEnum;
import com.firmbanking.global.code.ConsentStatus;
import com.firmbanking.global.code.TransferType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 코드성 테이블 enum ↔ id 컬럼. 코드 enum을 추가하면 @MappedTypes에도 추가한다.
 * MyBatis가 @MappedTypes의 타입마다 이 클래스를 생성자에 타입을 넘겨 만든다.
 */
@MappedTypes({TransferType.class, ConsentStatus.class})
public class CodeEnumTypeHandler<E extends Enum<E> & CodeEnum> extends BaseTypeHandler<E> {

    private final Class<E> type;

    public CodeEnumTypeHandler(Class<E> type) {
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, parameter.getId());
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int id = rs.getInt(columnName);
        return rs.wasNull() ? null : CodeEnum.fromId(type, id);
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int id = rs.getInt(columnIndex);
        return rs.wasNull() ? null : CodeEnum.fromId(type, id);
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int id = cs.getInt(columnIndex);
        return cs.wasNull() ? null : CodeEnum.fromId(type, id);
    }
}
