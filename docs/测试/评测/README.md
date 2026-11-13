# 导诊评测

门禁在 Java 单测里，跟 `mvn verify` 一起跑。本目录只放用例。

- 行为 / 下单：`OrderStopEvalTest`、`SafetyInvariantsTest`
- 硬禁：`HardBanEvalTest`（读 `quality-cases.csv`）
- 对抗：`AdversarialEvalTest`（读 `adversarial-cases.csv`）
- 故障：`FaultEvalTest`（读 `fault-cases.csv`）

`bans.txt` 仍给硬禁词表用。全链路样本在 `docs/测试/全链路/`。
