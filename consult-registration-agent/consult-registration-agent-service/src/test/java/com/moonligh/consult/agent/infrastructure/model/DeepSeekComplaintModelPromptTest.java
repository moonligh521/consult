package com.moonligh.consult.agent.infrastructure.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 主诉提示词：症状不当病名，能定科不选全科；不合格病名在模型层就拦下。
 */
class DeepSeekComplaintModelPromptTest {

    @Test
    void promptForbidsSymptomAsDiseaseAndAvoidsGeneralWhenClear() {
        AtomicReference<String> system = new AtomicReference<>();
        ModelGateway gateway = gatewayReturning(system,
                "{\"terms\":[],\"specialty\":\"呼吸内科\",\"specialtyEvidence\":\"kesou\"}");
        DeepSeekComplaintModel model = new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed());
        ComplaintReply reply = model.interpret("kesou三天", 32, Gender.FEMALE, List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals("complaint-v3.1", DeepSeekComplaintModel.PROMPT_VERSION);
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertTrue(system.get().contains("不是病名"));
        assertTrue(system.get().contains("不要选全科"));
        assertTrue(system.get().contains("kesou"));
        assertTrue(system.get().contains("脑壳痛"));
        assertTrue(system.get().contains("咳漱"));
        assertTrue(system.get().contains("{\"term\""));
        assertTrue(system.get().contains("方言") || system.get().contains("英文"));
        assertEquals("complaint-v3.1", reply.promptVersion());
    }

    @Test
    void illegalTermsAreStrippedAndSpecialtyKept() {
        ComplaintReply sneaked = new ComplaintReply(
                ModelStatus.SUCCESS,
                List.of(new ComplaintReply.ModelTerm("咳嗽", "kesou")),
                "呼吸内科",
                "kesou",
                1,
                false,
                "complaint-v3.1",
                "stub");
        assertFalse(DeepSeekComplaintModel.termsAllowed(sneaked, List.of("哮喘")));
        AtomicReference<String> system = new AtomicReference<>();
        ModelGateway gateway = gatewayReturning(system,
                "{\"terms\":[{\"term\":\"咳嗽\",\"evidence\":\"kesou\"}],\"specialty\":\"呼吸内科\",\"specialtyEvidence\":\"kesou\"}");
        DeepSeekComplaintModel model = new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed());
        ComplaintReply reply = model.interpret("kesou三天", 32, Gender.FEMALE, List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertTrue(reply.terms().isEmpty());
        assertEquals("呼吸内科", reply.specialty());
    }

    @Test
    void timeoutIsRetriedOnceThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        ModelGateway gateway = new ModelGateway(new ModelProperties(), null, new SimpleMeterRegistry()) {
            @Override
            public ModelReply chat(ModelPurpose purpose, String systemPrompt, String user, String cacheKey, int maxTokens) {
                if (calls.incrementAndGet() == 1) {
                    return new ModelReply(ModelStatus.TIMEOUT, "", 12, "stub", false);
                }
                return new ModelReply(ModelStatus.SUCCESS,
                        "{\"terms\":[],\"specialty\":\"呼吸内科\",\"specialtyEvidence\":\"kesou\"}",
                        1, "stub", false);
            }
        };
        DeepSeekComplaintModel model = new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed());
        ComplaintReply reply = model.interpret("kesou三天", 32, Gender.FEMALE, List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals(2, calls.get());
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertEquals("呼吸内科", reply.specialty());
    }

    @Test
    void timeoutIsRetriedAtMostOnce() {
        AtomicInteger calls = new AtomicInteger();
        ModelGateway gateway = new ModelGateway(new ModelProperties(), null, new SimpleMeterRegistry()) {
            @Override
            public ModelReply chat(ModelPurpose purpose, String systemPrompt, String user, String cacheKey, int maxTokens) {
                calls.incrementAndGet();
                return new ModelReply(ModelStatus.TIMEOUT, "", 12, "stub", false);
            }
        };
        DeepSeekComplaintModel model = new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed());
        ComplaintReply reply = model.interpret("kesou三天", 32, Gender.FEMALE, List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals(2, calls.get());
        assertEquals(ModelStatus.TIMEOUT, reply.status());
    }

    @Test
    void missingTermEvidenceDoesNotDiscardSpecialty() {
        AtomicReference<String> system = new AtomicReference<>();
        ModelGateway gateway = gatewayReturning(system,
                "{\"terms\":[{\"term\":\"哮喘\"}],\"specialty\":\"呼吸内科\"}");
        DeepSeekComplaintModel model = new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed());
        ComplaintReply reply = model.interpret("kesou三天", 32, Gender.FEMALE, List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertTrue(reply.terms().isEmpty());
        assertEquals("呼吸内科", reply.specialty());
    }

    @Test
    void missingTermsArrayStillKeepsSpecialty() {
        AtomicReference<String> system = new AtomicReference<>();
        ModelGateway gateway = gatewayReturning(system,
                "{\"specialty\":\"呼吸内科\",\"specialtyEvidence\":\"kesou\"}");
        DeepSeekComplaintModel model = new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed());
        ComplaintReply reply = model.interpret("kesou三天", 32, Gender.FEMALE, List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertTrue(reply.terms().isEmpty());
        assertEquals("呼吸内科", reply.specialty());
        assertEquals("kesou", reply.specialtyEvidence());
    }

    @Test
    void whollyEmptyOutputIsRetriedAndOnlyUsableOutputIsCached() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger remembers = new AtomicInteger();
        ModelGateway gateway = new ModelGateway(new ModelProperties(), null, new SimpleMeterRegistry()) {
            @Override
            public ModelReply chat(ModelPurpose purpose, String systemPrompt, String user, String cacheKey, int maxTokens) {
                if (calls.incrementAndGet() == 1) {
                    return new ModelReply(ModelStatus.SUCCESS,
                            "{\"terms\":[],\"specialty\":\"\",\"specialtyEvidence\":\"\"}",
                            1, "stub", false);
                }
                return new ModelReply(ModelStatus.SUCCESS,
                        "{\"terms\":[],\"specialty\":\"呼吸内科\",\"specialtyEvidence\":\"kesou\"}",
                        1, "stub", false);
            }

            @Override
            public void remember(String cacheKey, String content) {
                remembers.incrementAndGet();
            }
        };
        ComplaintReply reply = new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed())
                .interpret("kesou三天", 32, Gender.FEMALE, List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals(2, calls.get());
        assertEquals(1, remembers.get());
        assertEquals(ModelStatus.SUCCESS, reply.status());
    }

    @Test
    void termLimitIsAppliedAfterIllegalTermsAreRemoved() {
        AtomicReference<String> system = new AtomicReference<>();
        ModelGateway gateway = gatewayReturning(system,
                """
                {"terms":[
                  {"term":"咳嗽","evidence":"kesou"},
                  {"term":"头痛","evidence":"toutong"},
                  {"term":"哮喘","evidence":"xiaochuan"}
                ],"specialty":"呼吸内科","specialtyEvidence":"kesou"}
                """);
        ComplaintReply reply = new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed())
                .interpret("kesou toutong xiaochuan", 32, Gender.FEMALE,
                        List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertEquals(List.of(new ComplaintReply.ModelTerm("哮喘", "xiaochuan")), reply.terms());
    }

    @Test
    void complaintCallUsesTwoThousandMaxTokens() {
        AtomicInteger tokens = new AtomicInteger();
        ModelGateway gateway = new ModelGateway(new ModelProperties(), null, new SimpleMeterRegistry()) {
            @Override
            public ModelReply chat(ModelPurpose purpose, String systemPrompt, String user, String cacheKey, int maxTokens) {
                tokens.set(maxTokens);
                return new ModelReply(ModelStatus.SUCCESS,
                        "{\"terms\":[],\"specialty\":\"呼吸内科\",\"specialtyEvidence\":\"kesou\"}",
                        1, "stub", false);
            }
        };
        new DeepSeekComplaintModel(gateway, MemoryDepartmentCatalog.seed())
                .interpret("kesou三天", 32, Gender.FEMALE, List.of("哮喘"), List.of("呼吸内科", "全科"));
        assertEquals(2000, tokens.get());
    }

    private static ModelGateway gatewayReturning(AtomicReference<String> system, String content) {
        return new ModelGateway(new ModelProperties(), null, new SimpleMeterRegistry()) {
            @Override
            public ModelReply chat(ModelPurpose purpose, String systemPrompt, String user, String cacheKey, int maxTokens) {
                system.set(systemPrompt);
                return new ModelReply(ModelStatus.SUCCESS, content, 1, "stub", false);
            }
        };
    }
}
