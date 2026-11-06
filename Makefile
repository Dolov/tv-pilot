# tv-pilot 仓库入口。默认 target = help。
SHELL := /bin/bash

SKILL_DIR := .agents/skills/tv-pilot

.PHONY: help check test apk index

help:
	@printf '%s\n' \
	  'tv-pilot 常用命令：' \
	  '' \
	  '  make check   仓库自检：bash -n / shellcheck(error) / JSON / 档案 lint+index' \
	  '               / 文档引用 / 版本一致性 / SKILL.md / 禁提交文件' \
	  '  make test    离线测试：tests/run.sh（fake adb，不需要真电视）' \
	  '  make apk     构建电视端 APK：bash tvhud/build.sh' \
	  '  make index   重建设备支持矩阵：device-profile.sh index' \
	  '  make help    显示本帮助' \
	  '' \
	  '提交前请跑：make check && make test'

# 不依赖真电视、不下载工具链。
check:
	@bash $(SKILL_DIR)/scripts/check.sh

test:
	@bash tests/run.sh

# 需要 .tools/ 工具链（bash scripts/build-tools.sh ensure）。
apk:
	@bash tvhud/build.sh

index:
	@bash $(SKILL_DIR)/scripts/device-profile.sh index
