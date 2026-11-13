package com.moonligh.consult.identity.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.moonligh.consult.identity.domain.AccountRecord;
import com.moonligh.consult.identity.domain.PersonRecord;
import com.moonligh.consult.identity.infrastructure.persistence.AccountMapper;

/**
 * 账号和就诊人的 MySQL 存取。
 */
@Repository
public class AccountStore {

    private final AccountMapper accounts;

    public AccountStore(AccountMapper accounts) {
        this.accounts = accounts;
    }

    public AccountRecord findByMobile(String mobile) {
        List<AccountRecord> rows = accounts.findByMobile(mobile);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AccountRecord insert(String mobile) {
        String id = UUID.randomUUID().toString();
        accounts.insert(id, mobile);
        return new AccountRecord(id, mobile, 1);
    }

    public List<PersonRecord> listPersons(String accountId) {
        return accounts.listPersons(accountId);
    }

    public PersonRecord findPerson(String accountId, String personId) {
        List<PersonRecord> rows = accounts.findPerson(accountId, personId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean hasPersons(String accountId) {
        Integer count = accounts.countPersons(accountId);
        return count != null && count > 0;
    }

    public List<AccountAdminRow> listAccounts() {
        return accounts.listAccounts();
    }

    public List<AdminPersonRow> listAllPersons(String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            return accounts.listAllPersonsByAccount(accountId.trim());
        }
        return accounts.listAllPersons();
    }

    public PersonRecord insertPerson(PersonRecord person) {
        accounts.insertPerson(person.id(), person.name(), person.gender().getCode(), person.age());
        accounts.insertBinding(
                person.accountId(),
                person.id(),
                person.relation(),
                person.isDefault() ? 1 : 0);
        return person;
    }

    public record AccountAdminRow(String id, String mobile, int status, int personCount, String createdAt) {
    }

    public record AdminPersonRow(
            String id,
            String accountId,
            String mobile,
            String name,
            int gender,
            int age,
            String relation,
            boolean isDefault) {
    }
}
