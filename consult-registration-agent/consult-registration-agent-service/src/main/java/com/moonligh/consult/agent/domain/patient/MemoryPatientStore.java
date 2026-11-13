package com.moonligh.consult.agent.domain.patient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存就诊人。单测和默认装配用，不进生产库。
 */
public class MemoryPatientStore implements PatientStore {

    private final ConcurrentHashMap<String, PatientRecord> byId = new ConcurrentHashMap<>();

    @Override
    public List<PatientRecord> list(String accountId) {
        List<PatientRecord> rows = new ArrayList<>();
        for (PatientRecord row : byId.values()) {
            if (row.accountId().equals(accountId)) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(PatientRecord::isDefault).reversed().thenComparing(PatientRecord::id));
        return rows;
    }

    @Override
    public PatientRecord find(String accountId, String patientId) {
        PatientRecord row = byId.get(patientId);
        if (row == null || !row.accountId().equals(accountId)) {
            return null;
        }
        return row;
    }

    @Override
    public PatientRecord insert(PatientRecord patient) {
        byId.put(patient.id(), patient);
        return patient;
    }

    @Override
    public boolean isEmpty(String accountId) {
        return list(accountId).isEmpty();
    }
}
