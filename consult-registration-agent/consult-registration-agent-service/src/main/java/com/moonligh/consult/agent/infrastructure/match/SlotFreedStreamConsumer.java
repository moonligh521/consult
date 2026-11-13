package com.moonligh.consult.agent.infrastructure.match;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import com.moonligh.consult.agent.service.MatchDispatcher;
import com.moonligh.consult.framework.redis.ConsultRedis;
import com.moonligh.consult.framework.redis.RedisStreams;
import com.moonligh.consult.identity.api.SlotFreed;

/**
 * 消费 Redis Stream 放号。消费组 consult-match，失败不 ACK，对账任务兜底。
 */
public class SlotFreedStreamConsumer implements SmartLifecycle {

    public static final String STREAM = ConsultRedis.SLOT_FREED_STREAM;
    public static final String GROUP = ConsultRedis.SLOT_FREED_GROUP;

    private static final Logger log = LoggerFactory.getLogger(SlotFreedStreamConsumer.class);

    private final StringRedisTemplate redis;
    private final RedisStreams streams;
    private final MatchDispatcher dispatcher;
    private final String consumerName;

    private volatile boolean running;
    private Thread worker;

    public SlotFreedStreamConsumer(StringRedisTemplate redis, RedisStreams streams, MatchDispatcher dispatcher) {
        this.redis = redis;
        this.streams = streams;
        this.dispatcher = dispatcher;
        this.consumerName = host() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        ensureGroup();
        running = true;
        worker = new Thread(this::loop, "slot-freed-stream");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        StreamOperations<String, String, String> stream = redis.opsForStream();
        Consumer consumer = Consumer.from(GROUP, consumerName);
        StreamReadOptions options = StreamReadOptions.empty().count(50).block(Duration.ofSeconds(2));
        while (running) {
            try {
                List<MapRecord<String, String, String>> batch = stream.read(
                        consumer,
                        options,
                        StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
                if (batch == null || batch.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, String, String> record : batch) {
                    if (handle(record)) {
                        stream.acknowledge(STREAM, GROUP, record.getId());
                    }
                }
            } catch (RuntimeException error) {
                if (!running) {
                    return;
                }
                log.warn("放号 Stream 读取失败 type={}", error.getClass().getSimpleName());
                sleepQuietly(1000);
            }
        }
    }

    private boolean handle(MapRecord<String, String, String> record) {
        SlotFreed notice = SlotFreed.fromStream(record.getValue());
        if (notice == null) {
            return true;
        }
        try {
            dispatcher.onSlotFreed(notice.specialty(), notice.grade());
            return true;
        } catch (RuntimeException error) {
            log.warn("放号出队失败 specialty={} grade={}", notice.specialty(), notice.grade());
            return false;
        }
    }

    private void ensureGroup() {
        streams.ensureGroup(STREAM, GROUP);
    }

    private static String host() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception error) {
            return "agent";
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
