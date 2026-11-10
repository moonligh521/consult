package com.moonligh.consult.agent.domain;

/**
 * 某个二级科此刻有没有至少一名在线医生。号满了仍算在线，患者去排队。
 * 全科不看这个结果，始终可推荐。
 */
@FunctionalInterface
public interface SpecialtyPresence {

    /** 身份服务暂时不可用时返回 true，避免把所有科都改去全科。 */
    boolean online(String specialty);
}
