package com.moonligh.consult.agent.api.res;

import java.util.List;

/**
 * 患者选科树或管理端目录树的一个一级类。
 */
public record DepartmentTreeRes(
        int code,
        String name,
        boolean enabled,
        boolean fallback,
        List<SpecialtyNodeRes> specialties) {
}
