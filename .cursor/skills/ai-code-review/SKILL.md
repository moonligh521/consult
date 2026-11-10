---
name: ai-code-review
description: >
  Use when the user asks for AI code review, independent review of a diff or PR,
  Claude-style review, /ai-code-review, 审查代码, 审查这个 diff, 独立审查,
  按审核流程审查, or to check a change before opening or merging a PR.
  Do not use when the user only wants CodeRabbit CLI.
---

# AI Code Review

独立审查者，只读 diff。不修改文件，不跑 CodeRabbit，不把风格偏好当缺陷。

对应 Claude Code 流程里 Cursor **能执行** 的层：提交前验证、全新上下文审查、安全审查、轮次收敛。合并策略、发布渠道、线上回滚只写入「人工门控」，不要假装已执行。

## Recursion guard

If the prompt contains `INDEPENDENT_REVIEWER=1`, you **are** the reviewer. Execute [Review Contract](#review-contract) and return the report. Do **not** spawn another reviewer.

Otherwise you are the **orchestrator**. Follow [Orchestrator](#orchestrator) exactly.

## When NOT to use

- User said only `coderabbit` / `CodeRabbit` / `coderabbit review` → use that skill instead.
- User asked to implement or fix (not review) → do not switch into this workflow.

## Orchestrator

Copy and track:

```
Review Progress:
- [ ] 1. Scope
- [ ] 2. Evidence
- [ ] 3. Independent review
- [ ] 4. Verify findings
- [ ] 5. Report
```

### 1. Scope

Resolve the diff. Default: uncommitted + staged vs `HEAD`. If user names a PR or branch, diff that against `origin/main`; if that ref is missing, try `main`, then `master`.

```powershell
git rev-parse --abbrev-ref HEAD
git rev-parse --verify origin/main
git merge-base HEAD origin/main
git diff --stat --numstat <base>...HEAD
git diff --stat --numstat HEAD
git diff --stat --numstat --cached
```

Classify type from the diff + user text: `feature` | `bugfix` | `refactor` | `config/infra` | `test` | `docs`.

Read [repo-risk.md](repo-risk.md). Mark `HIGH_RISK=yes` if any changed path matches that list.

**Split, do not abort:** if changed files > 20 **or** added+deleted lines > 800, review **one Maven/frontend module at a time** (order in repo-risk.md). One report per module, then a one-paragraph rollup. Never sample a large PR.

Count prior reports in this chat titled `AI Code Review Report`. That count + 1 is `ROUND`.

- `ROUND >= 4` → do not spawn. If Important/Security remain open, output `HUMAN_ESCALATION_REQUIRED`; otherwise `审查收敛`. Stop.
- `ROUND >= 2` → re-review **only hunks new since the previous report**. Do not rescan unchanged lines. Do not repeat prior findings.

### 2. Evidence

Run what the change can actually run. Do not claim tests passed without the command output.

- Touched Java module: `mvn -B -pl <module> -am test`
- Touched `consult-admin-web`: `npm --prefix consult-admin-web run build`
- UI behavior change and browser tools exist: exercise the flow; keep a screenshot or console error as evidence
- Command failed or was skipped → `EVIDENCE_MISSING` (downgrade overall confidence one notch; do not invent results)

### 3. Independent review (mandatory)

Always spawn a **new** `generalPurpose` subagent (`run_in_background: false`). Do not review the diff in this session, even if this chat looks “fresh”. Do not spawn `code-reviewer` or `security-review` (those are CodeRabbit). This skill owns the review; do not also run CodeRabbit.

Subagent prompt **must** include:

1. Literal token `INDEPENDENT_REVIEWER=1`
2. `ROUND=<n>` and, if `ROUND>=2`, the previous findings list plus “review only the new hunks”
3. Exact git commands and `<base>` from step 1 (and the module path if split)
4. `HIGH_RISK=yes|no`
5. User’s stated intent / ticket / PR body (verbatim, no interpretation from the writing session)
6. “Read `.cursor/skills/ai-code-review/SKILL.md` and `repo-risk.md`. Execute Review Contract. Do not modify files. Do not use CodeRabbit. Return only the report template.”

### 4. Verify findings

Before showing the report to the user:

- Drop any finding without `path:line`
- Drop style/naming/comment-only items unless they cause a real bug or misleading API
- Drop findings against lines **not** in the current review hunks
- Open each Important/Security citation; if the line does not support the claim, drop it or (Important only) move to Nit with the uncertainty stated
- Security stays Security. Close as false-positive with a one-line reason, or keep. Never rename it Nit. Do not copy Security items into Important.

### 5. Report

Fill [Output](#output) with no extra sections. Do not edit the reviewed files. Suggestions may appear under **建议**; implementing them requires a new user request.

## Review Contract

(Reviewer only. Orchestrator does not skip the subagent by doing this locally.)

Declare this sentence first in the report:

> 本次审查基于独立审查上下文执行。不掌握写代码 Agent 的意图或先前结论。发现仅来自 diff 与仓库当前代码。

Work in this order. One pass. These are **sections**, not extra review rounds.

1. **Goal / fit / blast radius** (required)
   - Does the diff achieve the stated intent?
   - Does it match existing patterns in the same module (errors, DI, API shape)?
   - What else can it break (callers, tests, configs, sibling modules)?
   - Bugfix: verify the root cause, not the symptom.
2. **Security** — if `HIGH_RISK=yes`, complete the security checklist in repo-risk.md; if `no`, still scan new external input, authz, secrets, SQL/command/template concatenation. Open security issues **block merge**.
3. **Leftover defects** — null/empty/zero, swallowed errors, missing enum branches, invented APIs, tests with no assertions.

Tag every surviving finding. Put each item in **exactly one** section:

| Tag | Use |
|---|---|
| Important | Must answer before merge (bug, missing requirement, harmful blast radius) |
| Security | Authn/authz, injection, secrets, PII — blocks merge; never file as Important or Nit |
| Nit | Real but non-blocking |
| Pre-existing | Present before this diff; do not blame the author |

Group by **bug class** so the author can fix the class once. Do not file 5 copies of the same mistake.

**Discard:** formatting, taste, “add a type” with no runtime failure, comments on unchanged code, claims without a reproducing input.

Change-type emphasis (still one pass):

| Type | Lean on | May skim |
|---|---|---|
| feature | goal, security if high-risk, user journey | architecture nits |
| bugfix | root cause actually gone | architecture |
| refactor | behavior equivalence, coupling | security unless HIGH_RISK |
| config/infra | security, misroute, secrets | product journey |
| test | assertions test the behavior | security, architecture |
| docs | false statements | the rest |

## Loop control

Hard cap is **3 review rounds on the same change** (orchestrator `ROUND`).

| Rule | Do |
|---|---|
| Round cap | After 3, stop. Output `HUMAN_ESCALATION_REQUIRED`. Author rethinks the approach. |
| Diff-only re-review | Round 2+ inspects new hunks only |
| Class of bug | Report the pattern + every instance in this diff, once |
| No repeats | Do not restate findings already in a previous report |

No-progress: a re-review with zero new Important/Security (after filtering) is **审查收敛**. Stop.

## Output

```markdown
# AI Code Review Report

## 审查元信息
- 范围: {files or module / PR}
- 类型: {feature|bugfix|refactor|config/infra|test|docs}
- ROUND: {1-3}
- 独立上下文: 是（subagent） / 否（违规，说明）
- HIGH_RISK: yes / no
- 测试证据: 已提供（命令+结果摘要） / EVIDENCE_MISSING
- 置信度: 高 / 中 / 低

## 三问
- 是否达成目标: …
- 是否适配现有系统: …
- 是否影响其他功能: …

## 发现汇总
| 级别 | 数量 |
|---|---|
| Important | N |
| Security | N |
| Nit | N |
| Pre-existing | N |

## Important
### [I1] path:line — 标题
- 证据: 输入/路径/缺失签名
- 反驳后仍保留: …
- 建议: …

## Security
### [S1] path:line — 攻击路径
- 证据: …
- 反驳后仍保留: …
- 建议: …

## Nit
（可按 bug class 合并）

## Pre-existing
- path:line — 简述（不阻断本 PR）

## 范围
- 未覆盖的验收标准: …
- 无关改动: …

## 人工门控（本 Skill 不执行）
- 风险档: 低 / 中 / 高（见 repo-risk.md）
- 建议人工审批人数: 0 / 1 / 2
- 合并前: 未关闭 Important/Security 不得合并；作者须逐条回复（修复或说明）
- 合并: 不要直推主分支；在最新主分支上再跑测试
- 上线: 功能开关 / 灰度；出问题先关开关再回滚

## 审查终止状态
- [ ] 审查收敛
- [ ] 达到 3 轮且仍有 Important/Security → HUMAN_ESCALATION_REQUIRED
```

Security 无发现时该节只写 `无`。未关闭的 Security 不得合并。

## Red flags — stop and correct

| Excuse | Reality |
|---|---|
| “就在这个 Chat 里审更快” | Orchestrator must spawn the subagent |
| “我写过这段，边界已经想过” | Reviewer starts from zero |
| “顺手把问题改了” | Read-only |
| “大 PR 抽着看” | Split by module |
| “历史代码也这样，安全降级” | Security never downgrades |
| “再开第 4 轮吧” | Escalate to a human |
| “没有行号也算发现” | Drop it |

## Common mistakes

- Mixing **sections** (goal/security/defects) with **rounds** (max 3 on the same change)
- Reviewing in the writing session instead of a subagent
- Spawning another reviewer when `INDEPENDENT_REVIEWER=1` is already set
- Treating CodeRabbit as this workflow, or double-reviewing after a completed CodeRabbit run unless the user asked for this independent pass
- Filling 人工门控 as if merge/rollback already happened
