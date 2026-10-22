package com.moonligh.consult.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 挂号咨询服务启动入口。
 * 放在 agent 根包，Spring 才能扫到下面的 controller、service、config、infrastructure。
 */
@SpringBootApplication(scanBasePackages = "com.moonligh.consult")
public class AgentApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
        log.info("======启动成功======");
    }
}
