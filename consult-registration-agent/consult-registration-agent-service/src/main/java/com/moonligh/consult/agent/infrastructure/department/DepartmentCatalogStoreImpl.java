package com.moonligh.consult.agent.infrastructure.department;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.moonligh.consult.agent.api.req.TermGroup;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.DepartmentRow;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.KeywordBasicRow;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.KeywordFullRow;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.KeywordSpecialtyRow;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.SpecialtyLegacyRow;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.SpecialtyRow;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.SynonymRow;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.TouchedDepartmentRow;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.TouchedSpecialtyRow;
import com.moonligh.consult.agent.infrastructure.persistence.DepartmentCatalogMapper.TypoRow;
import com.moonligh.consult.agent.domain.department.CanonSynonym;
import com.moonligh.consult.agent.domain.department.CatalogDriftException;
import com.moonligh.consult.agent.domain.department.CatalogSeedGate;
import com.moonligh.consult.agent.domain.department.DepartmentCatalogStore;
import com.moonligh.consult.agent.domain.department.SynonymStatus;
import com.moonligh.consult.agent.domain.department.DepartmentRecord;
import com.moonligh.consult.agent.domain.department.DepartmentSeed;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.DepartmentSpecialty;
import com.moonligh.consult.agent.domain.department.KeywordKind;
import com.moonligh.consult.agent.domain.department.MatchTypo;

/**
 * 从 MySQL 加载匹配快照。表还没建或为空时回落到种子，并打日志。
 */
public class DepartmentCatalogStoreImpl implements DepartmentCatalogStore {

    private static final Logger log = LoggerFactory.getLogger(DepartmentCatalogStoreImpl.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DepartmentCatalogMapper catalog;

    public DepartmentCatalogStoreImpl(DepartmentCatalogMapper catalog) {
        this.catalog = catalog;
    }

    public DepartmentSnapshot load() {
        try {
            Integer count = catalog.countDepartments();
            if (count == null || count == 0) {
                log.warn("consult_department 为空，暂用种子目录。请执行 db/schema.sql 与 db/seed.sql");
                return DepartmentSeed.load();
            }
            return loadTables();
        } catch (DataAccessException error) {
            log.warn("科室表不可用，暂用种子目录: {}", error.getMessage());
            return DepartmentSeed.load();
        }
    }

    @Override
    public void setEnabled(int code, boolean enabled) {
        int updated = catalog.setDepartmentEnabled(enabled ? 1 : 0, code);
        if (updated == 0) {
            throw new IllegalStateException("没有这个科室 code: " + code);
        }
    }

    @Override
    public void upsertTypo(MatchTypo typo) {
        int updated = catalog.reviveTypo(typo.to(), typo.from());
        if (updated == 0) {
            catalog.insertTypo(typo.from(), typo.to());
        }
    }

    @Override
    public void upsertUncertainMarker(String marker) {
        int updated = catalog.reviveUncertainMarker(marker);
        if (updated == 0) {
            catalog.insertUncertainMarker(marker);
        }
    }

    @Override
    public void upsertKeyword(int departmentCode, String keyword, KeywordKind kind) {
        Integer enabled;
        try {
            enabled = catalog.departmentEnabled(departmentCode);
        } catch (DataAccessException error) {
            throw new IllegalStateException("关键词只能挂已开通科室 code: " + departmentCode, error);
        }
        if (enabled == null || enabled != 1) {
            throw new IllegalStateException("关键词只能挂已开通科室 code: " + departmentCode);
        }
        int kindValue = kind == KeywordKind.OVERRIDE ? 2 : 1;
        int updated = catalog.reviveKeyword(kindValue, departmentCode, keyword);
        if (updated == 0) {
            catalog.insertKeyword(departmentCode, keyword, kindValue);
        }
    }

    private DepartmentSnapshot loadTables() {
        List<DepartmentRecord> records = loadRecords();
        DepartmentSnapshot seed = DepartmentSeed.load();
        DepartmentSnapshot loaded = new DepartmentSnapshot(
                "mysql-" + System.currentTimeMillis(),
                records, loadTypos(seed), loadUncertain(seed), loadAmbiguous(seed), loadSynonyms(seed));
        List<String> gaps = CatalogSeedGate.missingUntouched(seed, records, adminTouchedSpecialties());
        if (!gaps.isEmpty()) {
            throw new CatalogDriftException(gaps);
        }
        return loaded;
    }

    private Set<String> adminTouchedSpecialties() {
        Set<String> touched = new HashSet<>();
        try {
            for (TouchedSpecialtyRow row : catalog.listAdminTouchedSpecialties()) {
                touched.add(nz(row.departmentCode()) + "\n" + row.name());
            }
            for (TouchedDepartmentRow row : catalog.listAdminTouchedDepartments()) {
                touched.add(nz(row.code()) + "\n");
            }
        } catch (DataAccessException error) {
            log.warn("无法读取二级科修改人，按未运营修改对账: {}", error.getMessage());
        }
        return touched;
    }

    @Override
    public void upsertAmbiguous(String phrase) {
        int updated = catalog.reviveAmbiguous(phrase);
        if (updated == 0) {
            catalog.insertAmbiguous(phrase);
        }
    }

    @Override
    public void saveSynonym(CanonSynonym rule) {
        String groups = writeJson(rule.allGroups());
        String none = writeJson(rule.noneTerms());
        int updated = catalog.updateDraftSynonym(rule.targetTerm(), groups, none, rule.pattern(), rule.ruleId());
        if (updated == 0) {
            Integer exists = catalog.countSynonym(rule.ruleId());
            if (exists != null && exists > 0) {
                throw new IllegalStateException("只有 DRAFT 规则能改: " + rule.ruleId());
            }
            catalog.insertSynonym(rule.ruleId(), rule.targetTerm(), groups, none, rule.pattern());
        }
    }

    @Override
    public void setSynonymStatus(String ruleId, SynonymStatus from, SynonymStatus to, String reviewer) {
        int updated = catalog.setSynonymStatus(to.getCode(), reviewer, ruleId, from.getCode());
        if (updated == 0) {
            throw new IllegalStateException("规则不存在或状态不是 " + from + ": " + ruleId);
        }
    }

    private List<String> loadAmbiguous(DepartmentSnapshot seed) {
        try {
            List<String> rows = catalog.listAmbiguous();
            if (rows.isEmpty()) {
                log.warn("consult_ambiguous_symptom 为空，暂用种子歧义症状");
                return seed.ambiguousSymptoms();
            }
            return rows;
        } catch (DataAccessException error) {
            log.warn("歧义症状表不可用，暂用种子: {}", error.getMessage());
            return seed.ambiguousSymptoms();
        }
    }

    private List<CanonSynonym> loadSynonyms(DepartmentSnapshot seed) {
        try {
            List<SynonymRow> rows = catalog.listSynonyms();
            List<CanonSynonym> synonyms = new ArrayList<>(rows.size());
            for (SynonymRow row : rows) {
                synonyms.add(new CanonSynonym(
                        row.ruleId(),
                        row.targetTerm(),
                        readGroups(row.allGroups()),
                        readList(row.noneTerms()),
                        row.pattern(),
                        SynonymStatus.fromCode(nz(row.status()))));
            }
            return synonyms;
        } catch (DataAccessException error) {
            log.warn("同义规则表不可用，暂用种子: {}", error.getMessage());
            return seed.synonyms();
        }
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("同义规则序列化失败", error);
        }
    }

    private static List<TermGroup> readGroups(String json) {
        try {
            return JSON.readValue(json, new TypeReference<List<TermGroup>>() { });
        } catch (Exception error) {
            throw new IllegalStateException("all_groups 不是二维数组", error);
        }
    }

    private static List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception error) {
            throw new IllegalStateException("none_terms 不是数组", error);
        }
    }

    private List<MatchTypo> loadTypos(DepartmentSnapshot seed) {
        try {
            List<TypoRow> rows = catalog.listTypos();
            if (rows.isEmpty()) {
                log.warn("consult_match_typo 为空，暂用种子错别字");
                return seed.typos();
            }
            List<MatchTypo> typos = new ArrayList<>(rows.size());
            for (TypoRow row : rows) {
                typos.add(new MatchTypo(row.fromWord(), row.toWord()));
            }
            return typos;
        } catch (DataAccessException error) {
            log.warn("错别字表不可用，暂用种子错别字: {}", error.getMessage());
            return seed.typos();
        }
    }

    private List<String> loadUncertain(DepartmentSnapshot seed) {
        try {
            List<String> rows = catalog.listUncertainMarkers();
            if (rows.isEmpty()) {
                log.warn("consult_uncertain_marker 为空，暂用种子不确定标记");
                return seed.uncertainMarkers();
            }
            return rows;
        } catch (DataAccessException error) {
            log.warn("不确定标记表不可用，暂用种子标记: {}", error.getMessage());
            return seed.uncertainMarkers();
        }
    }

    private void loadKeywords(Map<Integer, MutableDept> depts) {
        try {
            for (KeywordFullRow row : catalog.listKeywords()) {
                bindKeyword(depts, nz(row.departmentCode()), row.keyword(), nz(row.kind()), row.specialtyName(), true);
                if (nz(row.termType()) == 2) {
                    bindDisease(depts, nz(row.departmentCode()), row.specialtyName(), row.keyword());
                }
            }
            return;
        } catch (DataAccessException error) {
            log.warn("关键词未带词型列，病名归约暂不可用。请执行 db/migrate-triage.sql: {}", error.getMessage());
        }
        try {
            for (KeywordSpecialtyRow row : catalog.listKeywordsWithSpecialty()) {
                bindKeyword(depts, nz(row.departmentCode()), row.keyword(), nz(row.kind()), row.specialtyName(), true);
            }
        } catch (DataAccessException error) {
            log.warn("关键词未带二级科列，按一级类加载: {}", error.getMessage());
            for (KeywordBasicRow row : catalog.listKeywordsBasic()) {
                bindKeyword(depts, nz(row.departmentCode()), row.keyword(), nz(row.kind()), null, false);
            }
        }
    }

    private static void bindKeyword(
            Map<Integer, MutableDept> depts,
            int departmentCode,
            String keyword,
            int kind,
            String specialtyName,
            boolean withSpecialty) {
        MutableDept dept = depts.get(departmentCode);
        if (dept == null) {
            return;
        }
        if (kind == 2) {
            dept.override.add(keyword);
            return;
        }
        String specialty = withSpecialty ? specialtyName : null;
        if (specialty != null && !specialty.isBlank()) {
            MutableSpec spec = dept.specNamed(specialty);
            if (spec != null) {
                spec.keywords.add(keyword);
            }
        }
        dept.routing.add(keyword);
    }

    private static void bindDisease(
            Map<Integer, MutableDept> depts, int departmentCode, String specialty, String keyword) {
        MutableDept dept = depts.get(departmentCode);
        if (dept == null || specialty == null) {
            return;
        }
        MutableSpec spec = dept.specNamed(specialty);
        if (spec != null) {
            spec.diseases.add(keyword);
        }
    }

    private void loadSpecialties(Map<Integer, MutableDept> depts) {
        try {
            for (SpecialtyRow row : catalog.listSpecialties()) {
                MutableDept dept = depts.get(nz(row.departmentCode()));
                if (dept == null) {
                    continue;
                }
                dept.specialties.add(new MutableSpec(
                        row.id() == null ? 0L : row.id(),
                        row.name(),
                        row.treatDesc(),
                        nz(row.enabled()) == 1,
                        nz(row.audience())));
            }
        } catch (DataAccessException error) {
            log.warn("二级科室表不可用，暂不加载细分科: {}", error.getMessage());
            loadSpecialtiesLegacy(depts);
        }
    }

    private void loadSpecialtiesLegacy(Map<Integer, MutableDept> depts) {
        try {
            for (SpecialtyLegacyRow row : catalog.listSpecialtiesLegacy()) {
                MutableDept dept = depts.get(nz(row.departmentCode()));
                if (dept == null) {
                    continue;
                }
                dept.specialties.add(new MutableSpec(
                        row.id() == null ? 0L : row.id(),
                        row.name(),
                        row.treatDesc(),
                        true,
                        DepartmentSpecialty.inferAudience(row.name())));
            }
        } catch (DataAccessException ignored) {
            // 表仍不可用时目录只有一级
        }
    }

    @Override
    public List<DepartmentRecord> loadRecords() {
        Map<Integer, MutableDept> depts = new LinkedHashMap<>();
        for (DepartmentRow row : catalog.listDepartments()) {
            int code = nz(row.code());
            depts.put(code, new MutableDept(
                    code,
                    row.name(),
                    nz(row.enabled()) == 1,
                    nz(row.sortNo()),
                    nz(row.fallback()) == 1,
                    nz(row.genderScope()),
                    row.childAgeLt(),
                    row.treatDesc()));
        }
        loadSpecialties(depts);
        loadKeywords(depts);
        List<DepartmentRecord> records = new ArrayList<>();
        for (MutableDept dept : depts.values()) {
            records.add(dept.toRecord());
        }
        return records;
    }

    @Override
    public int insertDepartment(String name, int genderScope, Integer childAgeLt, String treatDesc) {
        Integer max = catalog.maxDepartmentCode();
        int code = (max == null ? 22 : max) + 1;
        catalog.insertDepartment(code, name, code, genderScope, childAgeLt, treatDesc);
        return code;
    }

    @Override
    public long insertSpecialty(int departmentCode, String name, String treatDesc, int audience) {
        catalog.insertSpecialty(departmentCode, name, treatDesc, audience);
        Long id = catalog.latestSpecialtyId(departmentCode, name);
        if (id == null) {
            throw new IllegalStateException("新建二级科失败: " + name);
        }
        return id;
    }

    @Override
    public void setSpecialtyEnabled(long specialtyId, boolean enabled) {
        int updated = catalog.setSpecialtyEnabled(enabled ? 1 : 0, specialtyId);
        if (updated == 0) {
            throw new IllegalStateException("没有这个二级科 id: " + specialtyId);
        }
    }

    @Override
    public void renameSpecialty(long specialtyId, String name) {
        catalog.renameSpecialty(name, specialtyId);
        catalog.renameKeywordSpecialty(specialtyId, name);
        catalog.renameDoctorSpecialty(specialtyId, name);
    }

    @Override
    public void setSpecialtyAudience(long specialtyId, int audience) {
        int updated = catalog.setSpecialtyAudience(audience, specialtyId);
        if (updated == 0) {
            throw new IllegalStateException("没有这个二级科 id: " + specialtyId);
        }
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static final class MutableDept {
        private final int code;
        private final String name;
        private final boolean enabled;
        private final int sort;
        private final boolean fallback;
        private final int genderScope;
        private final Integer childAgeLt;
        private final String treatDesc;
        private final List<String> routing = new ArrayList<>();
        private final List<String> override = new ArrayList<>();
        private final List<MutableSpec> specialties = new ArrayList<>();

        private MutableDept(
                int code,
                String name,
                boolean enabled,
                int sort,
                boolean fallback,
                int genderScope,
                Integer childAgeLt,
                String treatDesc) {
            this.code = code;
            this.name = name;
            this.enabled = enabled;
            this.sort = sort;
            this.fallback = fallback;
            this.genderScope = genderScope;
            this.childAgeLt = childAgeLt;
            this.treatDesc = treatDesc == null ? "" : treatDesc;
        }

        private MutableSpec specNamed(String specialty) {
            for (MutableSpec spec : specialties) {
                if (specialty.equals(spec.name)) {
                    return spec;
                }
            }
            return null;
        }

        private DepartmentRecord toRecord() {
            List<DepartmentSpecialty> specs = new ArrayList<>();
            for (MutableSpec spec : specialties) {
                specs.add(new DepartmentSpecialty(
                        spec.id, spec.name, spec.treatDesc, spec.enabled, spec.keywords, spec.diseases, spec.audience));
            }
            return new DepartmentRecord(
                    code, name, enabled, sort, fallback, genderScope, childAgeLt,
                    routing, override, treatDesc, specs);
        }
    }

    private static final class MutableSpec {
        private final Long id;
        private final String name;
        private final String treatDesc;
        private final boolean enabled;
        private final int audience;
        private final List<String> keywords = new ArrayList<>();
        private final List<String> diseases = new ArrayList<>();

        private MutableSpec(Long id, String name, String treatDesc, boolean enabled, int audience) {
            this.id = id;
            this.name = name;
            this.treatDesc = treatDesc == null ? "" : treatDesc;
            this.enabled = enabled;
            this.audience = audience;
        }
    }
}
