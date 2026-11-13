package com.moonligh.consult.agent.infrastructure.patient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.infrastructure.persistence.PatientMapper;
import com.moonligh.consult.agent.infrastructure.persistence.PatientMapper.PatientRow;
import com.moonligh.consult.agent.domain.patient.PatientRecord;
import com.moonligh.consult.agent.domain.patient.PatientStore;

/**
 * 旧扁平就诊人表。生产改走身份服务，单测仍可用内存实现。
 */
public class PatientStoreImpl implements PatientStore {

    private final PatientMapper patients;

    public PatientStoreImpl(PatientMapper patients) {
        this.patients = patients;
    }

    @Override
    public List<PatientRecord> list(String accountId) {
        List<PatientRow> rows = patients.list(accountId);
        List<PatientRecord> records = new ArrayList<>(rows.size());
        for (PatientRow row : rows) {
            records.add(toRecord(row));
        }
        return records;
    }

    @Override
    public PatientRecord find(String accountId, String patientId) {
        List<PatientRow> rows = patients.find(patientId, accountId);
        return rows.isEmpty() ? null : toRecord(rows.get(0));
    }

    @Override
    public PatientRecord insert(PatientRecord patient) {
        patients.insert(
                patient.id(),
                patient.accountId(),
                patient.name(),
                patient.gender().getCode(),
                patient.age(),
                patient.relation(),
                patient.isDefault() ? 1 : 0);
        return patient;
    }

    @Override
    public boolean isEmpty(String accountId) {
        Integer count = patients.countByAccount(accountId);
        return count == null || count == 0;
    }

    private static PatientRecord toRecord(PatientRow row) {
        Instant updatedAt = row.updatedAt() == null ? Instant.EPOCH : row.updatedAt();
        return new PatientRecord(
                row.id(),
                row.accountId(),
                row.name(),
                Gender.fromCode(nz(row.gender())),
                nz(row.age()),
                row.relation(),
                nz(row.isDefault()) == 1,
                Long.toString(updatedAt.toEpochMilli()),
                updatedAt);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
