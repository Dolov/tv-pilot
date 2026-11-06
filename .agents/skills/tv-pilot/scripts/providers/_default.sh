#!/usr/bin/env bash
# 默认 provider：所有机型的最小契约。
#
# 由 lib.sh 的 tvp_load_provider 先 source 本文件，再 source 具体 provider；
# 因此具体 provider 没实现的函数自动回落到这里。
#
# 约定：每个函数只往 stdout 打一个值（一行），不要打印日志/提示；不知道就输出空。
# 未知机型的「默认值」由调用方脚本决定（例如 probe 的广告正则会回退 ad|push|analy|stat），
# 这里保持空，避免把小米的包名/键名误用到别的品牌上。

tvp_screensaver_pkg() { :; }
tvp_play_log_path() { :; }
tvp_battery_key_prefix() { :; }
tvp_apps_snapshot() { :; }
tvp_ad_patterns() { :; }
tvp_home_pkg() { :; }
