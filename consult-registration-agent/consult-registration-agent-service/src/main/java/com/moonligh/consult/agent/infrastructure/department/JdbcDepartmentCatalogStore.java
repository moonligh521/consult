package com.moonligh.consult.agent.infrastructure.department;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.domain.department.CanonSynonym;
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
public class JdbcDepartmentCatalogStore implements DepartmentCatalogStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcDepartmentCatalogStore.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public JdbcDepartmentCatalogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DepartmentSnapshot load() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM consult_department WHERE is_deleted = 0", Integer.class);
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
        int updated = jdbc.update(
                "UPDATE consult_department SET enabled = ? WHERE code = ? AND is_deleted = 0",
                enabled ? 1 : 0,
                code);
        if (updated == 0) {
            throw new IllegalStateException("没有这个科室 code: " + code);
        }
    }

    @Override
    public void upsertTypo(MatchTypo typo) {
        int updated = jdbc.update(
                """
                UPDATE consult_match_typo
                SET to_word = ?, is_deleted = 0
                WHERE from_word = ? AND is_deleted = 0
                """,
                typo.to(),
                typo.from());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO consult_match_typo (from_word, to_word) VALUES (?, ?)",
                    typo.from(),
                    typo.to());
        }
    }

    @Override
    public void upsertUncertainMarker(String marker) {
        int updated = jdbc.update(
                """
                UPDATE consult_uncertain_marker
                SET is_deleted = 0
                WHERE marker = ? AND is_deleted = 0
                """,
                marker);
        if (updated == 0) {
            jdbc.update("INSERT INTO consult_uncertain_marker (marker) VALUES (?)", marker);
        }
    }

    @Override
    public void upsertKeyword(int departmentCode, String keyword, KeywordKind kind) {
        Integer enabled;
        try {
            enabled = jdbc.queryForObject(
                    "SELECT enabled FROM consult_department WHERE code = ? AND is_deleted = 0",
                    Integer.class,
                    departmentCode);
        } catch (DataAccessException error) {
            throw new IllegalStateException("关键词只能挂已开通科室 code: " + departmentCode, error);
        }
        if (enabled == null || enabled != 1) {
            throw new IllegalStateException("关键词只能挂已开通科室 code: " + departmentCode);
        }
        int kindValue = kind == KeywordKind.OVERRIDE ? 2 : 1;
        int updated = jdbc.update(
                """
                UPDATE consult_department_keyword
                SET kind = ?, is_deleted = 0
                WHERE department_code = ? AND keyword = ? AND is_deleted = 0
                """,
                kindValue,
                departmentCode,
                keyword);
        if (updated == 0) {
            jdbc.update(
                    """
                    INSERT INTO consult_department_keyword (department_code, keyword, kind)
                    VALUES (?, ?, ?)
                    """,
                    departmentCode,
                    keyword,
                    kindValue);
        }
    }

    private DepartmentSnapshot loadTables() {
        List<DepartmentRecord> records = loadRecords();
        DepartmentSnapshot seed = DepartmentSeed.load();
        return new DepartmentSnapshot(
                "mysql-" + System.currentTimeMillis(),
                records, loadTypos(seed), loadUncertain(seed), loadAmbiguous(seed), loadSynonyms(seed));
    }

    @Override
    public void upsertAmbiguous(String phrase) {
        int updated = jdbc.update(
                "UPDATE consult_ambiguous_symptom SET is_deleted = 0 WHERE phrase = ? AND is_deleted = 0",
                phrase);
        if (updated == 0) {
            jdbc.update("INSERT INTO consult_ambiguous_symptom (phrase) VALUES (?)", phrase);
        }
    }

    @Override
    public void saveSynonym(CanonSynonym rule) {
        String groups = writeJson(rule.allGroups());
        String none = writeJson(rule.noneTerms());
        int updated = jdbc.update(
                """
                UPDATE consult_canon_synonym
                SET target_term = ?, all_groups = ?, none_terms = ?, pattern = ?
                WHERE rule_id = ? AND is_deleted = 0 AND status = 1
                """,
                rule.targetTerm(), groups, none, rule.pattern(), rule.ruleId());
        if (updated == 0) {
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM consult_canon_synonym WHERE rule_id = ? AND is_deleted = 0",
                    Integer.class, rule.ruleId());
            if (exists != null && exists > 0) {
                throw new IllegalStateException("只有 DRAFT 规则能改: " + rule.ruleId());
            }
            jdbc.update(
                    """
                    INSERT INTO consult_canon_synonym (rule_id, target_term, all_groups, none_terms, pattern, status)
                    VALUES (?, ?, ?, ?, ?, 1)
                    """,
                    rule.ruleId(), rule.targetTerm(), groups, none, rule.pattern());
        }
    }

    @Override
    public void setSynonymStatus(String ruleId, SynonymStatus from, SynonymStatus to, String reviewer) {
        int updated = jdbc.update(
                """
                UPDATE consult_canon_synonym
                SET status = ?, reviewer = COALESCE(?, reviewer),
                    reviewed_at = CASE WHEN ? = 2 THEN CURRENT_TIMESTAMP(3) ELSE reviewed_at END
                WHERE rule_id = ? AND status = ? AND is_deleted = 0
                """,
                to.getCode(), reviewer, to.getCode(), ruleId, from.getCode());
        if (updated == 0) {
            throw new IllegalStateException("规则不存在或状态不是 " + from + ": " + ruleId);
        }
    }

    private List<String> loadAmbiguous(DepartmentSnapshot seed) {
        try {
            List<String> rows = jdbc.query(
                    "SELECT phrase FROM consult_ambiguous_symptom WHERE is_deleted = 0",
                    (rs, rowNum) -> rs.getString("phrase"));
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
            return jdbc.query(
                    """
                    SELECT rule_id, target_term, all_groups, none_terms, pattern, status
                    FROM consult_canon_synonym
                    WHERE is_deleted = 0 AND status IN (1, 2)
                    ORDER BY rule_id
                    """,
                    (rs, rowNum) -> new CanonSynonym(
                            rs.getString("rule_id"),
                            rs.getString("target_term"),
                            readGroups(rs.getString("all_groups")),
                            readList(rs.getString("none_terms")),
                            rs.getString("pattern"),
                            SynonymStatus.fromCode(rs.getInt("status"))));
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

    private static List<List<String>> readGroups(String json) {
        try {
            return JSON.readValue(json, new TypeReference<List<List<String>>>() { });
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
            List<MatchTypo> rows = jdbc.query(
                    "SELECT from_word, to_word FROM consult_match_typo WHERE is_deleted = 0",
                    (rs, rowNum) -> new MatchTypo(rs.getString("from_word"), rs.getString("to_word")));
            if (rows.isEmpty()) {
                log.warn("consult_match_typo 为空，暂用种子错别字");
                return seed.typos();
            }
            return rows;
        } catch (DataAccessException error) {
            log.warn("错别字表不可用，暂用种子错别字: {}", error.getMessage());
            return seed.typos();
        }
    }

    private List<String> loadUncertain(DepartmentSnapshot seed) {
        try {
            List<String> rows = jdbc.query(
                    "SELECT marker FROM consult_uncertain_marker WHERE is_deleted = 0",
                    (rs, rowNum) -> rs.getString("marker"));
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
            jdbc.query(
                    """
                    SELECT department_code, keyword, kind, specialty_name, term_type
                    FROM consult_department_keyword
                    WHERE is_deleted = 0
                    """,
                    rs -> {
                        bindKeyword(depts, rs, true);
                        if (rs.getInt("term_type") == 2) {
                            bindDisease(depts, rs);
                        }
                    });
            return;
        } catch (DataAccessException error) {
            log.warn("关键词未带词型列，病名归约暂不可用。请执行 db/migrate-triage.sql: {}", error.getMessage());
        }
        try {
            jdbc.query(
                    """
                    SELECT department_code, keyword, kind, specialty_name
                    FROM consult_department_keyword
                    WHERE is_deleted = 0
                    """,
                    rs -> {
                        bindKeyword(depts, rs, true);
                    });
        } catch (DataAccessException error) {
            log.warn("关键词未带二级科列，按一级类加载: {}", error.getMessage());
            jdbc.query(
                    """
                    SELECT department_code, keyword, kind
                    FROM consult_department_keyword
                    WHERE is_deleted = 0
                    """,
                    rs -> {
                        bindKeyword(depts, rs, false);
                    });
        }
    }

    private static void bindKeyword(Map<Integer, MutableDept> depts, ResultSet rs, boolean withSpecialty)
            throws SQLException {
        MutableDept dept = depts.get(rs.getInt("department_code"));
        if (dept == null) {
            return;
        }
        String keyword = rs.getString("keyword");
        if (rs.getInt("kind") == 2) {
            dept.override.add(keyword);
            return;
        }
        String specialty = withSpecialty ? rs.getString("specialty_name") : null;
        if (specialty != null && !specialty.isBlank()) {
            MutableSpec spec = dept.specNamed(specialty);
            if (spec != null) {
                spec.keywords.add(keyword);
            }
        }
        dept.routing.add(keyword);
    }

    private static void bindDisease(Map<Integer, MutableDept> depts, ResultSet rs) throws SQLException {
        MutableDept dept = depts.get(rs.getInt("department_code"));
        String specialty = rs.getString("specialty_name");
        if (dept == null || specialty == null) {
            return;
        }
        MutableSpec spec = dept.specNamed(specialty);
        if (spec != null) {
            spec.diseases.add(rs.getString("keyword"));
        }
    }

    private void loadSpecialties(Map<Integer, MutableDept> depts) {
        try {
            jdbc.query(
                    """
                    SELECT id, department_code, name, treat_desc, enabled
                    FROM consult_department_specialty
                    WHERE is_deleted = 0
                    ORDER BY sort_no, id
                    """,
                    rs -> {
                        MutableDept dept = depts.get(rs.getInt("department_code"));
                        if (dept == null) {
                            return;
                        }
                        dept.specialties.add(new MutableSpec(
                                rs.getLong("id"),
                                rs.getString("name"),
                                rs.getString("treat_desc"),
                                rs.getInt("enabled") == 1));
                    });
        } catch (DataAccessException error) {
            log.warn("二级科室表不可用，暂不加载细分科: {}", error.getMessage());
            loadSpecialtiesLegacy(depts);
        }
    }

    private void loadSpecialtiesLegacy(Map<Integer, MutableDept> depts) {
        try {
            jdbc.query(
                    """
                    SELECT id, department_code, name, treat_desc
                    FROM consult_department_specialty
                    WHERE is_deleted = 0
                    ORDER BY sort_no, id
                    """,
                    rs -> {
                        MutableDept dept = depts.get(rs.getInt("department_code"));
                        if (dept == null) {
                            return;
                        }
                        dept.specialties.add(new MutableSpec(
                                rs.getLong("id"),
                                rs.getString("name"),
                                rs.getString("treat_desc"),
                                true));
                    });
        } catch (DataAccessException ignored) {
            // 表仍不可用时目录只有一级
        }
    }

    @Override
    public List<DepartmentRecord> loadRecords() {
        Map<Integer, MutableDept> depts = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT code, name, enabled, sort_no, fallback, gender_scope, child_age_lt, treat_desc
                FROM consult_department
                WHERE is_deleted = 0
                ORDER BY sort_no, code
                """,
                rs -> {
                    int code = rs.getInt("code");
                    depts.put(code, new MutableDept(
                            code,
                            rs.getString("name"),
                            rs.getInt("enabled") == 1,
                            rs.getInt("sort_no"),
                            rs.getInt("fallback") == 1,
                            rs.getInt("gender_scope"),
                            childAge(rs),
                            rs.getString("treat_desc")));
                });
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
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(code), 22) FROM consult_department", Integer.class);
        int code = (max == null ? 22 : max) + 1;
        jdbc.update(
                """
                INSERT INTO consult_department
                    (code, name, enabled, sort_no, fallback, gender_scope, child_age_lt, treat_desc, remark)
                VALUES (?, ?, 0, ?, 0, ?, ?, ?, '运营新增')
                """,
                code,
                name,
                code,
                genderScope,
                childAgeLt,
                treatDesc);
        return code;
    }

    @Override
    public long insertSpecialty(int departmentCode, String name, String treatDesc) {
        jdbc.update(
                """
                INSERT INTO consult_department_specialty
                    (department_code, name, treat_desc, sort_no, enabled, remark)
                VALUES (?, ?, ?, 0, 0, '运营新增')
                """,
                departmentCode,
                name,
                treatDesc);
        Long id = jdbc.queryForObject(
                """
                SELECT id FROM consult_department_specialty
                WHERE department_code = ? AND name = ? AND is_deleted = 0
                ORDER BY id DESC LIMIT 1
                """,
                Long.class,
                departmentCode,
                name);
        if (id == null) {
            throw new IllegalStateException("新建二级科失败: " + name);
        }
        return id;
    }

    @Override
    public void setSpecialtyEnabled(long specialtyId, boolean enabled) {
        int updated = jdbc.update(
                "UPDATE consult_department_specialty SET enabled = ? WHERE id = ? AND is_deleted = 0",
                enabled ? 1 : 0,
                specialtyId);
        if (updated == 0) {
            throw new IllegalStateException("没有这个二级科 id: " + specialtyId);
        }
    }

    @Override
    public void renameSpecialty(long specialtyId, String name) {
        jdbc.update(
                "UPDATE consult_department_specialty SET name = ? WHERE id = ? AND is_deleted = 0",
                name,
                specialtyId);
        jdbc.update(
                """
                UPDATE consult_department_keyword k
                JOIN consult_department_specialty s ON s.id = ? AND s.is_deleted = 0
                SET k.specialty_name = ?
                WHERE k.department_code = s.department_code AND k.specialty_name = s.name AND k.is_deleted = 0
                """,
                specialtyId,
                name);
        jdbc.update(
                """
                UPDATE consult_doctor d
                JOIN consult_department_specialty s ON s.id = ? AND s.is_deleted = 0
                SET d.specialty = ?, d.department_code = s.department_code
                WHERE d.is_deleted = 0 AND (
                    d.specialty_id = s.id
                    OR (d.specialty_id IS NULL AND d.department_code = s.department_code AND d.specialty = s.name)
                )
                """,
                specialtyId,
                name);
    }

    private static Integer childAge(ResultSet rs) throws SQLException {
        int value = rs.getInt("child_age_lt");
        return rs.wasNull() ? null : value;
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
                        spec.id, spec.name, spec.treatDesc, spec.enabled, spec.keywords, spec.diseases));
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
        private final List<String> keywords = new ArrayList<>();
        private final List<String> diseases = new ArrayList<>();

        private MutableSpec(Long id, String name, String treatDesc, boolean enabled) {
            this.id = id;
            this.name = name;
            this.treatDesc = treatDesc == null ? "" : treatDesc;
            this.enabled = enabled;
        }
    }
}
