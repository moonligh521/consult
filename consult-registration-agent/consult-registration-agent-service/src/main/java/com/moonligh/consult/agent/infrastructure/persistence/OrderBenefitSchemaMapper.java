package com.moonligh.consult.agent.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderBenefitSchemaMapper {

    Integer columnCount(@Param("column") String column);

    void addBenefitAmount();

    void addBenefitCardId();

    void addBenefitReady();

    void addBenefitSettled();
}
