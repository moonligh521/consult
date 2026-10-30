package com.moonligh.consult.agent.infrastructure.model;

import java.util.List;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;
import com.moonligh.consult.framework.model.ModelStatus;

/**
 * 主诉模型级联：词表能定就定，否则再打大模型。词表未命中不算一次大模型失败。
 */
public class CascadeComplaintModel implements ComplaintModel {

    private final ComplaintModel lexicon;
    private final ComplaintModel llm;

    public CascadeComplaintModel(ComplaintModel lexicon, ComplaintModel llm) {
        this.lexicon = lexicon;
        this.llm = llm == null ? ComplaintModel.unavailable() : llm;
    }

    @Override
    public ComplaintReply interpret(
            String description,
            Integer age,
            Gender gender,
            List<String> allowTerms,
            List<String> candidates) {
        ComplaintReply local = lexicon.interpret(description, age, gender, allowTerms, candidates);
        if (local != null && local.status() == ModelStatus.SUCCESS) {
            return local;
        }
        return llm.interpret(description, age, gender, allowTerms, candidates);
    }
}
