# agent.turn 旁路 Judge（Java）

患者已经拿到回复之后，由 **consult-registration-agent** 旁路任务抽样评估这一轮对用户说的话。不进挂号热路径，不走 `ModelGateway` 舱壁，不判断科室对错。Python 脚本已去掉。

默认关闭。本机 `application-local.yml` 打开（密钥不要进 git）：

```yaml
consult:
  observability:
    langfuse:
      public-key: pk-lf-consult
      secret-key: sk-lf-consult
    judge:
      enabled: true
      phase: bootstrap   # 稳定后改 steady
```

评分模型复用 `model.api-key` / `model.base-url`，但独立 HTTP，超时 15s，不写 `agent.model`。

| phase | 何时 | 比例 |
| --- | --- | --- |
| `bootstrap` | 观察环境、刚接入（默认） | 窗口内全量 |
| `recheck` | 改了追问或主诉提示词后几天 | 窗口内全量 |
| `steady` | 人和 Judge 大致同向 | 0.2% |

XXL 开启时任务名 `turnJudgeJob`。未开 XXL 时按 `interval-ms`（默认 15 分钟）自跑。
