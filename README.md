# 导诊挂号助手

多轮导诊助手，把一句病情描述补全并路由到科室。

本仓库是 [moonligh](https://moonligh.cn/) 的个人开源项目，采用 [Apache-2.0](LICENSE) 许可证。

## 目标

本仓库要完成简历里「智能挂号助手」的五条能力：多轮补槽、规则与向量分诊、可确认的咨询卡片和三档号源、Redis 锁与缓存预热、RocketMQ 事务消息和幂等扣减。完成标准见 [项目目标](docs/product/goals.md)。

## 概述

患者用自然语言描述病情，例如「干咳半年、运动加重」。本项目用状态机做多轮导诊：缺性别、年龄、病程这类信息就继续追问。信息齐了之后抽出症状实体，再按规则和向量检索把症状路由到科室。

确认前生成一张咨询卡片，包含患者画像、病情描述和推荐科室，用户可以修改。卡片确认后，按科室在示例号源里推荐不同层级的资源。仓库里的病情、科室表和号源都是示例，不连接真实挂号系统。

## 状态

仓库已有骨架和社区文件。当前版本线是 V0.1，开发分支是 `feature-V0.1-20261021`。交付范围见 [V0.1 交付基线](docs/product/V0.1-baseline.md)，一轮咨询的输入输出见 [V0.1 一轮咨询](docs/product/V0.1-conversation.md)，第一批代码的构建和包见 [V0.1 应用结构](docs/engineering/V0.1-application.md)。分支和标签见 [分支与版本](docs/engineering/git-workflow.md)。业务代码和构建文件尚未写入。

## 功能范围

- 用状态机补全性别、年龄、病程等槽位
- 把病情描述抽成症状标签，并用规则与向量检索路由到科室
- 生成可确认、可修改的咨询卡片
- 按科室推荐普通、极速、三甲专家三档示例号源
- 用 Redis 分布式锁和缓存预热约束并发扣减
- 用 RocketMQ 事务消息衔接订单、号源扣减和权益校验，并以唯一流水号做幂等消费

## 技术选择

系列仓库选定同一套运行时。本仓库按 [基础设施](docs/engineering/infrastructure.md) 引入工具：代码用到哪一项，构建文件才引用哪一项，并在基础设施层封装。

V0.1 使用 JDK 21 与 Spring Boot 4.1.1。模型接入使用 LangChain4j 调用 DeepSeek。MySQL 8.0.46、Redis 8.2.10、RocketMQ 5.5.1（Java 客户端 rocketmq-spring 2.3.6）和向量库留到各自能力进入代码时再引用。LangChain4j 的版本和 DeepSeek 的模型名也在那时写入基础设施文档。

源码目录按 Maven 标准布局预留。构建文件会和第一批可运行代码一起提交。

## 目录

```text
src/main/java/com/moonligh/registration/
src/main/resources/
src/test/java/com/moonligh/registration/
```

Java 包名是 `com.moonligh.registration`。Redis、消息队列、向量库等封装包在第一次被调用时再建立。

## 数据

病情描述、科室表和对话样例使用合成数据。真实就诊人信息、病历和号源数据不进入仓库。

## 路线图

### V0.1

- [x] 冻结交付基线、验收用例和分支版本规范
- [ ] 补齐构建文件、启动入口和示例配置
- [ ] 用状态机补全性别、年龄、病程
- [ ] 抽出症状标签，并用关键词规则推荐科室
- [ ] 生成可确认、可修改的咨询卡片
- [ ] 按验收用例补测试，并在合并前跑通编译和测试

### 后续版本

- [ ] 用向量检索增强科室路由
- [ ] 按科室推荐普通、极速、三甲专家三档示例号源
- [ ] 用 Redis 分布式锁和缓存预热约束并发扣减
- [ ] 用 RocketMQ 事务消息衔接订单、号源扣减和权益校验，并以唯一流水号做幂等消费

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
