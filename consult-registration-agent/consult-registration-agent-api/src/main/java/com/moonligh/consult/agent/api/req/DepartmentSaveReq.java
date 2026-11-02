package com.moonligh.consult.agent.api.req;

/**
 * 新建或改一级类。开通/停用走 enabled。
 */
public record DepartmentSaveReq(
        String name,
        Boolean enabled,
        Integer genderScope,
        Integer childAgeLt,
        String treatDesc) {
}
