package com.moonligh.pay.infrastructure.persistence;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moonligh.pay.domain.PayOrder;

@Mapper
public interface PayOrderMapper {

    void ensureTable();

    void upsert(@Param("order") PayOrder order, @Param("createdAt") Instant createdAt);

    PayOrder find(@Param("tradeNo") String tradeNo);

    int markPaid(@Param("bizOrderNo") String bizOrderNo, @Param("paidAt") Instant paidAt);
}
