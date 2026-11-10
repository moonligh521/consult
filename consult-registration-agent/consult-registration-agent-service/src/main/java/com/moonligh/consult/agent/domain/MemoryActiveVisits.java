package com.moonligh.consult.agent.domain;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内占位。单测和未接数据库时使用。多实例以数据库主键为准。
 */
public final class MemoryActiveVisits implements ActiveVisits {

    private final ConcurrentHashMap<String, String> holder = new ConcurrentHashMap<>();

    @Override
    public boolean busy(String accountId, String patientId, String consultationId) {
        String current = holder.get(key(accountId, patientId));
        return current != null && !current.equals(consultationId);
    }

    @Override
    public boolean claim(String accountId, String patientId, String consultationId) {
        String key = key(accountId, patientId);
        String previous = holder.putIfAbsent(key, consultationId);
        return previous == null || previous.equals(consultationId);
    }

    @Override
    public void release(String accountId, String patientId, String consultationId) {
        holder.remove(key(accountId, patientId), consultationId);
    }

    private static String key(String accountId, String patientId) {
        return accountId + "\n" + patientId;
    }
}
