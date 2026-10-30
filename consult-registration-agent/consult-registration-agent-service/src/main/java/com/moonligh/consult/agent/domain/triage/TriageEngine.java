package com.moonligh.consult.agent.domain.triage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.CompactText;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.SpokenNormalizer;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.framework.model.ModelStatus;

/**
 * N4 归约 + N6 挂科：规则能定就定；定不了合并成一次主诉模型调用；模型也不行就兜底。
 * <ul>
 *   <li>规则 CERTAIN 时 {@code needModel=false}，禁止调模型。</li>
 *   <li>N4 失败（resolved=false）时 N6 不信孤立症状，只落全科。</li>
 *   <li>模型给了病名，挂科仍由目录规则映射（规则路由模型输出）。</li>
 *   <li>非法病名丢掉；挂科合格仍可用。两者都没有才 FALLBACK。</li>
 * </ul>
 */
public class TriageEngine {

    public static final String MODEL_NONE = "MODEL_NONE";
    public static final String MODEL_TERMS = "MODEL_TERMS";
    public static final String SKIPPED_BY_ROUTE = "SKIPPED_BY_ROUTE";

    private final DepartmentCatalog catalog;
    private final ComplaintCanon canon;
    private final DepartmentRouter router;
    private final ComplaintModel model;

    public TriageEngine(DepartmentCatalog catalog, DepartmentRouter router, ComplaintModel model) {
        this.catalog = catalog;
        this.canon = new ComplaintCanon(catalog);
        this.router = router;
        this.model = model == null ? ComplaintModel.unavailable() : model;
    }

    public ComplaintCanon canon() {
        return canon;
    }

    public TriageRun run(TriageInput in) {
        long started = System.nanoTime();
        DepartmentSnapshot snapshot = catalog.snapshot();
        String runId = UUID.randomUUID().toString();
        String description = in.description() == null ? "" : in.description();
        String haystack = SpokenNormalizer.forMatch(description, snapshot);

        CanonResult canonRule = canon.rule(description, snapshot);
        RouteDecision routeRule = router.route(new RouteInput(description, canonRule, in.age(), in.gender()), snapshot);
        long ruleMs = elapsed(started);

        boolean routeFixed = RouteDecision.UNCERTAIN_MARKER.equals(routeRule.reason())
                || RouteDecision.OVERRIDE.equals(routeRule.reason());
        boolean needModel = !routeRule.certain() || (!canonRule.certain() && !routeFixed);
        List<String> candidates = routeRule.certain() ? withGeneral(snapshot, snapshot.enabledSpecialtyNames()) : routeRule.candidates();
        ComplaintReply reply = needModel
                ? model.interpret(description, in.age(), in.gender(), snapshot.allowedDiseaseTerms(), candidates)
                : ComplaintReply.notCalled();
        ModelStatus status = reply.status();
        List<CanonTerm> modelTerms = List.of();
        String modelSpecialty = null;
        if (status == ModelStatus.SUCCESS) {
            modelTerms = validTerms(reply, description, haystack, snapshot);
            modelSpecialty = validSpecialty(reply, description, candidates);
            if (modelTerms == null) {
                modelTerms = List.of();
            }
            if (modelTerms.isEmpty() && modelSpecialty == null
                    && (reply.specialty() == null || reply.specialty().isBlank())) {
                status = ModelStatus.INVALID_OUTPUT;
            }
        }

        CanonResult canonFinal;
        if (canonRule.certain()) {
            canonFinal = canonRule;
        } else if (routeFixed) {
            canonFinal = canonRule.with(List.of(), DecisionSource.RULE, SKIPPED_BY_ROUTE, true);
        } else if (status == ModelStatus.SUCCESS) {
            canonFinal = canonRule.with(modelTerms, DecisionSource.MODEL, modelTerms.isEmpty() ? MODEL_NONE : MODEL_TERMS, true);
        } else {
            canonFinal = canonRule.with(List.of(), DecisionSource.FALLBACK, "MODEL_" + status.name(), false);
        }

        RouteDecision routeFinal;
        if (routeRule.certain()) {
            routeFinal = routeRule;
            if (!canonRule.certain() && canonFinal.source() == DecisionSource.MODEL && !canonFinal.terms().isEmpty()) {
                RouteDecision again = router.route(new RouteInput(description, canonFinal, in.age(), in.gender()), snapshot);
                if (again.certain() && !sameSpecialty(again.advice(), routeRule.advice())) {
                    routeFinal = new RouteDecision(again.advice(), Certainty.CERTAIN, DecisionSource.MODEL,
                            RouteDecision.MODEL_TERM_ROUTED, routeRule.candidates());
                }
            }
        } else {
            routeFinal = null;
            if (canonFinal.source() == DecisionSource.MODEL && !canonFinal.terms().isEmpty()) {
                RouteDecision again = router.route(new RouteInput(description, canonFinal, in.age(), in.gender()), snapshot);
                if (again.certain()) {
                    routeFinal = new RouteDecision(again.advice(), Certainty.UNCERTAIN, DecisionSource.MODEL,
                            RouteDecision.MODEL_TERM_ROUTED, routeRule.candidates());
                }
            }
            if (routeFinal == null && status == ModelStatus.SUCCESS && modelSpecialty != null) {
                DepartmentRouter.Advice picked = DepartmentRouter.adviceOf(
                        snapshot, snapshot.refOfSpecialty(modelSpecialty), evidenceText(reply.specialtyEvidence(), description));
                routeFinal = new RouteDecision(picked, Certainty.UNCERTAIN, DecisionSource.MODEL,
                        RouteDecision.MODEL_PICK, routeRule.candidates());
            }
            if (routeFinal == null) {
                String reason = status == ModelStatus.SUCCESS
                        ? RouteDecision.MODEL_OUT_OF_CANDIDATES
                        : "MODEL_" + status.name();
                routeFinal = new RouteDecision(DepartmentRouter.fallbackOf(snapshot), Certainty.UNCERTAIN,
                        DecisionSource.FALLBACK, reason, routeRule.candidates());
            }
        }

        String fingerprint = fingerprint(haystack, in.age(), in.gender());
        String catalogVersion = snapshot.version();
        TriageResult result = new TriageResult(runId, catalogVersion, reply.promptVersion(), reply.modelName(),
                fingerprint, canonFinal, routeFinal, status, reply.latencyMs(), reply.cached());

        long total = elapsed(started);
        List<MatchDecision> decisions = new ArrayList<>();
        decisions.add(canonDecision(runId, canonRule, canonFinal, needModel ? total : ruleMs)
                .withModel(needModel ? status : null, needModel ? reply.cached() : null,
                        needModel ? reply.latencyMs() : null, reply.promptVersion(), reply.modelName())
                .withVersion(catalogVersion, fingerprint, canonRule.shadowRuleIds()));
        decisions.add(routeDecision(runId, routeRule, routeFinal, needModel ? total : ruleMs)
                .withModel(needModel ? status : null, needModel ? reply.cached() : null,
                        needModel ? reply.latencyMs() : null, reply.promptVersion(), reply.modelName())
                .withVersion(catalogVersion, fingerprint, List.of()));
        return new TriageRun(result, decisions);
    }

    /** 模型病名：必须在允许列表、证据是原话子串。不合格的条丢掉，不连坐挂科。 */
    private static List<CanonTerm> validTerms(
            ComplaintReply reply,
            String description,
            String haystack,
            DepartmentSnapshot snapshot) {
        List<CanonTerm> terms = new ArrayList<>();
        for (ComplaintReply.ModelTerm item : reply.terms()) {
            if (terms.size() >= ComplaintCanon.MAX_TERMS) {
                break;
            }
            if (item.term() == null || !snapshot.isDisease(item.term())) {
                continue;
            }
            if (item.evidence() == null || item.evidence().isBlank() || !description.contains(item.evidence())) {
                continue;
            }
            String compact = SpokenNormalizer.forMatch(item.evidence(), snapshot);
            int at = haystack.indexOf(compact);
            Span span = at < 0 ? null : new Span(at, at + compact.length());
            boolean duplicate = terms.stream().anyMatch(existing -> existing.term().equals(item.term()));
            if (!duplicate) {
                terms.add(new CanonTerm(item.term(), CanonTerm.MODEL, span));
            }
        }
        return terms;
    }

    /** 模型挂科：必须在候选里，证据若给了必须是原话子串。不合格返回 null。 */
    private static String validSpecialty(ComplaintReply reply, String description, List<String> candidates) {
        String specialty = reply.specialty();
        if (specialty == null || specialty.isBlank() || !candidates.contains(specialty)) {
            return null;
        }
        String evidence = reply.specialtyEvidence();
        if (evidence != null && !evidence.isBlank() && !description.contains(evidence)) {
            return null;
        }
        return specialty;
    }

    private static boolean sameSpecialty(DepartmentRouter.Advice left, DepartmentRouter.Advice right) {
        return left.department() == right.department() && java.util.Objects.equals(left.specialty(), right.specialty());
    }

    private static String evidenceText(String evidence, String description) {
        return evidence != null && !evidence.isBlank() && description.contains(evidence) ? evidence : null;
    }

    private static List<String> withGeneral(DepartmentSnapshot snapshot, List<String> names) {
        List<String> list = new ArrayList<>(names);
        String general = snapshot.fallback().defaultSpecialty();
        if (!list.contains(general)) {
            list.add(general);
        }
        return list;
    }

    private static MatchDecision canonDecision(String runId, CanonResult rule, CanonResult result, long latencyMs) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("terms", result.termNames());
        List<String> history = new ArrayList<>();
        for (CanonTerm term : result.historyTerms()) {
            history.add(term.term());
        }
        output.put("historyTerms", history);
        output.put("ruleReason", rule.reason());
        output.put("resolved", result.resolved());
        List<Span> evidence = new ArrayList<>();
        for (CanonTerm term : result.terms()) {
            if (term.evidence() != null) {
                evidence.add(term.evidence());
            }
        }
        return MatchDecision.of(runId, TriageNode.N4_CANON, rule.certainty(), result.source(), result.reason(),
                output, evidence, latencyMs);
    }

    private static MatchDecision routeDecision(String runId, RouteDecision rule, RouteDecision result, long latencyMs) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("departmentCode", result.advice().departmentCode());
        output.put("specialty", result.advice().specialty());
        output.put("ruleReason", rule.reason());
        output.put("candidates", rule.candidates());
        return MatchDecision.of(runId, TriageNode.N6_DEPARTMENT, rule.certainty(), result.source(), result.reason(),
                output, List.of(), latencyMs);
    }

    /** sha256(紧凑原话|年龄段|性别)，同说法聚合与缓存用。 */
    public static String fingerprint(String compact, Integer age, Gender gender) {
        String raw = CompactText.of(compact) + "|" + ageBand(age) + "|" + (gender == null ? "" : gender.name());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public static String ageBand(Integer age) {
        if (age == null) {
            return "";
        }
        if (age < 14) {
            return "child";
        }
        return age < 60 ? "adult" : "senior";
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
