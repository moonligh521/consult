package com.moonligh.consult.agent.service;

import java.time.Duration;

/** 连点窗口。超过这个时间，同一句话按新的一句处理。 */
final class RepeatWindow {

    static final Duration SPAN = Duration.ofSeconds(8);

    private RepeatWindow() {
    }
}
