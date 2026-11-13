package com.moonligh.consult.agent.infrastructure.judge;

/** 旁路评分模型。不走挂号 ModelGateway，避免占舱壁、写出 agent.model。 */
public interface JudgeModelPort {

    String complete(String prompt);
}
