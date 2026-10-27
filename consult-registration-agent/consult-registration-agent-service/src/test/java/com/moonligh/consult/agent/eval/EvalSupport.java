package com.moonligh.consult.agent.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.DoctorAssigner;
import com.moonligh.consult.agent.domain.InMemoryConsultationRepository;
import com.moonligh.consult.agent.domain.RegistrationAgent;
import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.SymptomExtractor;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.agent.domain.patient.MemoryPatientStore;
import com.moonligh.consult.agent.domain.safety.CatalogLoader;
import com.moonligh.consult.agent.domain.safety.DraftMode;
import com.moonligh.consult.agent.domain.safety.InMemorySafetyAuditRecorder;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.domain.safety.SafetyGate;
import com.moonligh.consult.agent.domain.safety.SafetyMetrics;
import com.moonligh.consult.agent.domain.safety.SignalExtractor;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 评测用例和证据的读写。评分提示不进患者会话。
 */
public final class EvalSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private EvalSupport() {
    }

    /** 从模块目录往上找到仓库根。 */
    public static Path repoRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && cursor != null; i++) {
            if (Files.isDirectory(cursor.resolve("docs").resolve("测试").resolve("评测"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("找不到 docs/测试/评测");
    }

    public static List<Map<String, String>> readCsv(String fileName) throws IOException {
        Path path = repoRoot().resolve("docs").resolve("测试").resolve("评测").resolve(fileName);
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> header = parseLine(stripBom(lines.get(0)));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> cells = parseLine(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                row.put(header.get(c), c < cells.size() ? cells.get(c) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    public static void replaceSuite(String suite, List<Map<String, Object>> rows) throws IOException {
        Path dir = Path.of("target", "eval");
        Files.createDirectories(dir);
        JSON.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(suite + ".json").toFile(), rows);
    }

    public static int errorCode(Runnable action) {
        try {
            action.run();
            return 0;
        } catch (BusinessException error) {
            return error.getCode();
        }
    }

    public static Map<String, Object> evidence(String caseId, ConsultationRes view, Integer errorCode, boolean modelCalled) {
        ConsultationCardRes card = view.card();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("case_id", caseId);
        row.put("status_code", view.status().getCode());
        row.put("error_code", errorCode == null || errorCode == 0 ? "" : errorCode);
        row.put("prompt", view.prompt() == null ? "" : view.prompt());
        row.put("department", card == null || card.department() == null ? "" : String.valueOf(card.department()));
        row.put("specialty", card == null || card.specialty() == null ? "" : card.specialty());
        row.put("evidence", card == null || card.departmentEvidence() == null ? "" : card.departmentEvidence());
        row.put("source", card == null || card.departmentSource() == null ? "" : card.departmentSource());
        row.put("model_called", modelCalled);
        row.put("model_field_names", List.of());
        row.put("order_count", view.order() == null ? 0 : 1);
        row.put("input", "");
        return row;
    }

    public static ConsultationService service(SignalExtractor extractor, RuleSafetyEngine engine, InquiryProperties inquiry,
            ComplaintModel model, DoctorAssigner assigner) {
        InMemorySafetyAuditRecorder audits = new InMemorySafetyAuditRecorder();
        InMemoryConsultationRepository repository = new InMemoryConsultationRepository(new ConcurrentHashMap<>(), audits);
        SafetyGate gate = new SafetyGate(CatalogLoader.loadDefault(DraftMode.SHADOW), engine, extractor);
        DepartmentRouter router = new DepartmentRouter();
        ComplaintModel used = model == null ? ComplaintModel.unavailable() : model;
        RegistrationAgent agent = new RegistrationAgent(MemoryDepartmentCatalog.seed(), router, used);
        return new ConsultationService(
                repository,
                gate,
                new SlotParser(),
                new SymptomExtractor(),
                agent,
                new SafetyMetrics(),
                inquiry == null ? new InquiryProperties() : inquiry,
                new MemoryPatientStore(),
                assigner);
    }

    public static boolean containsAny(String prompt, String forbid) {
        if (forbid == null || forbid.isBlank() || prompt == null) {
            return false;
        }
        for (String part : forbid.split("\\|")) {
            if (!part.isEmpty() && prompt.contains(part)) {
                return true;
            }
        }
        return false;
    }

    public static Integer codeOf(ErrorCode error) {
        return error.getCode();
    }

    private static String stripBom(String line) {
        if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    static List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quote) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quote = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quote = true;
            } else if (ch == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    /** 测试里确认 JSON 还能读回来。 */
    public static List<Map<String, Object>> readSuite(String suite) throws IOException {
        Path file = Path.of("target", "eval").resolve(suite + ".json");
        if (!Files.exists(file)) {
            return List.of();
        }
        return JSON.readValue(file.toFile(), new TypeReference<>() {
        });
    }
}
