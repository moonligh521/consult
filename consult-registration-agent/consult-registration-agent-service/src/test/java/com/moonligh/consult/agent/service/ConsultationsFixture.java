package com.moonligh.consult.agent.service;

import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.InMemoryConsultationRepository;
import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.SymptomExtractor;
import com.moonligh.consult.agent.domain.patient.MemoryPatientStore;
import com.moonligh.consult.agent.domain.safety.CatalogLoader;
import com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor;
import com.moonligh.consult.agent.domain.safety.DraftMode;
import com.moonligh.consult.agent.domain.safety.InMemorySafetyAuditRecorder;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.safety.SafetyGate;
import com.moonligh.consult.agent.domain.safety.SafetyMetrics;
import com.moonligh.consult.agent.domain.safety.SignalExtractor;

/**
 * 测试用装配：内存仓储 + 默认急症目录 + 关闭模型。
 * 不连 MySQL，方便单测快速跑。
 */
public final class ConsultationsFixture {

    public final InMemoryConsultationRepository repository;
    public final InMemorySafetyAuditRecorder audits;
    public final SafetyCatalog catalog;
    public final SafetyGate gate;
    public final SafetyMetrics metrics;
    public final MemoryPatientStore patients;
    public final ConsultationService service;

    private ConsultationsFixture(
            InMemoryConsultationRepository repository,
            InMemorySafetyAuditRecorder audits,
            SafetyCatalog catalog,
            SafetyGate gate,
            SafetyMetrics metrics) {
        this.repository = repository;
        this.audits = audits;
        this.catalog = catalog;
        this.gate = gate;
        this.metrics = metrics;
        this.patients = new MemoryPatientStore();
        this.service = new ConsultationService(
                repository,
                gate,
                new SlotParser(),
                new SymptomExtractor(),
                new DepartmentRouter(),
                metrics,
                new InquiryProperties(),
                patients);
    }

    /** 生产同款：DRAFT 规则只记影子。 */
    public static ConsultationsFixture shadow() {
        return of(CatalogLoader.loadDefault(DraftMode.SHADOW), new DisabledSignalExtractor());
    }

    /** 测试专用：DRAFT 也参与决策。 */
    public static ConsultationsFixture enforce() {
        return of(CatalogLoader.loadDefault(DraftMode.ENFORCE), new DisabledSignalExtractor());
    }

    public static ConsultationsFixture of(SafetyCatalog catalog, SignalExtractor extractor) {
        return of(catalog, new RuleSafetyEngine(), extractor);
    }

    public static ConsultationsFixture of(SafetyCatalog catalog, RuleSafetyEngine engine, SignalExtractor extractor) {
        InMemorySafetyAuditRecorder audits = new InMemorySafetyAuditRecorder();
        InMemoryConsultationRepository repository = new InMemoryConsultationRepository(new java.util.concurrent.ConcurrentHashMap<>(), audits);
        SafetyGate gate = new SafetyGate(catalog, engine, extractor);
        return new ConsultationsFixture(repository, audits, catalog, gate, new SafetyMetrics());
    }
}
