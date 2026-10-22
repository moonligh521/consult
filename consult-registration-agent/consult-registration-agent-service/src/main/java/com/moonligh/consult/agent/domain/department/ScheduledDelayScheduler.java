package com.moonligh.consult.agent.domain.department;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 延时双删的第二次删除。守护线程，进程退出不阻塞。
 */
public final class ScheduledDelayScheduler implements DelayScheduler, AutoCloseable {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "dept-cache-double-delete");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void schedule(Runnable task, long delayMs) {
        executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
