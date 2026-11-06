# 离线测试（不需要真电视）

这些测试用一个假的 `adb`（`tests/fake-adb`）回放固定输出，验证脚本的连接、识别、取数和
`--json` 契约。CI 里 `make test` 跑的就是这里；它不能替代真机验证，但能在提交前抓住
「输出格式变了 / 解析挂了 / 连接阶梯退化了」这类回归。

## 结构

```text
tests/
├── fake-adb                 # 可执行：按 adb 参数匹配并输出 fixtures 里的内容
├── fixtures/
│   ├── devices.txt          # adb devices
│   ├── identify-shell.txt   # getprop / resolve-activity 的一次性输出
│   ├── remote-battery-shell.txt
│   ├── tv-state-shell.txt
│   ├── bluetooth-manager.txt
│   ├── packages-all.txt
│   └── packages-third.txt
└── run.sh                   # 用 fake-adb 跑各脚本并断言关键字段
```

## 隔离方式

`run.sh` 会给每个用例设置临时目录，避免污染本机状态：

| 变量 | 作用 |
|---|---|
| `TVPILOT_ADB_BIN` | 指向 `tests/fake-adb`，脚本以为在跟真 adb 说话 |
| `TVPILOT_STATE_FILE` | 临时 state 文件，避免读到/写坏 `.tools/state.env` |
| `TVPILOT_TOOLS_DIR` | 临时工具目录（不存在的 adb 路径会被 `TVPILOT_ADB_BIN` 覆盖） |
| `TVPILOT_DEVICES_DIR` | 指向空的临时目录，避免读到开发者本地档案 |
| `TVPILOT_PROFILE_AUTODETECT` | 需要固定机型时设为 `0`，配合 `TVPILOT_PROFILE=<slug>` |

## 加一个用例

1. 把要回放的输出放进 `tests/fixtures/`；
2. 在 `tests/fake-adb` 里按参数加一个分支（注意匹配顺序，先具体后通用）；
3. 在 `tests/run.sh` 里加一个 `test_xxx` 函数，用 `pass_case` / `fail_case` 断言；
4. `bash tests/run.sh` 本地跑通，`make check` 确认没有破坏文档引用。

断言优先检查 `--json` 的关键字段（如 `state` / `percent` / `match` / `profile_slug`），
不要断言整段人类可读文本——那会让每次改措辞都误报。

## 加一台新机型的用例

想验证新 provider 的取数链路时，需要同时加 fixture 和 fake-adb 分支：

- `tests/fixtures/<brand>-battery.txt`：该机型 `settings list global | grep <prefix>` 的输出；
- fake-adb 里根据 `TVPILOT_PROFILE`/测试场景选不同 fixture（现有实现是按命令匹配，必要时加环境变量开关）；
- `run.sh` 用 `TVPILOT_PROFILE=<slug> TVPILOT_PROFILE_AUTODETECT=0` 跑 `remote-battery.sh --json`。

没有这一步，只能验证「provider 函数返回了正确前缀」，验证不了「脚本真的用它取到了电量」。
