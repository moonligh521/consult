package com.moonligh.consult.agent.api.req;

/**
 * 新建或改二级科。
 */
public record SpecialtySaveReq(String name, String treatDesc, Boolean enabled) {
}
