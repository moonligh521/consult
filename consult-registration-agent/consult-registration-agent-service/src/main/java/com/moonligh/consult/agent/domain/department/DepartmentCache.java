package com.moonligh.consult.agent.domain.department;

/**
 * 科室目录缓存。写路径只删不写，避免把旧快照填回去。
 */
public interface DepartmentCache {

    /** 删掉 Redis 和进程内快照。 */
    void evict();
}
