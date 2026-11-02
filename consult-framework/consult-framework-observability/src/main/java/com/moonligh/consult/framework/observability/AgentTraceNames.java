package com.moonligh.consult.framework.observability;

/**
 * 稳定 span 名。Langfuse 筛选和回归标注都按这些名字。
 */
public final class AgentTraceNames {

    public static final String SAFETY = "agent.safety";
    public static final String TRIAGE = "agent.triage";
    public static final String ROUTE = "agent.route";
    public static final String VALIDATE = "agent.validate";
    public static final String MODEL = "agent.model";
    public static final String TURN = "agent.turn";

    private AgentTraceNames() {
    }
}
