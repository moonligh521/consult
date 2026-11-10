package com.moonligh.consult.identity.service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.moonligh.consult.agent.api.enums.Gender;
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

    private static final Duration CREATE_HOLD = Duration.ofSeconds(5);

    private final AccountStore store;
    private final ObjectProvider<StringRedisTemplate> redis;

    public PersonService(AccountStore store, ObjectProvider<StringRedisTemplate> redis) {
        this.store = store;
        this.redis = redis;
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
        String relation = relationOf(request.relation());
        PatientRes existing = findSame(accountId, name, request.gender(), request.age(), relation);
        if (existing != null) {
            return existing;
        }
        String hold = "consult:patient:new:" + accountId + ":" + name + ":" + request.gender() + ":"
                + request.age() + ":" + relation;
        StringRedisTemplate client = redis.getIfAvailable();
        boolean held = client == null || Boolean.TRUE.equals(client.opsForValue().setIfAbsent(hold, "1", CREATE_HOLD));
        if (!held) {
            existing = findSame(accountId, name, request.gender(), request.age(), relation);
            if (existing != null) {
                return existing;
            }
            throw new BusinessException(ErrorCode.CONSULT_IN_PROGRESS);
        }
        existing = findSame(accountId, name, request.gender(), request.age(), relation);
        if (existing != null) {
            return existing;
        }
        PersonRecord saved = store.insertPerson(new PersonRecord(
                UUID.randomUUID().toString(),
                accountId,
                name,
                request.gender(),
                request.age(),
                relation,
                !store.hasPersons(accountId)));
        return saved.toRes();
    }

    private PatientRes findSame(String accountId, String name, Gender gender,
            int age, String relation) {
        for (PersonRecord row : store.listPersons(accountId)) {
            if (row.name().equals(name)
                    && row.gender() == gender
                    && row.age() == age
                    && relation.equals(row.relation())) {
                return row.toRes();
            }
        }
        return null;
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
