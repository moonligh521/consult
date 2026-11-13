package com.moonligh.benefit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 权益服务启动入口。只扫 com.moonligh.benefit。 */
@MapperScan(basePackages = "com.moonligh.benefit", annotationClass = org.apache.ibatis.annotations.Mapper.class)
@SpringBootApplication(scanBasePackages = "com.moonligh.benefit")
public class BenefitApplication {

    private static final Logger log = LoggerFactory.getLogger(BenefitApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(BenefitApplication.class, args);
        log.info("======启动成功======");
    }
}
