package com.moonligh.consult.agent.domain.department;

/**
 * 科室目录缓存。变更后回填新快照，避免空窗回源。
 */
public interface DepartmentCache {

    /** 删掉 Redis 和进程内快照。手工改库时用。 */
    void evict();

    /** 覆盖写入新快照。变更写库成功后调用。 */
    default void put(DepartmentSnapshot snapshot) {
        evict();
    }
}
