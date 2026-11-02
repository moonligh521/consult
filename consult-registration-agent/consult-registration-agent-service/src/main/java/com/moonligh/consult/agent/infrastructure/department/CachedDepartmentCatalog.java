package com.moonligh.consult.agent.infrastructure.department;

import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;

/**
 * 定科读这份目录：Redis 校验用快照，miss 再回源数据库。
 */
public class CachedDepartmentCatalog implements DepartmentCatalog {

    private final RedisDepartmentCache cache;
    private final JdbcDepartmentCatalogStore store;

    public CachedDepartmentCatalog(RedisDepartmentCache cache, JdbcDepartmentCatalogStore store) {
        this.cache = cache;
        this.store = store;
    }

    @Override
    public DepartmentSnapshot snapshot() {
        return cache.load(store::load);
    }
}
