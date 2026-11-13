package com.moonligh.consult.agent.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SafetyCatalogReleaseMapper {

    int insertIgnore(
            @Param("catalogVersion") String catalogVersion,
            @Param("checksum") String checksum,
            @Param("payload") String payload);
}
