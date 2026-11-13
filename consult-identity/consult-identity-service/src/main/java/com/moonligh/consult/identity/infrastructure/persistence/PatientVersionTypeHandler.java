package com.moonligh.consult.identity.infrastructure.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** patientVersion 是 updated_at 的纪元毫秒字符串。空时间写成 0。 */
public class PatientVersionTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return version(rs.getTimestamp(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return version(rs.getTimestamp(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return version(cs.getTimestamp(columnIndex));
    }

    private static String version(Timestamp updated) {
        Instant updatedAt = updated == null ? Instant.EPOCH : updated.toInstant();
        return Long.toString(updatedAt.toEpochMilli());
    }
}
