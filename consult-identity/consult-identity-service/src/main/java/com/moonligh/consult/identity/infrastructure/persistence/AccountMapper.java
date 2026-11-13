package com.moonligh.consult.identity.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moonligh.consult.identity.domain.AccountRecord;
import com.moonligh.consult.identity.domain.PersonRecord;
import com.moonligh.consult.identity.infrastructure.persistence.AccountStore.AccountAdminRow;
import com.moonligh.consult.identity.infrastructure.persistence.AccountStore.AdminPersonRow;

@Mapper
public interface AccountMapper {

    List<AccountRecord> findByMobile(@Param("mobile") String mobile);

    void insert(@Param("id") String id, @Param("mobile") String mobile);

    List<PersonRecord> listPersons(@Param("accountId") String accountId);

    List<PersonRecord> findPerson(@Param("accountId") String accountId, @Param("personId") String personId);

    Integer countPersons(@Param("accountId") String accountId);

    List<AccountAdminRow> listAccounts();

    List<AdminPersonRow> listAllPersons();

    List<AdminPersonRow> listAllPersonsByAccount(@Param("accountId") String accountId);

    void insertPerson(
            @Param("id") String id,
            @Param("name") String name,
            @Param("gender") int gender,
            @Param("age") int age);

    void insertBinding(
            @Param("accountId") String accountId,
            @Param("personId") String personId,
            @Param("relation") String relation,
            @Param("isDefault") int isDefault);
}
