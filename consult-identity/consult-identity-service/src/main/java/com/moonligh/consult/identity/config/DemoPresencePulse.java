package com.moonligh.consult.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.moonligh.consult.identity.infrastructure.persistence.DoctorStore;

/**
 * 演示环境没有医生端。每 5 秒给已经在线的人续一次心跳，避免 15 秒后全部派不出去。
 */
@Component
@ConditionalOnProperty(prefix = "consult.doctor", name = "demo-heartbeat", havingValue = "true")
public class DemoPresencePulse implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoPresencePulse.class);

    private final DoctorStore doctors;

    public DemoPresencePulse(DoctorStore doctors) {
        this.doctors = doctors;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("demo-presence").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    doctors.refreshPresentBeats();
                    Thread.sleep(5_000);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException error) {
                    log.warn("演示心跳续期失败");
                    try {
                        Thread.sleep(5_000);
                    } catch (InterruptedException stopped) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }
}
