package com.moonligh.consult.agent.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moonligh.consult.agent.domain.judge.TurnJudge;
import com.moonligh.consult.agent.infrastructure.judge.JudgeModelPort;
import com.moonligh.consult.agent.infrastructure.judge.LangfuseTurnPort;
import com.moonligh.consult.framework.model.ModelProperties;
import com.moonligh.consult.framework.observability.ObservabilityProperties;

/**
 * 旁路跑一轮：拉 agent.turn、抽样、打分、写回 Score。失败只记条数，不抛到挂号线程。
 */
public class TurnJudgeService {

    private static final Logger log = LoggerFactory.getLogger(TurnJudgeService.class);

    private final ObservabilityProperties observability;
    private final ModelProperties model;
    private final LangfuseTurnPort langfuse;
    private final JudgeModelPort judgeModel;
    private final Map<String, String> prompts;

    public TurnJudgeService(
            ObservabilityProperties observability,
            ModelProperties model,
            LangfuseTurnPort langfuse,
            JudgeModelPort judgeModel) {
        this.observability = observability;
        this.model = model;
        this.langfuse = langfuse;
        this.judgeModel = judgeModel;
        this.prompts = loadPrompts();
    }

    public Summary runOnce() {
        return runOnce(null, false);
    }

    public Summary runOnce(List<TurnJudge.Observation> fixture, boolean apply) {
        ObservabilityProperties.Judge judge = observability.getJudge();
        TurnJudge.Phase phase = TurnJudge.resolvePhase(judge.getPhase());
        double rate = TurnJudge.resolveRate(phase, null);
        Summary summary = new Summary(phase.name().toLowerCase(), rate);
        if (!judge.isEnabled()) {
            return summary;
        }
        if (!applyReady() && fixture == null) {
            log.warn("turn judge skip: missing langfuse key or model key");
            return summary;
        }
        List<TurnJudge.Observation> observations = fixture != null
                ? fixture
                : langfuse.fetchTurns(judge.getSinceMinutes(), judge.getMaxObservations());
        for (TurnJudge.Observation obs : observations) {
            summary.scanned++;
            String skip = TurnJudge.skipReason(obs);
            if (skip != null) {
                summary.skipped++;
                continue;
            }
            if (!TurnJudge.sampleHit(obs.id(), rate)) {
                continue;
            }
            summary.sampled++;
            try {
                List<TurnJudge.Score> scores = TurnJudge.evaluate(obs, prompts, judgeModel::complete);
                for (TurnJudge.Score score : scores) {
                    summary.scored++;
                    summary.scores.add(score);
                    if (fixture == null || apply) {
                        langfuse.postScore(score);
                    }
                }
            } catch (RuntimeException error) {
                summary.errors++;
                log.warn("turn judge failed id={} type={}", obs.id(), error.getClass().getSimpleName());
            }
        }
        log.info("turn judge phase={} rate={} scanned={} skipped={} sampled={} scored={} errors={}",
                summary.phase, summary.sampleRate, summary.scanned, summary.skipped,
                summary.sampled, summary.scored, summary.errors);
        return summary;
    }

    private boolean applyReady() {
        ObservabilityProperties.Langfuse langfuseProps = observability.getLangfuse();
        return notBlank(langfuseProps.getPublicUrl())
                && notBlank(langfuseProps.getPublicKey())
                && notBlank(langfuseProps.getSecretKey())
                && notBlank(model.getApiKey());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static Map<String, String> loadPrompts() {
        Map<String, String> prompts = new LinkedHashMap<>();
        prompts.put(TurnJudge.PROMPT_GROUNDED, readClasspath("judge/prompt_grounded.txt"));
        prompts.put(TurnJudge.VAGUE_LOOP, readClasspath("judge/vague_loop.txt"));
        return Map.copyOf(prompts);
    }

    private static String readClasspath(String path) {
        try (InputStream in = TurnJudgeService.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("read " + path, error);
        }
    }

    public static final class Summary {
        public final String phase;
        public final double sampleRate;
        public int scanned;
        public int skipped;
        public int sampled;
        public int scored;
        public int errors;
        public final List<TurnJudge.Score> scores = new ArrayList<>();

        Summary(String phase, double sampleRate) {
            this.phase = phase;
            this.sampleRate = sampleRate;
        }
    }
}
