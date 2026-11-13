package com.moonligh.consult.agent.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpeningSchemaMapper {

    void createOpeningTable();
}
