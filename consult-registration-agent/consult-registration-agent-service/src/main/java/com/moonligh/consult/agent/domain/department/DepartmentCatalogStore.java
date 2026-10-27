package com.moonligh.consult.agent.domain.department;

import java.util.List;

/**
 * 科室目录的数据库写入口。读仍走快照加载。
 */
public interface DepartmentCatalogStore {

    /** 开通或关闭一个科室。 */
    void setEnabled(int code, boolean enabled);

    /** 匹配用快照。含错别字和同义。 */
    default DepartmentSnapshot load() {
        throw new UnsupportedOperationException("load");
    }

    /** 含停用一级/二级的原始目录，管理端用。 */
    default List<DepartmentRecord> loadRecords() {
        throw new UnsupportedOperationException("loadRecords");
    }

    default int insertDepartment(String name, int genderScope, Integer childAgeLt, String treatDesc) {
        throw new UnsupportedOperationException("insertDepartment");
    }

    default long insertSpecialty(int departmentCode, String name, String treatDesc) {
        throw new UnsupportedOperationException("insertSpecialty");
    }

    default void setSpecialtyEnabled(long specialtyId, boolean enabled) {
        throw new UnsupportedOperationException("setSpecialtyEnabled");
    }

    default void renameSpecialty(long specialtyId, String name) {
        throw new UnsupportedOperationException("renameSpecialty");
    }

    /** 写入或覆盖一条挂号错别字。 */
    default void upsertTypo(MatchTypo typo) {
        throw new UnsupportedOperationException("upsertTypo");
    }

    /** 写入一条不确定定科标记。 */
    default void upsertUncertainMarker(String marker) {
        throw new UnsupportedOperationException("upsertUncertainMarker");
    }

    /** 写入一条关键词。科室必须已开通。 */
    default void upsertKeyword(int departmentCode, String keyword, KeywordKind kind) {
        throw new UnsupportedOperationException("upsertKeyword");
    }

    /** 写入一条歧义症状。 */
    default void upsertAmbiguous(String phrase) {
        throw new UnsupportedOperationException("upsertAmbiguous");
    }

    /** 新建或修改一条 DRAFT 同义规则。非 DRAFT 不能改。 */
    default void saveSynonym(CanonSynonym rule) {
        throw new UnsupportedOperationException("saveSynonym");
    }

    /** 同义规则状态迁移。from 对不上就失败。 */
    default void setSynonymStatus(String ruleId, SynonymStatus from, SynonymStatus to, String reviewer) {
        throw new UnsupportedOperationException("setSynonymStatus");
    }
}
