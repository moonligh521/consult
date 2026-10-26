package com.moonligh.consult.agent.domain.department;

import java.util.ArrayList;
import java.util.Collections;
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
    private final List<String> ambiguousSymptoms;
    private final List<CanonSynonym> synonyms;
    private final Map<String, SpecialtyRef> diseaseIndex;
    private final Map<String, List<SpecialtyRef>> phraseRefs;

    public DepartmentSnapshot(String version, List<DepartmentRecord> departments) {
        this(version, departments, List.of(), List.of());
    }

    public DepartmentSnapshot(
            String version,
            List<DepartmentRecord> departments,
            List<MatchTypo> typos,
            List<String> uncertainMarkers) {
        this(version, departments, typos, uncertainMarkers, List.of(), List.of());
    }

    public DepartmentSnapshot(
            String version,
            List<DepartmentRecord> departments,
            List<MatchTypo> typos,
            List<String> uncertainMarkers,
            List<String> ambiguousSymptoms,
            List<CanonSynonym> synonyms) {
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
        this.ambiguousSymptoms = copyMarkers(ambiguousSymptoms);
        this.synonyms = synonyms == null ? List.of() : List.copyOf(synonyms);
        this.diseaseIndex = buildDiseaseIndex(this.enabled);
        this.phraseRefs = buildPhraseRefs(this.enabled);
    }

    /** 挂词 → 能挂的二级，口径与关键词路由一致：二级挂词、一级挂词落默认二级、一级名。 */
    public Map<String, List<SpecialtyRef>> phraseRefs() {
        return phraseRefs;
    }

    /** 多系统都可能出现的症状。只有这些词时挂科交给模型。 */
    public List<String> ambiguousSymptoms() {
        return ambiguousSymptoms;
    }

    public boolean isAmbiguous(String phrase) {
        return ambiguousSymptoms.contains(phrase);
    }

    /** 全部同义规则，含 DRAFT。 */
    public List<CanonSynonym> synonyms() {
        return synonyms;
    }

    /** 已开通二级的病名 → 所在二级。同名病只认目录里第一次出现的。 */
    public Map<String, SpecialtyRef> diseaseIndex() {
        return diseaseIndex;
    }

    public boolean isDisease(String term) {
        return diseaseIndex.containsKey(term);
    }

    /** 模型允许输出的病名，只有目录 DISEASE 词。 */
    public List<String> allowedDiseaseTerms() {
        return List.copyOf(diseaseIndex.keySet());
    }

    /** 已开通的全部二级科名，按目录顺序。 */
    public List<String> enabledSpecialtyNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (DepartmentRecord dept : enabled) {
            for (DepartmentSpecialty spec : dept.specialties()) {
                names.add(spec.name());
            }
        }
        return List.copyOf(names);
    }

    /** 按二级科名找它所在的一级码。重名取第一个。 */
    public SpecialtyRef refOfSpecialty(String specialty) {
        if (specialty == null) {
            return null;
        }
        for (DepartmentRecord dept : enabled) {
            if (dept.hasSpecialty(specialty)) {
                return new SpecialtyRef(dept.code(), specialty);
            }
        }
        return null;
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

    public DepartmentRecord recordOf(int code) {
        return enabledByCode.get(code);
    }

    public String resolveSpecialty(Department department, String specialty) {
        DepartmentRecord rec = department == null ? null : enabledByCode.get(department.getCode());
        if (rec == null) {
            return fallback.defaultSpecialty();
        }
        if (rec.hasSpecialty(specialty)) {
            return specialty;
        }
        return rec.defaultSpecialty();
    }

    /** 按二级科名反查一级类。重名时返回空，调用方按一级码来。 */
    public DepartmentRecord parentOfSpecialty(String specialty) {
        if (specialty == null || specialty.isBlank()) {
            return null;
        }
        DepartmentRecord found = null;
        for (DepartmentRecord dept : enabled) {
            if (!dept.hasSpecialty(specialty)) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = dept;
        }
        return found;
    }

    public DepartmentSnapshot withoutEnabled(int code) {
        List<DepartmentRecord> copy = new ArrayList<>();
        for (DepartmentRecord item : enabled) {
            if (item.code() != code) {
                copy.add(item);
            }
        }
        return new DepartmentSnapshot(version, copy, typos, uncertainMarkers, ambiguousSymptoms, synonyms);
    }

    public DepartmentSnapshot withSynonyms(List<CanonSynonym> replaced) {
        return new DepartmentSnapshot(version, enabled, typos, uncertainMarkers, ambiguousSymptoms, replaced);
    }

    private static Map<String, List<SpecialtyRef>> buildPhraseRefs(List<DepartmentRecord> departments) {
        Map<String, List<SpecialtyRef>> refs = new LinkedHashMap<>();
        for (DepartmentRecord dept : departments) {
            java.util.Set<String> used = new java.util.HashSet<>();
            for (DepartmentSpecialty spec : dept.specialties()) {
                for (String term : spec.matchTerms()) {
                    used.add(term);
                    addRef(refs, term, new SpecialtyRef(dept.code(), spec.name()));
                }
            }
            SpecialtyRef fallbackRef = new SpecialtyRef(dept.code(), dept.defaultSpecialty());
            for (String term : dept.routingKeywords()) {
                if (!used.contains(term)) {
                    addRef(refs, term, fallbackRef);
                }
            }
            if (!used.contains(dept.name()) && dept.name().length() >= 2) {
                addRef(refs, dept.name(), fallbackRef);
            }
        }
        Map<String, List<SpecialtyRef>> frozen = new LinkedHashMap<>();
        refs.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(frozen);
    }

    private static void addRef(Map<String, List<SpecialtyRef>> refs, String term, SpecialtyRef ref) {
        if (term == null || term.isBlank()) {
            return;
        }
        List<SpecialtyRef> list = refs.computeIfAbsent(term, key -> new ArrayList<>());
        if (!list.contains(ref)) {
            list.add(ref);
        }
    }

    private static Map<String, SpecialtyRef> buildDiseaseIndex(List<DepartmentRecord> departments) {
        Map<String, SpecialtyRef> index = new LinkedHashMap<>();
        for (DepartmentRecord dept : departments) {
            for (DepartmentSpecialty spec : dept.specialties()) {
                for (String term : spec.diseaseTerms()) {
                    if (term != null && !term.isBlank()) {
                        index.putIfAbsent(term, new SpecialtyRef(dept.code(), spec.name()));
                    }
                }
            }
        }
        return Collections.unmodifiableMap(index);
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
            for (DepartmentSpecialty spec : dept.specialties()) {
                phrases.addAll(spec.routingKeywords());
            }
        }
        phrases.addAll(SAFETY_PHRASES);
        return List.copyOf(phrases);
    }
}
