package com.moonligh.consult.identity.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.moonligh.consult.agent.api.req.PatientCreateReq;
import com.moonligh.consult.agent.api.res.PatientRes;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.domain.PersonRecord;
import com.moonligh.consult.identity.store.AccountStore;

/**
 * 当前账号下的就诊人：列表、新建、按编号取。
 */
@Service
public class PersonService {

    private static final Set<String> RELATIONS = Set.of("本人", "配偶", "子女", "父母", "其他");

    private final AccountStore store;

    public PersonService(AccountStore store) {
        this.store = store;
    }

    public List<PatientRes> list(String accountId) {
        return store.listPersons(accountId).stream().map(PersonRecord::toRes).toList();
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
        PersonRecord saved = store.insertPerson(new PersonRecord(
                UUID.randomUUID().toString(),
                accountId,
                name,
                request.gender(),
                request.age(),
                relationOf(request.relation()),
                !store.hasPersons(accountId)));
        return saved.toRes();
    }

    public PatientRes require(String accountId, String personId) {
        PersonRecord person = store.findPerson(accountId, personId);
        if (person == null) {
            throw new BusinessException(ErrorCode.PATIENT_NOT_FOUND);
        }
        return person.toRes();
    }

    private String relationOf(String relation) {
        if (relation == null || relation.isBlank()) {
            return "其他";
        }
        String value = relation.trim();
        return RELATIONS.contains(value) ? value : "其他";
    }
}
