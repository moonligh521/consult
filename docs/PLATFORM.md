# 脚手架依赖说明（架构）

本仓库不再内嵌 framework。

统一返回、错误码、鉴权、Redis、MQ、网关横切、下游韧性拦截、单飞锁等由独立仓 **moonligh-platform** 提供（Maven 坐标 `com.moonligh:platform-*`）。

## 本仓如何引用

| 模块 | 平台依赖 | 本仓只留 |
| --- | --- | --- |
| `consult-gateway` | `platform-gateway` | 路由、`consult.gateway.*-uri`（含 nutritionist）、产品 CORS |
| `consult-identity` / agent | `platform-common` / `security` / `redis` / … | 业务服务；`ConsultationGuard` 装配 `ExclusiveGuard`；`ResilienceConfiguration` 数值 |
| `consult-payment` / `benefit` | `platform-security`（`SharedSecretHeaderFilter`） | 渠道与账本业务 |

营养服务走本仓网关上的 `/api/nutrition/**`，复用同一套 `moonligh.gateway` 限流与 JWT。

## 边界

| 仓 | 职责 |
| --- | --- |
| `moonligh-platform` | 公司脚手架 |
| `registration-assistant` | 账号、患者主数据、产品网关路由、支付/权益/导诊 |
| `nutritionist` | 营养业务；患者只存引用 |

业务仓之间不互为 jar 依赖；协作走 HTTP / 事件。
