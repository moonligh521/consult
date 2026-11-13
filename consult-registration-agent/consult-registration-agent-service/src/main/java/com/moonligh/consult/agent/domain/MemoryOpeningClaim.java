package com.moonligh.consult.agent.domain;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** 单进程里的开轮占位。单测和没接数据库时使用。 */
public final class MemoryOpeningClaim implements OpeningClaim {

    private final ConcurrentHashMap<String, Hold> holds = new ConcurrentHashMap<>();

    @Override
    public String claim(String accountId, String text, String consultationId, Instant now) {
        if (accountId == null || accountId.isBlank() || text == null || text.isBlank() || consultationId == null) {
            return consultationId;
        }
        String key = accountId.trim() + "\n" + text.trim();
        Instant at = now == null ? Instant.now() : now;
        Hold fresh = new Hold(consultationId, at);
        while (true) {
            Hold current = holds.putIfAbsent(key, fresh);
            if (current == null) {
                return consultationId;
            }
            if (!current.at.isBefore(at.minus(WINDOW))) {
                return current.consultationId;
            }
            if (holds.replace(key, current, fresh)) {
                return consultationId;
            }
        }
    }

    @Override
    public void replace(String accountId, String text, String consultationId, Instant now) {
        if (accountId == null || text == null || consultationId == null) {
            return;
        }
        Instant at = now == null ? Instant.now() : now;
        holds.put(accountId.trim() + "\n" + text.trim(), new Hold(consultationId, at));
    }

    private record Hold(String consultationId, Instant at) {
    }
}
