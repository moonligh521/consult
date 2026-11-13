package com.moonligh.consult.identity.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moonligh.consult.identity.domain.AssignmentNoticeStore.PendingNotice;

@Mapper
public interface AssignmentNoticeMapper {

    void insertOutbox(
            @Param("orderId") String orderId,
            @Param("consultationId") String consultationId,
            @Param("doctorId") String doctorId,
            @Param("specialty") String specialty,
            @Param("scheduleId") String scheduleId);

    void insertInbox(
            @Param("orderId") String orderId,
            @Param("consultationId") String consultationId,
            @Param("doctorId") String doctorId,
            @Param("specialty") String specialty,
            @Param("scheduleId") String scheduleId);

    void releaseStaleClaims();

    void claimPending(@Param("token") String token, @Param("size") int size);

    List<PendingNotice> listClaimed(@Param("token") String token);

    void releaseStaleClaim(@Param("orderId") String orderId);

    int claimOne(@Param("token") String token, @Param("orderId") String orderId);

    List<PendingNotice> findClaimed(@Param("orderId") String orderId, @Param("token") String token);

    void markSent(@Param("orderId") String orderId);

    void releaseClaim(@Param("orderId") String orderId);

    List<PendingNotice> inbox(@Param("doctorId") String doctorId, @Param("limit") int limit);
}
