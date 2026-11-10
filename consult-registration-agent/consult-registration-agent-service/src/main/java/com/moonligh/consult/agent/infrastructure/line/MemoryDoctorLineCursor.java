package com.moonligh.consult.agent.infrastructure.line;

import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.moonligh.consult.agent.domain.DoctorLineCursor;

/** 单测和单进程用。只向前推进。 */
public final class MemoryDoctorLineCursor implements DoctorLineCursor {

    private final ConcurrentMap<String, Integer> seqs = new ConcurrentHashMap<>();

    @Override
    public OptionalInt read(String consultationId) {
        if (consultationId == null || consultationId.isBlank()) {
            return OptionalInt.empty();
        }
        Integer seq = seqs.get(consultationId.trim());
        return seq == null ? OptionalInt.empty() : OptionalInt.of(seq);
    }

    @Override
    public void publish(String consultationId, int seq) {
        if (consultationId == null || consultationId.isBlank() || seq < 0) {
            return;
        }
        seqs.merge(consultationId.trim(), seq, Math::max);
    }
}
