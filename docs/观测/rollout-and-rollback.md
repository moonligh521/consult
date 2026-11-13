# 分阶段发布与回滚

观测回滚只关导出或完整 payload，不回滚 Prometheus、业务审计表或挂号逻辑。

## 阶段

1. 开发：`consult.observability.enabled=true`，`capture-payloads=true`，`management.tracing.export.enabled=true`，Collector 指向本地 Compose。
2. 生产先只采运维元数据：`enabled=true`，`capture-payloads=false`。保留 7 天。
3. 容量门禁连续通过后再开完整输入输出，保留提到 30 天。

`application.yml` 默认打开采集、完整 payload 和 OTLP 导出。Collector 不可用时仍 no-op。Langfuse 地址和密钥只写 `application-local.yml`。

## 回滚

- 关完整原文：`consult.observability.capture-payloads=false`
- 停导出：`management.tracing.export.enabled=false`
- 全程 no-op：`consult.observability.enabled=false`

Collector 或 Langfuse 挂掉时，Batch 队列丢弃，挂号延迟不得上升。用 `deploy/compose/noop-latency-check.md` 的步骤核对。

## 敏感数据

永久排除姓名、手机号、证件号、Authorization、模型密钥。应用侧 `SensitiveAttributeFilter` 与 Collector `attributes/redact` 双层丢弃。
