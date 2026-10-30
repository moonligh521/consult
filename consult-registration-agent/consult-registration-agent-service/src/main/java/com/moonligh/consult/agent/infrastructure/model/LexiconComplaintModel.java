package com.moonligh.consult.agent.infrastructure.model;

import java.util.ArrayList;
import java.util.List;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.InformalLexicon;
import com.moonligh.consult.agent.domain.triage.CanonResult;
import com.moonligh.consult.agent.domain.triage.CanonTerm;
import com.moonligh.consult.agent.domain.triage.ComplaintCanon;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;
import com.moonligh.consult.agent.domain.triage.RouteDecision;
import com.moonligh.consult.agent.domain.triage.RouteInput;
import com.moonligh.consult.framework.model.ModelStatus;

/**
 * 主诉模型槽的本地实现：把拼音/英文/方言换成目录词，再交给规则挂科。
 * 解不出或解完仍不确定，报 UNAVAILABLE，让级联里的大模型接着试。
 */
public class LexiconComplaintModel implements ComplaintModel {

    public static final String PROMPT_VERSION = "lexicon-v1";
    public static final String MODEL_NAME = "informal-lexicon";

    private final DepartmentCatalog catalog;
    private final DepartmentRouter router;
    private final InformalLexicon lexicon;
    private final ComplaintCanon canon;

    public LexiconComplaintModel(DepartmentCatalog catalog, DepartmentRouter router, InformalLexicon lexicon) {
        this.catalog = catalog;
        this.router = router;
        this.lexicon = lexicon;
        this.canon = new ComplaintCanon(catalog);
    }

    @Override
    public ComplaintReply interpret(
            String description,
            Integer age,
            Gender gender,
            List<String> allowTerms,
            List<String> candidates) {
        InformalLexicon.Rewrite rewrite = lexicon.rewrite(description);
        if (!rewrite.hit()) {
            return unavailable();
        }
        DepartmentSnapshot snapshot = catalog.snapshot();
        CanonResult decoded = canon.rule(rewrite.rewritten(), snapshot);
        RouteDecision routed = router.route(new RouteInput(rewrite.rewritten(), decoded, age, gender), snapshot);
        if (!usable(routed, candidates)) {
            return unavailable();
        }
        String evidence = originalEvidence(description, rewrite, routed.advice().evidence());
        if (evidence == null) {
            return unavailable();
        }
        List<ComplaintReply.ModelTerm> terms = new ArrayList<>();
        for (CanonTerm term : decoded.terms()) {
            if (allowTerms != null && !allowTerms.contains(term.term())) {
                continue;
            }
            if (!snapshot.isDisease(term.term())) {
                continue;
            }
            String termEvidence = description.contains(term.term())
                    ? term.term()
                    : originalEvidence(description, rewrite, term.term());
            if (termEvidence == null) {
                continue;
            }
            terms.add(new ComplaintReply.ModelTerm(term.term(), termEvidence));
        }
        return new ComplaintReply(
                ModelStatus.SUCCESS,
                terms,
                routed.advice().specialty(),
                evidence,
                0,
                false,
                PROMPT_VERSION,
                MODEL_NAME);
    }

    private static boolean usable(RouteDecision routed, List<String> candidates) {
        if (routed == null || !routed.certain()) {
            return false;
        }
        if (RouteDecision.NO_HIT_SHORT.equals(routed.reason())
                || RouteDecision.UNCERTAIN_MARKER.equals(routed.reason())) {
            return false;
        }
        String specialty = routed.advice() == null ? null : routed.advice().specialty();
        if (specialty == null || specialty.isBlank()) {
            return false;
        }
        return candidates == null || candidates.isEmpty() || candidates.contains(specialty);
    }

    private static String originalEvidence(
            String description,
            InformalLexicon.Rewrite rewrite,
            String routedEvidence) {
        if (description == null || rewrite == null || routedEvidence == null || routedEvidence.isBlank()) {
            return null;
        }
        for (InformalLexicon.Match match : rewrite.matches()) {
            if ((match.target().contains(routedEvidence) || routedEvidence.contains(match.target()))
                    && description.contains(match.surface())) {
                return match.surface();
            }
        }
        return null;
    }

    private static ComplaintReply unavailable() {
        return ComplaintReply.failed(ModelStatus.UNAVAILABLE, 0, PROMPT_VERSION, MODEL_NAME);
    }
}
