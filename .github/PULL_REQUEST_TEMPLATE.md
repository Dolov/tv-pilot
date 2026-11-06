## 这个 PR 做了什么

<!-- 一句话说明目的；关联 issue 用 "Closes #123" -->

## 改动类型

- [ ] 设备档案（新增 / 修正）
- [ ] 通用脚本 / provider
- [ ] TV HUD App
- [ ] 文档 / 工程基建

## 检查清单

- [ ] 已跑 `make check && make test`
      （本地结果贴在下方；失败项已说明原因）
- [ ] 若本地有 SKIP（如未装 shellcheck、工作树脏导致矩阵断言跳过），已注明并说明 CI 会覆盖
- [ ] 设备相关结论附了「命令 + 输出摘录 + 日期」证据；没测的保持 `unknown`
- [ ] 已脱敏：无真实 IP / MAC / 序列号 / SSID / 账号 / 观看记录 / 家庭照片
- [ ] 通用逻辑与机型事实已分离（机型差异在设备档案或 `scripts/providers/`）
- [ ] 没有提交 `.env`、`.tools/`、keystore、`.build/`、`*.apk`、
      `tvhud/assets/*.otf|ttf`、`references/local.md`
- [ ] 新增/移动文件后，`SKILL.md` 与文档中的引用路径有效
- [ ] 改 App 时 `version.properties` 与 `AndroidManifest.xml` 版本号一致（`build.sh` 读取 properties）
- [ ] 改设备档案时已跑 `device-profile.sh index`，矩阵变更一并提交
- [ ] 真机行为改动已说明验证方式（进程 / logcat / 截图），并确认没有默认自动写操作

## 验证记录

```text
$ make check
...
$ make test
...
```

## 备注 / 需要 reviewer 关注的点
