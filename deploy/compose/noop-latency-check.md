# Collector 不可用时的延迟核对

目的：证明观测故障不拖挂号。

1. 停 Collector：`docker compose -f deploy/compose/observability-compose.yml stop otel-collector`
2. 保持 `consult.observability.enabled=true` 和 `management.tracing.export.enabled=true`
3. 对 `/api/consultations` 走现有压测抽 50 条
4. 对比 Collector 正常时的 p95。成功率不变，p95 上升不得超过测量噪声（建议 < 5ms）
5. 看应用日志不应出现挂号线程被 OTLP 导出堵住的堆栈

单元测试 `OtelAgentTraceTest.noopNeverThrows` 覆盖接口降级；本步骤覆盖进程外 Collector 挂掉。
