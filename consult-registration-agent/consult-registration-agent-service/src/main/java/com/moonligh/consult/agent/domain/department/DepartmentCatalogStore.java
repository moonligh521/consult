package com.moonligh.consult.agent.domain.department;

/**
 * 科室目录的数据库写入口。读仍走快照加载。
 */
public interface DepartmentCatalogStore {

    /** 开通或关闭一个科室。 */
    void setEnabled(int code, boolean enabled);

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
