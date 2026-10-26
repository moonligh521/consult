package com.moonligh.consult.agent.domain.triage;

/**
 * 挂号链路上要单独算准确率的节点。N1 安全写在 safety_decision_audit。
 */
public enum TriageNode {

    N2_VAGUE(2),
    N3_SLOT(3),
    N4_CANON(4),
    N5_SYMPTOM(5),
    N6_DEPARTMENT(6),
    N7_CARD(7),
    N8_DOCTOR(8),
    N9_OCCUPY(9);

    private final int code;

    TriageNode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
