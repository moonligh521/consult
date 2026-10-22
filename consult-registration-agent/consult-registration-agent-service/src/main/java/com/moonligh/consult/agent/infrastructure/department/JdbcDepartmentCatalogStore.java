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

import com.moonligh.consult.agent.domain.department.DepartmentCatalogStore;
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
        DepartmentSnapshot seed = DepartmentSeed.load();
        List<DepartmentRecord> records = new ArrayList<>();
        for (MutableDept dept : depts.values()) {
            records.add(dept.toRecord());
        }
        return new DepartmentSnapshot("mysql", records, loadTypos(seed), loadUncertain(seed));
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

    private void loadSpecialties(Map<Integer, MutableDept> depts) {
        try {
            jdbc.query(
                    """
                    SELECT department_code, name, treat_desc
                    FROM consult_department_specialty
                    WHERE is_deleted = 0
                    ORDER BY sort_no, id
                    """,
                    rs -> {
                        MutableDept dept = depts.get(rs.getInt("department_code"));
                        if (dept == null) {
                            return;
                        }
                        dept.specialties.add(new MutableSpec(rs.getString("name"), rs.getString("treat_desc")));
                    });
        } catch (DataAccessException error) {
            log.warn("二级科室表不可用，暂不加载细分科: {}", error.getMessage());
        }
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
                specs.add(new DepartmentSpecialty(spec.name, spec.treatDesc, spec.keywords));
            }
            return new DepartmentRecord(
                    code, name, enabled, sort, fallback, genderScope, childAgeLt,
                    routing, override, treatDesc, specs);
        }
    }

    private static final class MutableSpec {
        private final String name;
        private final String treatDesc;
        private final List<String> keywords = new ArrayList<>();

        private MutableSpec(String name, String treatDesc) {
            this.name = name;
            this.treatDesc = treatDesc == null ? "" : treatDesc;
        }
    }
}
