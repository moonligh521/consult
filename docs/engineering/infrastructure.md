# 基础设施

Redis、消息队列、向量库、数据库和模型客户端按同一条规则进入仓库：代码用到哪一项，构建文件就引用哪一项，引用的同时在基础设施层完成封装。没有调用方的工具不写进依赖，也不预建空包。

系列仓库可以选定同一套版本。每个仓库只引用自己已经调用的那些工具。

## 选定版本

| 能力 | 工具 | 版本 | 本仓库的首次引入时机 |
| --- | --- | --- | --- |
| 应用运行时 | JDK、Spring Boot | JDK 21、Spring Boot 4.1.1 | 第一批可运行代码 |
| 模型调用 | LangChain4j 调用 DeepSeek | 引入该能力时选定 LangChain4j 版本和 DeepSeek 模型名，并写回本表 | 出现从病情文本抽取槽位或症状的类时 |
| 订单与号源结果 | MySQL | 8.0.46 | 出现订单或号源持久化时 |
| 分布式锁与缓存 | Redis | 8.2.10 | 出现分布式锁或缓存预热时 |
| 事务消息 | RocketMQ | 5.5.1，Java 客户端 rocketmq-spring 2.3.6 | 出现订单、号源扣减和权益校验的最终一致链路时 |
| 向量检索 | 向量库 | 引入该能力的改动里选定产品与版本，并写回本表 | 科室路由需要向量检索时 |

V0.1 的槽位、症状标签、科室规则和咨询卡片都在进程内完成。这一版只引用 JDK，以及 [V0.1 应用结构](V0.1-application.md) 里列出的 Spring Boot 启动器。

## 一次改动带齐三件事

引入某个工具的那次改动同时包含：

1. 业务端口：放在使用它的包里，入参和返回值只用本仓库的类型。
2. 构建依赖、配置和客户端创建：只声明这次调用所需要的构件。
3. 封装实现：放在 `com.moonligh.registration.infrastructure` 的对应子包，实现业务端口。

业务代码、领域规则和验收测试只调用业务端口。这些位置不出现该工具的客户端类型，例如 Redis 模板、消息模板、向量存储客户端、数据库访问类型和模型客户端。

封装里完成连接、超时、序列化和失败转换。失败转换成调用方能处理的本仓库类型。客户端类型不越过 `infrastructure` 包。

该能力从产品中移除时，同一次改动删除端口的实现、配置和构建依赖。

## 端口与封装位置

这些名称留给首次引入该工具的改动。V0.1 不创建这些类型。

| 工具 | 业务端口 | 封装包 |
| --- | --- | --- |
| Redis 锁 | `com.moonligh.registration.scheduling.RegistrationLock` | `com.moonligh.registration.infrastructure.redis` |
| Redis 缓存 | `com.moonligh.registration.scheduling.ScheduleCache` | `com.moonligh.registration.infrastructure.redis` |
| RocketMQ | `com.moonligh.registration.order.RegistrationPublisher` | `com.moonligh.registration.infrastructure.mq` |
| 向量库 | `com.moonligh.registration.routing.SymptomIndex` | `com.moonligh.registration.infrastructure.vector` |
| MySQL | 各聚合自己的仓储接口 | `com.moonligh.registration.infrastructure.persistence` |
| LangChain4j | `com.moonligh.registration.conversation.IllnessExtractor` | `com.moonligh.registration.infrastructure.langchain` |

`IllnessExtractor` 只返回抽出的槽位和症状。科室、追问原文和卡片是否可确认仍由业务规则决定。

对象存储、短信或其他后来加入的工具沿用本页：先有调用方，再加依赖，再在 `infrastructure` 下用单独子包封装。

## 测试

验收用例和领域规则使用进程内实现，不连接 Redis、消息队列、向量库或数据库。

封装类自己的测试随该工具一起提交，只验证端口与客户端之间的这一层。
