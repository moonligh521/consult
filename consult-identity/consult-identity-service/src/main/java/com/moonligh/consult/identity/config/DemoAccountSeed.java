package com.moonligh.consult.identity.config;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.identity.domain.AccountRecord;
import com.moonligh.consult.identity.domain.PersonRecord;
import com.moonligh.consult.identity.store.AccountStore;

/**
 * 演示患者账号。登录验证码仍用配置里的 sms-dev-code（默认 000000）。
 */
@Component
@Order(25)
public class DemoAccountSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeed.class);
    public static final String DEMO_MOBILE = "15513981390";

    private final AccountStore accounts;

    public DemoAccountSeed(AccountStore accounts) {
        this.accounts = accounts;
    }

    @Override
    public void run(ApplicationArguments args) {
        AccountRecord account = accounts.findByMobile(DEMO_MOBILE);
        if (account == null) {
            account = accounts.insert(DEMO_MOBILE);
            log.info("已写入演示账号 mobile={}", DEMO_MOBILE);
        }
        if (!accounts.hasPersons(account.id())) {
            accounts.insertPerson(new PersonRecord(
                    UUID.randomUUID().toString(),
                    account.id(),
                    "张明",
                    Gender.MALE,
                    32,
                    "本人",
                    true));
            log.info("已写入演示就诊人 accountId={}", account.id());
        }
    }
}
