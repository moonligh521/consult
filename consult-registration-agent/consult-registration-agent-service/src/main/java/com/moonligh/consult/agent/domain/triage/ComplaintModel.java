package com.moonligh.consult.agent.domain.triage;

import java.util.List;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.safety.ModelStatus;

/**
 * 主诉模型：一次调用同时给出病名归约（N4）和挂科建议（N6）。只在规则定不了时调用。
 */
public interface ComplaintModel {

    ComplaintReply interpret(
            String description,
            Integer age,
            Gender gender,
            List<String> allowTerms,
            List<String> candidates);

    /** 单测或缺实现时：一律不可用，走兜底。 */
    static ComplaintModel unavailable() {
        return (description, age, gender, allowTerms, candidates) ->
                ComplaintReply.failed(ModelStatus.UNAVAILABLE, 0, null, null);
    }
}
