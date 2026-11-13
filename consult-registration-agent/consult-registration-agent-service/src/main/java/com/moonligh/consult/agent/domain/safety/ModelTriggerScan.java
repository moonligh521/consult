package com.moonligh.consult.agent.domain.safety;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.domain.CompactText;

/**
 * 安全模型的触发判定。规则没判急症、原话也没碰到这些组合，就算规则已能确定「无风险」，不调模型。
 * 匹配用 {@link CompactText}，与急症闸门一致，不做挂号用的错别字纠正。
 */
public final class ModelTriggerScan {

    public static final String LOCATION = "/safety/model-triggers.json";

    private final String version;
    private final List<Trigger> triggers;

    public ModelTriggerScan(String version, List<Trigger> triggers) {
        this.version = version;
        this.triggers = triggers == null ? List.of() : List.copyOf(triggers);
    }

    /** 从 classpath 读。文件坏了直接起不来。 */
    public static ModelTriggerScan load() {
        try (InputStream in = ModelTriggerScan.class.getResourceAsStream(LOCATION)) {
            if (in == null) {
                throw new IllegalStateException("找不到模型触发词表 " + LOCATION);
            }
            TriggerFile file = new ObjectMapper().readValue(in, TriggerFile.class);
            if (file.triggers() == null || file.triggers().isEmpty()) {
                throw new IllegalStateException("模型触发词表为空");
            }
            return new ModelTriggerScan(file.version(), file.triggers());
        } catch (IOException error) {
            throw new IllegalStateException("读取模型触发词表失败", error);
        }
    }

    public String version() {
        return version;
    }

    /** 全部原话拼起来扫，返回命中的触发编号。 */
    public List<String> scan(List<ConsultationMessage> transcript) {
        StringBuilder joined = new StringBuilder();
        if (transcript != null) {
            for (ConsultationMessage message : transcript) {
                if (message.text() != null) {
                    joined.append(CompactText.of(message.text()));
                }
            }
        }
        return scanText(joined.toString());
    }

    public List<String> scanText(String compact) {
        List<String> hits = new ArrayList<>();
        if (compact == null || compact.isEmpty()) {
            return hits;
        }
        for (Trigger trigger : triggers) {
            if (trigger.matches(compact)) {
                hits.add(trigger.id());
            }
        }
        return hits;
    }

    /** 一条触发：每组至少命中一个词。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trigger(String id, List<List<String>> all) {

        boolean matches(String compact) {
            if (all == null || all.isEmpty()) {
                return false;
            }
            for (List<String> group : all) {
                boolean hit = false;
                for (String word : group) {
                    if (!word.isEmpty() && compact.contains(word)) {
                        hit = true;
                        break;
                    }
                }
                if (!hit) {
                    return false;
                }
            }
            return true;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TriggerFile(String version, List<Trigger> triggers) {
    }
}
