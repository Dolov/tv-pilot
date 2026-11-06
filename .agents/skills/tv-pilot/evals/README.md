# 评测集

两个文件，用途不同：

| 文件 | 用途 |
|---|---|
| `evals.json` | **行为评测**：19 个真实任务 + 期望行为（如「不跑全量扫描」「用 screen_toggled 而不是 play_time_today_watchtime」「失败快速分类」）。用于回归和给贡献者当规格。 |
| `trigger-queries.json` | **触发评测**：24 条用户查询（17 应触发 / 7 不应触发），用于校验 `SKILL.md` 的 description 是否覆盖到位、边界是否合理。 |

## 怎么用

- **人工核对**：把 `trigger-queries.json` 的 query 丢给加载了本 skill 的代理，看它是否调用 tv-pilot；负例要确认没有误触发。
- **自动触发评测**：可以用任意 LLM CLI / API 起一个只带 `SKILL.md` description 的对照，把 query 和 `should_trigger` 做比对。仓库不再内置绑定某个 CLI 的脚本——避免贡献者为了跑评测被迫装特定工具。
- **行为评测**：`evals.json` 目前是行为规格（自然语言 expectation），适合人工或评审代理逐条核对；不依赖真电视。涉及真机操作的条目需要在有电视的环境里跑对应脚本验证。

## 加一条评测

1. 在 `evals.json` 的 `evals` 数组追加：
   ```json
   {"id": 20, "prompt": "用户会怎么说", "expected_output": "期望结果", "files": [], "expectations": ["可核对的点"]}
   ```
2. 触发集里补正例或负例，负例优先选「关键词相近但实际不需要本 skill」的（如投屏、电视导购）。
3. 跑 `make check` 确认 JSON 结构没坏。

> 评测的 expectation 是给人和评审代理读的规格，不要求写成可执行断言；真正需要自动化的检查放在仓库根 `tests/`（离线、假 adb）。
