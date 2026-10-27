# 导诊评测

开发阶段门禁：

```text
python docs/测试/评测/run_eval.py
python docs/测试/评测/run_eval.py --only behavior
```

部署到正式环境之后、放量之前：

```text
python docs/测试/评测/run_probe.py --base https://正式网关
```

探针使用专用账号，不支付、不占号。故障注入和全链路回放不打正式环境。10 科口语集已退出行为线。

`reliability-cases.csv` 已冻结 100 条（safety_model 40 / complaint_model 40 / multi_turn 20）。未加 `--live` 不计分；三次重跑尚未接入运行器。
