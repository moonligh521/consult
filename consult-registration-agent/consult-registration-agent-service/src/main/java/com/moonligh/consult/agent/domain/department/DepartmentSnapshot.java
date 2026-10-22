package com.moonligh.consult.agent.domain.department;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 一次加载的匹配快照。科室、关键词、错别字、不确定标记同一版。
 */
public final class DepartmentSnapshot {

    private static final List<String> SAFETY_PHRASES = List.of("呼吸困难", "胸痛");

    private final String version;
    private final List<DepartmentRecord> enabled;
    private final Map<Integer, DepartmentRecord> enabledByCode;
    private final DepartmentRecord fallback;
    private final List<MatchTypo> typos;
    private final List<String> uncertainMarkers;
    private final List<String> symptomPhrases;

    public DepartmentSnapshot(String version, List<DepartmentRecord> departments) {
        this(version, departments, List.of(), List.of());
    }

    public DepartmentSnapshot(
            String version,
            List<DepartmentRecord> departments,
            List<MatchTypo> typos,
            List<String> uncertainMarkers) {
        this.version = version;
        List<DepartmentRecord> live = new ArrayList<>();
        Map<Integer, DepartmentRecord> byCode = new LinkedHashMap<>();
        DepartmentRecord foundFallback = null;
        for (DepartmentRecord item : departments) {
            if (!item.enabled()) {
                continue;
            }
            live.add(item);
            byCode.put(item.code(), item);
            if (item.fallback()) {
                if (foundFallback != null) {
                    throw new IllegalStateException("科室目录只能有一个兜底科");
                }
                foundFallback = item;
            }
        }
        if (live.isEmpty()) {
            throw new IllegalStateException("科室目录没有已开通科室");
        }
        if (foundFallback == null) {
            throw new IllegalStateException("科室目录必须有一个兜底科");
        }
        if (!"全科".equals(foundFallback.name())) {
            throw new IllegalStateException("兜底科必须是全科，实际是 " + foundFallback.name());
        }
        this.enabled = List.copyOf(live);
        this.enabledByCode = Map.copyOf(byCode);
        this.fallback = foundFallback;
        this.typos = copyTypos(typos);
        this.uncertainMarkers = copyMarkers(uncertainMarkers);
        this.symptomPhrases = buildSymptomPhrases(this.enabled);
    }

    public String version() {
        return version;
    }

    public List<DepartmentRecord> enabled() {
        return enabled;
    }

    public DepartmentRecord fallback() {
        return fallback;
    }

    public Department fallbackDepartment() {
        return fallback.toDepartment();
    }

    public List<MatchTypo> typos() {
        return typos;
    }

    public List<String> uncertainMarkers() {
        return uncertainMarkers;
    }

    /** 已开通科的 ROUTING 词，加上急症用的呼吸困难/胸痛。OVERRIDE 不算症状。 */
    public List<String> symptomPhrases() {
        return symptomPhrases;
    }

    public boolean isEnabled(int code) {
        return enabledByCode.containsKey(code);
    }

    public Department requireEnabled(int code) {
        if (!isEnabled(code)) {
            throw new BusinessException(ErrorCode.INVALID_DEPARTMENT);
        }
        return Department.fromCode(code);
    }

    public DepartmentSnapshot withoutEnabled(int code) {
        List<DepartmentRecord> copy = new ArrayList<>();
        for (DepartmentRecord item : enabled) {
            if (item.code() != code) {
                copy.add(item);
            }
        }
        return new DepartmentSnapshot(version, copy, typos, uncertainMarkers);
    }

    private static List<MatchTypo> copyTypos(List<MatchTypo> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<MatchTypo> copy = new ArrayList<>();
        for (MatchTypo typo : source) {
            if (typo == null || typo.from().isBlank() || typo.to().isBlank()) {
                continue;
            }
            copy.add(typo);
        }
        copy.sort((left, right) -> Integer.compare(right.from().length(), left.from().length()));
        return List.copyOf(copy);
    }

    private static List<String> copyMarkers(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> copy = new ArrayList<>();
        for (String marker : source) {
            if (marker == null || marker.isBlank()) {
                continue;
            }
            copy.add(marker);
        }
        copy.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return List.copyOf(copy);
    }

    private static List<String> buildSymptomPhrases(List<DepartmentRecord> departments) {
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        for (DepartmentRecord dept : departments) {
            phrases.addAll(dept.routingKeywords());
        }
        phrases.addAll(SAFETY_PHRASES);
        return List.copyOf(phrases);
    }
}
