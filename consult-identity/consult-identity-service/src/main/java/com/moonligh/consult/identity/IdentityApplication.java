package com.moonligh.consult.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 登录与就诊人服务。
 */
@SpringBootApplication(scanBasePackages = "com.moonligh.consult")
public class IdentityApplication {

    private static final Logger log = LoggerFactory.getLogger(IdentityApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
        log.info("======identity 启动成功======");
    }
}
