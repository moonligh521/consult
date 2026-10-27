package com.moonligh.consult.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 身份服务登录配置（短信、演示管理员）。JWT 过滤器在框架里。
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {
}
