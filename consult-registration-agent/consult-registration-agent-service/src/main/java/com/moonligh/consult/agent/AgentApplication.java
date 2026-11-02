package com.moonligh.consult.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 挂号咨询服务启动入口。
 * 只扫 agent 包。框架靠 AutoConfiguration.imports 装载，不要扫 com.moonligh.consult。
 */
@SpringBootApplication(scanBasePackages = "com.moonligh.consult.agent")
public class AgentApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
        log.info("======启动成功======");
    }
}
