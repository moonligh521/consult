package com.moonligh.consult.framework;

/**
 * 当前请求的标准 trace id。有 OTel 时对齐 W3C，没有则空，由 {@link TraceIdFilter} 回退到请求头或新编号。
 */
@FunctionalInterface
public interface TraceIdSource {

    TraceIdSource NONE = () -> null;

    String currentTraceId();
}
