package com.moonligh.consult.agent.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.Demographics;
import com.moonligh.consult.agent.domain.safety.SafetyAssessment;
import com.moonligh.consult.agent.domain.triage.TriageInput;
import com.moonligh.consult.agent.domain.triage.TriageRun;

/**
 * 一次请求里模型结论的暂存：锁外在会话副本上预演一遍并记下，进锁后输入完全一致就直接复用。
 * 输入对不上（期间有并发改动）时锁内照常现算，结论不会用错。
 */
final class ModelMemo {

    private final Map<String, SafetyAssessment> safety = new HashMap<>();
    private final Map<TriageInput, TriageRun> triage = new HashMap<>();
    private boolean recording = true;

    boolean recording() {
        return recording;
    }

    /** 预演结束，转入复用。 */
    ModelMemo replay() {
        recording = false;
        return this;
    }

    SafetyAssessment safety(List<ConsultationMessage> transcript, Demographics demographics) {
        return safety.get(safetyKey(transcript, demographics));
    }

    void putSafety(List<ConsultationMessage> transcript, Demographics demographics, SafetyAssessment assessment) {
        safety.put(safetyKey(transcript, demographics), assessment);
    }

    TriageRun triage(TriageInput input) {
        return triage.get(input);
    }

    void putTriage(TriageInput input, TriageRun run) {
        triage.put(input, run);
    }

    /** 评估只看序号、来源、正文和年龄性别；messageId 和收到时间每次生成都不同，不进键。 */
    private static String safetyKey(List<ConsultationMessage> transcript, Demographics demographics) {
        StringBuilder key = new StringBuilder();
        key.append(demographics.age()).append('|').append(demographics.gender()).append('\n');
        for (ConsultationMessage message : transcript) {
            key.append(message.seq()).append('|').append(message.source()).append('|')
                    .append(message.text().length()).append(':').append(message.text()).append('\n');
        }
        return key.toString();
    }
}
