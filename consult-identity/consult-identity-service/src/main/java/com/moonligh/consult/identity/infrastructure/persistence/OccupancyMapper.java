package com.moonligh.consult.identity.infrastructure.persistence;

import java.sql.Timestamp;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moonligh.consult.identity.domain.OccupancyRow;

@Mapper
public interface OccupancyMapper {

    List<OccupancyRow> find(@Param("orderId") String orderId);

    void insert(
            @Param("orderId") String orderId,
            @Param("doctorId") String doctorId,
            @Param("scheduleId") String scheduleId,
            @Param("status") int status,
            @Param("occupiedAt") Timestamp occupiedAt,
            @Param("expiresAt") Timestamp expiresAt);

    int markReleased(
            @Param("reason") int reason, @Param("releasedAt") Timestamp releasedAt, @Param("orderId") String orderId);

    int updateExpiry(@Param("expiresAt") Timestamp expiresAt, @Param("orderId") String orderId);

    List<OccupancyRow> claimExpired(@Param("now") Timestamp now, @Param("limit") int limit);
}
