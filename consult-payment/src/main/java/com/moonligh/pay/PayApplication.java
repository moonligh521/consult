package com.moonligh.pay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 支付服务启动入口。只扫 com.moonligh.pay，拷走这个模块就能单独跑。
 */
@SpringBootApplication(scanBasePackages = "com.moonligh.pay")
public class PayApplication {

    private static final Logger log = LoggerFactory.getLogger(PayApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PayApplication.class, args);
        log.info("======启动成功======");
    }
}
