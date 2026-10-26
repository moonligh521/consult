package com.moonligh.consult.agent.domain.department;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 从 classpath 种子文件加载目录。单测和库表未建时用。
 */
public final class DepartmentSeed {

    private static final String LOCATION = "/department/catalog-seed.json";

    private DepartmentSeed() {
    }

    public static DepartmentSnapshot load() {
        try (InputStream in = DepartmentSeed.class.getResourceAsStream(LOCATION)) {
            if (in == null) {
                throw new IllegalStateException("找不到科室种子 " + LOCATION);
            }
            SeedFile file = new ObjectMapper().findAndRegisterModules().readValue(in, SeedFile.class);
            return file.toSnapshot();
        } catch (IOException error) {
            throw new IllegalStateException("读取科室种子失败", error);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeedFile(
            String version,
            List<SeedDept> departments,
            List<SeedTypo> typos,
            List<String> uncertain,
            List<String> ambiguous,
            List<SeedSynonym> synonyms) {
        private DepartmentSnapshot toSnapshot() {
            List<DepartmentRecord> records = new ArrayList<>();
            for (SeedDept dept : departments == null ? List.<SeedDept>of() : departments) {
                records.add(dept.toRecord());
            }
            List<MatchTypo> matchTypos = new ArrayList<>();
            for (SeedTypo typo : typos == null ? List.<SeedTypo>of() : typos) {
                matchTypos.add(new MatchTypo(typo.from(), typo.to()));
            }
            List<CanonSynonym> rules = new ArrayList<>();
            for (SeedSynonym item : synonyms == null ? List.<SeedSynonym>of() : synonyms) {
                rules.add(new CanonSynonym(item.ruleId(), item.target(), item.all(), item.none(), item.pattern(),
                        item.status() == null ? SynonymStatus.DRAFT : item.status()));
            }
            return new DepartmentSnapshot(
                    version,
                    records,
                    matchTypos,
                    uncertain == null ? List.of() : uncertain,
                    ambiguous == null ? List.of() : ambiguous,
                    rules);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeedSynonym(
            String ruleId,
            String target,
            List<List<String>> all,
            List<String> none,
            String pattern,
            SynonymStatus status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeedTypo(String from, String to) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeedDept(
            int code,
            String name,
            boolean enabled,
            int sort,
            boolean fallback,
            int genderScope,
            Integer childAgeLt,
            String treatDesc,
            List<SeedSpecialty> specialties,
            List<SeedKw> keywords) {
        private DepartmentRecord toRecord() {
            List<String> routing = new ArrayList<>();
            List<String> override = new ArrayList<>();
            for (SeedKw keyword : keywords == null ? List.<SeedKw>of() : keywords) {
                if (keyword.kind() == KeywordKind.OVERRIDE) {
                    override.add(keyword.text());
                } else {
                    routing.add(keyword.text());
                }
            }
            List<DepartmentSpecialty> specs = new ArrayList<>();
            for (SeedSpecialty item : specialties == null ? List.<SeedSpecialty>of() : specialties) {
                specs.add(new DepartmentSpecialty(item.name(), item.treatDesc(), item.keywords(), item.diseases()));
            }
            return new DepartmentRecord(
                    code, name, enabled, sort, fallback, genderScope, childAgeLt,
                    routing, override, treatDesc, specs);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeedSpecialty(String name, String treatDesc, List<String> keywords, List<String> diseases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeedKw(String text, KeywordKind kind) {
    }
}
