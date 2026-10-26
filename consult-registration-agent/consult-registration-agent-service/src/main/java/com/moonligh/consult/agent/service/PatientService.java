package com.moonligh.consult.agent.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;


import com.moonligh.consult.agent.api.req.PatientCreateReq;
import com.moonligh.consult.agent.api.res.PatientRes;
import com.moonligh.consult.agent.domain.patient.AccountIds;
import com.moonligh.consult.agent.domain.patient.PatientRecord;
import com.moonligh.consult.agent.domain.patient.PatientStore;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 单测用就诊人：列表和新建。生产走身份服务。
 */
public class PatientService {

    private static final Set<String> RELATIONS = Set.of("本人", "配偶", "子女", "父母", "其他");

    private final PatientStore store;

    public PatientService(PatientStore store) {
        this.store = store;
    }

    public List<PatientRes> list(String accountId) {
        return store.list(accountOf(accountId)).stream().map(PatientRecord::toRes).toList();
    }

    public PatientRes create(String accountId, PatientCreateReq request) {
        if (request == null || request.gender() == null || request.age() == null) {
            throw new BusinessException(ErrorCode.INVALID_PATIENT);
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 16) {
            throw new BusinessException(ErrorCode.INVALID_PATIENT);
        }
        if (request.age() < 1 || request.age() > 120) {
            throw new BusinessException(ErrorCode.INVALID_AGE);
        }
        String account = accountOf(accountId);
        PatientRecord saved = store.insert(new PatientRecord(
                UUID.randomUUID().toString(),
                account,
                name,
                request.gender(),
                request.age(),
                relationOf(request.relation()),
                store.isEmpty(account)));
        return saved.toRes();
    }

    private String accountOf(String accountId) {
        return AccountIds.orDefault(accountId);
    }

    private String relationOf(String relation) {
        if (relation == null || relation.isBlank()) {
            return "其他";
        }
        String value = relation.trim();
        return RELATIONS.contains(value) ? value : "其他";
    }
}
