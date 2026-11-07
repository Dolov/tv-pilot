# tv-pilot 仓库入口。默认 target = help。
SHELL := /bin/bash

SKILL_DIR := .agents/skills/tv-pilot

.PHONY: help check test apk index e2e regression

help:
	@printf '%s\n' \
	  'tv-pilot 常用命令：' \
	  '' \
	  '  make check   仓库自检：bash -n / shellcheck(error) / JSON / 档案 lint+index' \
	  '               / 文档引用 / 版本一致性 / SKILL.md / 禁提交文件' \
	  '  make test    离线测试：tests/run.sh（fake adb + 核心 Java 单元测试，不需要真电视）' \
	  '  make e2e     真机冒烟：tests/e2e/report-smoke.sh（需要电视，装 APK 后跑报告页流程）' \
	  '  make regression 全量真机回归：tests/e2e/full-regression.sh（首页/台账/记录/报告全走一遍）' \
	  '  make apk     构建电视端 APK：bash app/build.sh' \
	  '  make index   重建设备支持矩阵：device-profile.sh index' \
	  '  make help    显示本帮助' \
	  '' \
	  '提交前请跑：make check && make test'

# 不依赖真电视、不下载工具链。
check:
	@bash $(SKILL_DIR)/scripts/check.sh

test:
	@bash tests/run.sh

# 真机端到端冒烟：需要已连接的电视（先 bash .agents/skills/tv-pilot/scripts/ensure-tv.sh）。
e2e:
	@bash tests/e2e/report-smoke.sh

# 全量真机回归：所有页面 + 数据侧断言，改动涉及多个页面时跑这个。
regression:
	@bash tests/e2e/full-regression.sh

# 需要 .tools/ 工具链（bash scripts/build-tools.sh ensure）。
apk:
	@bash app/build.sh

index:
	@bash $(SKILL_DIR)/scripts/device-profile.sh index
