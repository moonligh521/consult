package com.moonligh.consult.agent.domain.triage;

import java.util.List;

import com.moonligh.consult.agent.domain.DepartmentRouter;

/**
 * N6 挂科结论。不确定时 candidates 是交给模型挑的二级科（总含全科）。
 */
public record RouteDecision(
        DepartmentRouter.Advice advice,
        Certainty certainty,
        DecisionSource source,
        String reason,
        List<String> candidates) {

    public static final String UNCERTAIN_MARKER = "UNCERTAIN_MARKER";
    public static final String OVERRIDE = "OVERRIDE";
    public static final String PEDIATRIC = "PEDIATRIC";
    public static final String CANON_TERM = "CANON_TERM";
    public static final String CONFLICT = "CONFLICT";
    public static final String SINGLE_SPECIALTY = "SINGLE_SPECIALTY";
    public static final String SAME_L1_EARLIEST = "SAME_L1_EARLIEST";
    public static final String AMBIGUOUS_ONLY = "AMBIGUOUS_ONLY";
    public static final String NO_HIT = "NO_HIT";
    public static final String NO_HIT_SHORT = "NO_HIT_SHORT";
    public static final String MODEL_TERM_ROUTED = "MODEL_TERM_ROUTED";
    public static final String MODEL_PICK = "MODEL_PICK";
    public static final String MODEL_OUT_OF_CANDIDATES = "MODEL_OUT_OF_CANDIDATES";
    public static final String PATIENT_CONFIRM = "PATIENT_CONFIRM";
    public static final String CARRIED = "CARRIED";

    public RouteDecision {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public boolean certain() {
        return certainty == Certainty.CERTAIN;
    }
}
