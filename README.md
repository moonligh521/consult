# 导诊挂号助手

多轮导诊助手，把一句病情描述补全并路由到科室。

本仓库是 [moonligh](https://moonligh.cn/) 的个人开源项目，采用 [Apache-2.0](LICENSE) 许可证。

## 概述

患者用自然语言描述病情，例如「干咳半年、运动加重」。本项目用状态机做多轮导诊：缺性别、年龄、病程这类信息就继续追问。信息齐了之后抽出症状实体，再按规则和向量检索把症状路由到科室。

确认前生成一张咨询卡片，包含患者画像、病情描述和推荐科室，用户可以修改。卡片确认后，按科室在示例号源里推荐不同层级的资源。仓库里的病情、科室表和号源都是示例，不连接真实挂号系统。

## 状态

当前提交的是仓库骨架和社区文件。包名、目录和文档已经按开源仓库放好，业务代码和构建文件尚未写入。下面的功能范围是这个仓库要完成的事，还不是已经可运行的能力。

## 功能范围

- 用状态机补全性别、年龄、病程等槽位
- 把病情描述抽成症状标签，并用规则与向量检索路由到科室
- 生成可确认、可修改的咨询卡片
- 按科室推荐示例号源层级
- 用 Redis 分布式锁和缓存预热约束并发扣减
- 用 RocketMQ 事务消息衔接订单、号源扣减和权益校验，并以唯一流水号做幂等消费

## 技术选择

五个仓库使用同一套运行时，模型接入使用 Spring AI。

- JDK 21
- Spring Boot 4.1.1
- Spring AI 2.0.1
- MySQL 8.0.46
- Redis 8.2.10
- RocketMQ 5.5.1，Java 客户端使用 rocketmq-spring 2.3.6

在本仓库中，Spring AI 负责多轮追问、症状抽取和咨询卡片。MySQL 保存订单与号源扣减结果。Redis 负责分布式锁和放号前的缓存预热。RocketMQ 用事务消息把订单生成、号源扣减和权益校验收成一条最终一致的链路。

源码目录按 Maven 标准布局预留。构建文件会和第一批可运行代码一起提交。

## 目录

```text
src/main/java/com/moonligh/registration/
src/main/resources/
src/test/java/com/moonligh/registration/
```

Java 包名是 `com.moonligh.registration`。

## 数据

病情描述、科室表和对话样例使用合成数据。真实就诊人信息、病历和号源数据不进入仓库。

## 路线图

- [ ] 用状态机补全性别、年龄、病程等槽位
- [ ] 把病情描述抽成症状标签，并用规则与向量检索路由到科室
- [ ] 生成可确认、可修改的咨询卡片
- [ ] 按科室推荐示例号源层级
- [ ] 用 Redis 分布式锁和缓存预热约束并发扣减
- [ ] 用 RocketMQ 事务消息衔接订单、号源扣减和权益校验，并以唯一流水号做幂等消费
- [ ] 补齐构建文件、启动入口和示例
- [ ] 为核心路径补测试

## 声明

本项目提供的是健康信息整理，不能替代执业医师的诊断、分诊或挂号建议。

## 系列仓库

- `report-interpreter`：报告解读
- `nutritionist`：饮食方案
- `case-analysis`：案情分析
- `contract-review`：合同审查

## 参与贡献

贡献方式见 [CONTRIBUTING.md](CONTRIBUTING.md)，行为准则见 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。安全问题见 [SECURITY.md](SECURITY.md)，请不要把漏洞细节写进公开 Issue。

## 许可证

Copyright 2026 moonligh

本项目使用 Apache License 2.0，全文见 [LICENSE](LICENSE)。
