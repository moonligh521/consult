package com.moonligh.consult.identity.infrastructure.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** idle_since 为空时按 0 毫秒，和原来 ResultSet 取值一致。 */
public class EpochMillisTypeHandler extends BaseTypeHandler<Long> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Long parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setTimestamp(i, new Timestamp(parameter));
    }

    @Override
    public Long getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return millis(rs.getTimestamp(columnName));
    }

    @Override
    public Long getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return millis(rs.getTimestamp(columnIndex));
    }

    @Override
    public Long getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return millis(cs.getTimestamp(columnIndex));
    }

    private static long millis(Timestamp idle) {
        return idle == null ? 0L : idle.toInstant().toEpochMilli();
    }
}
