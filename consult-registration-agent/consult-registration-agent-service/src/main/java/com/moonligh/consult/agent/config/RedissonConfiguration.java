package com.moonligh.consult.agent.config;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.consult.agent.infrastructure.lock.RedissonConsultationGuard;
import com.moonligh.consult.agent.service.ConsultationGuard;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 跨实例协调用的 Redisson：模型全局并发配额、同一会话单飞。
 * 懒连接：Redis 起不来时服务照样启动，用到的地方按各自兜底放行。
 * 生产用哨兵或集群时给 consult.redisson.config-file 指一份 Redisson YAML，忽略下面的单机参数。
 */
@Configuration
@ConditionalOnProperty(prefix = "consult.redisson", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${consult.redisson.config-file:}") String configFile,
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${consult.redisson.timeout:1s}") Duration timeout,
            @Value("${consult.redisson.pool-size:64}") int poolSize) throws IOException {
        Config config;
        if (configFile != null && !configFile.isBlank()) {
            config = Config.fromYAML(new File(configFile));
        } else {
            config = new Config();
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

    /** 租期要盖住一次请求最坏耗时：模型排队 1.5s + 安全模型两次 5s + 主诉 2.8s + 派单 5s。 */
    @Bean
    public ConsultationGuard consultationGuard(
            RedissonClient redisson,
            @Value("${consult.guard.lease:20s}") Duration lease,
            MeterRegistry meters) {
        return new RedissonConsultationGuard(redisson, lease, meters);
    }
}
