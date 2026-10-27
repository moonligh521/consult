package com.moonligh.consult.agent.infrastructure.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.framework.model.ModelGateway;
import com.moonligh.consult.framework.model.ModelProperties;
import com.moonligh.consult.framework.model.ModelPurpose;
import com.moonligh.consult.framework.model.ModelReply;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.framework.model.ModelStatus;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 主诉模型提示词：拼音/口误走模型，证据仍要求原文子串。
 */
class DeepSeekComplaintModelPromptTest {

    @Test
    void promptVersionAndNoiseInstructions() {
        AtomicReference<String> system = new AtomicReference<>();
        ModelGateway gateway = new ModelGateway(new ModelProperties(), null, new SimpleMeterRegistry()) {
            @Override
            public ModelReply chat(ModelPurpose purpose, String systemPrompt, String user, String cacheKey, int maxTokens) {
                system.set(systemPrompt);
                return new ModelReply(ModelStatus.SUCCESS,
                        "{\"terms\":[{\"term\":\"哮喘\",\"evidence\":\"kesou\"}],\"specialty\":\"呼吸内科\",\"specialtyEvidence\":\"kesou\"}",
                        1, "stub", false);
            }
        };
        DeepSeekComplaintModel model = new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed());
        ComplaintReply reply = model.interpret("kesou三天", 32, Gender.FEMALE, List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals("complaint-v2", DeepSeekComplaintModel.PROMPT_VERSION);
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertTrue(system.get().contains("拼音"));
        assertTrue(system.get().contains("口误") || system.get().contains("同音"));
        assertTrue(system.get().contains("原文"));
        assertEquals("complaint-v2", reply.promptVersion());
    }
}
