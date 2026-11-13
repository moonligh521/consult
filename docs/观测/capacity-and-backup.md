# 观测栈容量门禁与备份恢复

单机 Docker Compose 不是 HA。Langfuse / ClickHouse 不得和核心 MySQL / Redis 抢内存或磁盘。

## 上线前测量

在目标主机执行：

```
sh deploy/compose/capacity-check.sh
```

必须同时满足：可用内存 ≥ 10GB，可用磁盘 ≥ 300GB，逻辑 CPU ≥ 4。任一不满足则不要 `compose up`。

资源预算：

| 组件 | 内存上限 | CPU | 说明 |
| --- | --- | --- | --- |
| otel-collector | 512MB | 1 | 队列满丢弃，不反压挂号 |
| langfuse-web | 1GB | 1 | 分析前台 |
| langfuse-worker | 1GB | 1 | 摄入与导出 |
| clickhouse | 4GB | 2 | 30 天 trace |
| postgres | 1GB | 1 | Langfuse 元数据 |
| redis | 512MB | 0.5 | 仅观测栈，勿复用业务 Redis |
| minio | 512MB | 0.5 | 事件对象 |

告警：备份失败、数据盘使用率 ≥ 80%、ClickHouse 与 MySQL 同盘 IO wait 升高时，先关 `consult.observability.capture-payloads`，再考虑把 ClickHouse、PostgreSQL、对象存储拆出单机。

## 备份

`deploy/compose/backup.sh` 每日跑一次，默认保留 7 天：

- PostgreSQL：`pg_dump`
- ClickHouse：优先 `BACKUP DATABASE`，失败则至少探活
- MinIO：镜像数据目录

密钥不进仓库。恢复前先停 `langfuse-web` / `langfuse-worker`。

## 恢复

1. `docker compose -f deploy/compose/observability-compose.yml --env-file deploy/compose/.env stop langfuse-web langfuse-worker`
2. 恢复 Postgres：`psql` 导入 `postgres.sql`
3. 恢复 ClickHouse 与 MinIO 卷后启动全部服务
4. 用一条已知 `consultation_id` 打开管理端深链，确认 trace 可检索

## 迁移路径

和现有业务 Compose 同机时：观测用独立文件 `observability-compose.yml`，加入已有 `ai-net`，不要把服务写进 MySQL/Redis 那份文件，也不要复用业务 Redis。本地业务把 OTLP 指到 `http://服务器IP:4318/v1/traces`。以后拆机时只改这个地址，不必改挂号代码。
