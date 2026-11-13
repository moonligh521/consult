package com.moonligh.consult.agent.infrastructure.persistence;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OpeningMapper {

    int insert(
            @Param("accountId") String accountId,
            @Param("textHash") String textHash,
            @Param("consultationId") String consultationId,
            @Param("createdAt") Instant createdAt);

    int takeIfExpired(
            @Param("consultationId") String consultationId,
            @Param("createdAt") Instant createdAt,
            @Param("accountId") String accountId,
            @Param("textHash") String textHash,
            @Param("windowStart") Instant windowStart);

    String findConsultationId(@Param("accountId") String accountId, @Param("textHash") String textHash);

    int replace(
            @Param("consultationId") String consultationId,
            @Param("createdAt") Instant createdAt,
            @Param("accountId") String accountId,
            @Param("textHash") String textHash);
}
