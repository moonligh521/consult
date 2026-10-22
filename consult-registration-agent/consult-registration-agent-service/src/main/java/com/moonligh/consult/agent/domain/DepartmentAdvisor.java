package com.moonligh.consult.agent.domain;

import java.util.Optional;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 关键词对不上时的科室补漏。只能建议已开通科室，不能改安全状态。
 */
public interface DepartmentAdvisor {

    /** 关闭时一律空，走目录兜底全科。 */
    static DepartmentAdvisor disabled() {
        return (description, age, gender) -> Optional.empty();
    }

    /**
     * 建议科室。空表示继续用快路径兜底。
     * evidence 必须是原话子串，没有则空。
     */
    Optional<DepartmentRouter.Advice> suggest(String description, Integer age, Gender gender);
}
