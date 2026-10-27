package com.moonligh.consult.framework.redis;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 跨实例协调用的 Redisson。懒连接：Redis 起不来时服务照样启动。
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "consult.redisson", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedissonProperties.class)
public class RedissonAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(
            RedissonProperties properties,
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) throws IOException {
        Config config;
        String configFile = properties.getConfigFile();
        if (configFile != null && !configFile.isBlank()) {
            config = Config.fromYAML(new File(configFile));
        } else {
            config = new Config();
            Duration timeout = properties.getTimeout() == null ? Duration.ofSeconds(1) : properties.getTimeout();
            int poolSize = Math.max(1, properties.getPoolSize());
            SingleServerConfig server = config.useSingleServer()
                    .setAddress("redis://" + host + ":" + port)
                    .setTimeout((int) timeout.toMillis())
                    .setConnectionPoolSize(poolSize)
                    .setConnectionMinimumIdleSize(Math.min(8, poolSize));
            if (password != null && !password.isBlank()) {
                server.setPassword(password);
            }
        }
        config.setCodec(StringCodec.INSTANCE);
        config.setLazyInitialization(true);
        return Redisson.create(config);
    }
}
