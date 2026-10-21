# 参与贡献

感谢你关注 `registration-assistant`。这个仓库目前是骨架，问题和实现建议都很有用。

## 开始之前

先看 [README.md](README.md) 里的功能范围。已经有人在做的事，请先到对应 Issue 下面留言，避免两个人写同一块。

较大的改动先开 Issue 说明目标和做法，再提交代码。小的文档修正可以直接提 Pull Request。

## 开发约定

- 目标运行时是 JDK 21。应用栈是 Spring Boot 4.1.1 与 Spring AI 2.0.1。数据组件是 MySQL 8.0.46、Redis 8.2.10、RocketMQ 5.5.1。
- 包名使用 `com.moonligh.registration`，新代码放在这个包及其子包下。
- 仓库里的项目归属写 moonligh，主页是 <https://moonligh.cn/>。
- 示例使用合成数据或可公开引用的文本。
- 密钥、令牌、真实病历、真实合同和客户材料不进入仓库，包括 Issue 和提交说明。

## 提交说明

提交说明使用 [Conventional Commits](https://www.conventionalcommits.org/)：

```text
feat: 补充科室路由的示例规则
fix: 修正咨询卡片漏掉年龄的问题
docs: 更新本地运行步骤
test: 补上槽位缺失时的追问用例
refactor: 拆分检索结果合并
chore: 调整忽略规则
```

标题用现在时，说清这次提交做了什么。

## 分支与 Pull Request

功能分支使用 `feat/简短英文名`，修复使用 `fix/简短英文名`。

Pull Request 请写明：

- 解决了哪个问题
- 行为有什么变化
- 怎样验证
- 有没有新增示例数据

维护者可能直接修改提交说明，或请你补测试。还不能运行的骨架阶段，文档和目录改动可以在说明里写「当前仓库尚无可执行测试」。

## 版本

版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。对外可见的变化写入 [CHANGELOG.md](CHANGELOG.md) 的 `Unreleased` 一节，发布时再归入具体版本。

## 许可

提交到本仓库的贡献，按 [Apache-2.0](LICENSE) 授权给本项目。除非贡献者在提交中明确标明不属于贡献，否则适用该许可证第 5 条。
