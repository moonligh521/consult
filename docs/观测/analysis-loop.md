# 日常分析前台与迭代闭环

主分析界面是自建 Langfuse，不在管理端复制链路树。管理端只提供「查看 Agent 链路」。

## 深链

`GET /api/admin/consultations/{id}/agent-trace` 按 `consultation_id` / 最近 `run_id` 拼 Langfuse 检索地址。

| reason | 提示 |
| --- | --- |
| 未登录或非员工 | 网关直接 1201，前台提示没有权限 |
| `NOT_CONFIGURED` | 未配置 Langfuse 地址或项目 |
| `NO_DECISION` | 还没有分诊决策 |
| `EXPIRED` | 超过 `consult.observability.langfuse.retention-days` |

Langfuse session 等于 `consultation_id`。先看 `agent.turn` 的 Input/Output 和列表名（`no_card · vague_ask · …` / `card · 口腔科 · RULE`），定位这一轮为什么没出卡、要不要下一轮；再下钻 `agent.safety`、`agent.triage`、`agent.route`、`agent.validate`、`agent.model`。属性键和生成摘要用英文，用户原话、对用户提示、科室名保持原文。

## 标注标签

| 标签 | 含义 | 固定动作 |
| --- | --- | --- |
| `vague_loop` | N2 追问后用户仍含糊或流失 | 看追问文案、是否过早放行第二句 |
| `slot_stuck` | 连续多轮停在同一 `missing_slot` | 看就诊人/病程组件 |
| `turn_no_card` | 不是失败；过滤尚未出卡的轮 | 和出卡轮对比 |
| `rule_miss` | 规则已确定但挂错（只打出卡轮） | 改挂词 |
| `model_not_called` | 本该模型却没调（只打出卡轮） | 检查规则确定性 |
| `model_wrong` | 模型原始输出错（只打出卡轮） | 改提示词 |
| `valid_dropped` | 模型输出对但被校验丢掉（只打出卡轮） | 改校验或候选集 |
| `model_unavailable` | 超时、熔断、限流（只打出卡轮） | 改容量 |

每日把已标注样本导出到回归集，对应 `docs/测试/全链路/triage-cases.csv` 的增量，而不是只看准确率汇总。

## 在线旁路 Judge

科室对错仍用回归和人标。话术软项由 agent 服务旁路任务对已入库的 `agent.turn` 打分，不进患者请求，不写 `consult_match_decision`，不进发布门禁，不走 Python。

观察期 `consult.observability.judge.phase=bootstrap` 窗口全量；人和 Judge 同向后改 `steady`（0.2%）。改提示词后用 `recheck` 拉高几天再降回去。开关和密钥见 `docs/观测/judge/README.md`。

| 分数 | 打在哪 | 含义 |
| --- | --- | --- |
| `prompt_grounded` | 抽中的每一轮 `agent.turn` | 对用户的话是否贴这一轮节点 |
| `vague_loop` | 仅 `vague_ask` / `N2_VAGUE` | 已经具体的主诉是否仍在空转追问 |

Langfuse 3.117 不升级。agent 任务走 Scores API；Collector 和挂号热路径不改。密钥只放 local yml。用法见 `docs/观测/judge/README.md`。
