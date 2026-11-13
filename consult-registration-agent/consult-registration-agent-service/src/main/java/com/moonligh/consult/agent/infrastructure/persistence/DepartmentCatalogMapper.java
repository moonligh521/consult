package com.moonligh.consult.agent.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DepartmentCatalogMapper {

    Integer countDepartments();

    int setDepartmentEnabled(@Param("enabled") int enabled, @Param("code") int code);

    int reviveTypo(@Param("toWord") String toWord, @Param("fromWord") String fromWord);

    int insertTypo(@Param("fromWord") String fromWord, @Param("toWord") String toWord);

    int reviveUncertainMarker(@Param("marker") String marker);

    int insertUncertainMarker(@Param("marker") String marker);

    Integer departmentEnabled(@Param("code") int code);

    int reviveKeyword(
            @Param("kind") int kind,
            @Param("departmentCode") int departmentCode,
            @Param("keyword") String keyword);

    int insertKeyword(
            @Param("departmentCode") int departmentCode,
            @Param("keyword") String keyword,
            @Param("kind") int kind);

    List<TouchedSpecialtyRow> listAdminTouchedSpecialties();

    List<TouchedDepartmentRow> listAdminTouchedDepartments();

    int reviveAmbiguous(@Param("phrase") String phrase);

    int insertAmbiguous(@Param("phrase") String phrase);

    int updateDraftSynonym(
            @Param("targetTerm") String targetTerm,
            @Param("allGroups") String allGroups,
            @Param("noneTerms") String noneTerms,
            @Param("pattern") String pattern,
            @Param("ruleId") String ruleId);

    Integer countSynonym(@Param("ruleId") String ruleId);

    int insertSynonym(
            @Param("ruleId") String ruleId,
            @Param("targetTerm") String targetTerm,
            @Param("allGroups") String allGroups,
            @Param("noneTerms") String noneTerms,
            @Param("pattern") String pattern);

    int setSynonymStatus(
            @Param("toStatus") int toStatus,
            @Param("reviewer") String reviewer,
            @Param("ruleId") String ruleId,
            @Param("fromStatus") int fromStatus);

    List<String> listAmbiguous();

    List<SynonymRow> listSynonyms();

    List<TypoRow> listTypos();

    List<String> listUncertainMarkers();

    List<KeywordFullRow> listKeywords();

    List<KeywordSpecialtyRow> listKeywordsWithSpecialty();

    List<KeywordBasicRow> listKeywordsBasic();

    List<SpecialtyRow> listSpecialties();

    List<SpecialtyLegacyRow> listSpecialtiesLegacy();

    List<DepartmentRow> listDepartments();

    Integer maxDepartmentCode();

    int insertDepartment(
            @Param("code") int code,
            @Param("name") String name,
            @Param("sortNo") int sortNo,
            @Param("genderScope") int genderScope,
            @Param("childAgeLt") Integer childAgeLt,
            @Param("treatDesc") String treatDesc);

    int insertSpecialty(
            @Param("departmentCode") int departmentCode,
            @Param("name") String name,
            @Param("treatDesc") String treatDesc,
            @Param("audience") int audience);

    Long latestSpecialtyId(@Param("departmentCode") int departmentCode, @Param("name") String name);

    int setSpecialtyEnabled(@Param("enabled") int enabled, @Param("id") long id);

    int renameSpecialty(@Param("name") String name, @Param("id") long id);

    int renameKeywordSpecialty(@Param("id") long id, @Param("name") String name);

    int renameDoctorSpecialty(@Param("id") long id, @Param("name") String name);

    int setSpecialtyAudience(@Param("audience") int audience, @Param("id") long id);

    record DepartmentRow(
        Integer code,
        String name,
        Integer enabled,
        Integer sortNo,
        Integer fallback,
        Integer genderScope,
        Integer childAgeLt,
        String treatDesc) {
}

record SpecialtyRow(
        Long id,
        Integer departmentCode,
        String name,
        String treatDesc,
        Integer enabled,
        Integer audience) {
}

record SpecialtyLegacyRow(Long id, Integer departmentCode, String name, String treatDesc) {
}

record KeywordFullRow(
        Integer departmentCode,
        String keyword,
        Integer kind,
        String specialtyName,
        Integer termType) {
}

record KeywordSpecialtyRow(Integer departmentCode, String keyword, Integer kind, String specialtyName) {
}

record KeywordBasicRow(Integer departmentCode, String keyword, Integer kind) {
}

record TouchedSpecialtyRow(Integer departmentCode, String name) {
}

record TouchedDepartmentRow(Integer code) {
}

record SynonymRow(
        String ruleId,
        String targetTerm,
        String allGroups,
        String noneTerms,
        String pattern,
        Integer status) {
}

    record TypoRow(String fromWord, String toWord) {
    }
}
