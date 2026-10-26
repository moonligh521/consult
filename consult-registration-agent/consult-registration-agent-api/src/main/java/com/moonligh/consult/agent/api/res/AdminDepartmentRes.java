package com.moonligh.consult.agent.api.res;

import java.util.List;

/**
 * 管理端科室目录一行。
 */
public record AdminDepartmentRes(int code, String name, boolean enabled, int specialtyCount, List<String> specialties) {
}
