package com.moonligh.consult.agent.domain.department;

/**
 * 延时执行。生产用线程池，单测用手拨。
 */
public interface DelayScheduler {

    void schedule(Runnable task, long delayMs);
}
