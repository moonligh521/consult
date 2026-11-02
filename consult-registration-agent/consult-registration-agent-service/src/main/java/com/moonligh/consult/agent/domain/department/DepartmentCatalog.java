package com.moonligh.consult.agent.domain.department;

/**
 * 当前生效的科室目录。定科、改卡、下拉都读这份快照。
 */
public interface DepartmentCatalog {

    DepartmentSnapshot snapshot();
}
