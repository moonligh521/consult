package com.moonligh.consult.agent.domain.department;

/**
 * 进程内目录。单测用种子；也可以包一份已加载的快照。
 */
public final class MemoryDepartmentCatalog implements DepartmentCatalog {

    private final DepartmentSnapshot snapshot;

    public MemoryDepartmentCatalog(DepartmentSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static MemoryDepartmentCatalog seed() {
        return new MemoryDepartmentCatalog(DepartmentSeed.load());
    }

    public MemoryDepartmentCatalog withoutEnabled(int code) {
        return new MemoryDepartmentCatalog(snapshot.withoutEnabled(code));
    }

    public MemoryDepartmentCatalog withoutSpecialty(String specialtyName) {
        return new MemoryDepartmentCatalog(snapshot.withoutSpecialty(specialtyName));
    }

    @Override
    public DepartmentSnapshot snapshot() {
        return snapshot;
    }
}
