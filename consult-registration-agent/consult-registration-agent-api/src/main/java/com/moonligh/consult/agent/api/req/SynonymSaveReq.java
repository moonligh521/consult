package com.moonligh.consult.agent.api.req;

import java.util.List;

/**
 * 新建或修改一条同义归约规则。保存后一律是 DRAFT，只记影子，审核后才参与决策。
 */
public record SynonymSaveReq(
        /** 规则编号，例如 SYN-HTN-003。 */
        String ruleId,
        /** 归约到的病名，必须是目录里的病名词。 */
        String target,
        /** 每组至少命中一个词，所有组都命中才算。 */
        List<List<String>> all,
        /** 命中任一即不算。 */
        List<String> none,
        /** 可选，只允许指标加数字写法。 */
        String pattern) {
}
