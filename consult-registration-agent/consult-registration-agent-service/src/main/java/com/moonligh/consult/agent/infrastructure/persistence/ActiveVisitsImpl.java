package com.moonligh.consult.agent.infrastructure.persistence;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.agent.domain.ActiveVisits;

/**
 * 进行中的问诊落在主键 (account_id, patient_id) 上。两个实例同时支付，只有一行能插进去。
 */
@Repository
public class ActiveVisitsImpl implements ActiveVisits {

    private final ActiveVisitMapper visits;

    public ActiveVisitsImpl(ActiveVisitMapper visits) {
        this.visits = visits;
    }

    @Override
    public boolean busy(String accountId, String patientId, String consultationId) {
        String holder = find(accountId, patientId);
        return holder != null && !holder.equals(consultationId);
    }

    @Override
    public boolean claim(String accountId, String patientId, String consultationId) {
        try {
            visits.insert(accountId, patientId, consultationId);
            return true;
        } catch (DuplicateKeyException existing) {
            return consultationId.equals(find(accountId, patientId));
        }
    }

    @Override
    public void release(String accountId, String patientId, String consultationId) {
        visits.release(accountId, patientId, consultationId);
    }

    private String find(String accountId, String patientId) {
        List<String> rows = visits.find(accountId, patientId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
