package com.moonligh.consult.identity.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.moonligh.consult.identity.api.AccountAdminRes;
import com.moonligh.consult.identity.api.AdminPersonRes;
import com.moonligh.consult.identity.store.AccountStore;

/**
 * 管理端查账号和就诊人。
 */
@Service
public class AdminQueryService {

    private final AccountStore store;

    public AdminQueryService(AccountStore store) {
        this.store = store;
    }

    public List<AccountAdminRes> accounts() {
        return store.listAccounts().stream()
                .map(row -> new AccountAdminRes(row.id(), row.mobile(), row.status(), row.personCount(), row.createdAt()))
                .toList();
    }

    public List<AdminPersonRes> patients(String accountId) {
        return store.listAllPersons(accountId).stream()
                .map(row -> new AdminPersonRes(
                        row.id(),
                        row.accountId(),
                        row.mobile(),
                        row.name(),
                        row.gender(),
                        row.age(),
                        row.relation(),
                        row.isDefault()))
                .toList();
    }
}
