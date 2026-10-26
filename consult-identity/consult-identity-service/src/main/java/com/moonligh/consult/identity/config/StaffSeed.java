package com.moonligh.consult.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.moonligh.consult.identity.store.StaffStore;

/**
 * 库里没有工作人员时写入演示管理员。密码来自本机配置。
 */
@Component
public class StaffSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffSeed.class);

    private final StaffStore store;
    private final AuthProperties properties;

    public StaffSeed(StaffStore store, AuthProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (store.count() > 0) {
            return;
        }
        store.insert(properties.getAdminUsername(), properties.getAdminPassword(), "系统管理员");
        log.info("已写入演示管理员 username={}", properties.getAdminUsername());
    }
}
