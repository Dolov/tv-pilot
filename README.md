# tv-pilot

用**局域网 ADB** 操作小米 / Redmi 电视（MIUI TV / PatchWall）：免数据线发现设备、只读探测、配置儿童模式与观看时长、清理广告组件、安装第三方 APK、读遥控器电量、统计电视使用时长与观看内容，以及在电视上跑一个自研小 App「**TV HUD**」（屏保右上角角标 + 今日使用台账 + 使用记录）。

整套东西由两部分组成：

- **`.agents/skills/tv-pilot/`**：给 AI 编码代理用的 skill（脚本 + 参考文档 + 评测），把「电视运维」的试错经验固化下来，下次不用重新踩坑。
- **`tvhud/`**：电视上真正运行的小 App（纯 Java + 系统 API，无 Gradle/Android Studio，`build.sh` 直接出 APK）。

## 仓库结构

```
tv-pilot/
├── .agents/skills/tv-pilot/      # skill：代理的操作手册与脚本
│   ├── SKILL.md                  # 入口（三条原则、常见请求 → 一条命令）
│   ├── .env.example              # 本机敏感配置模板（复制成 .env，不进仓库）
│   ├── scripts/                  # ensure-tv / probe / usage / remote-battery / app-deploy ...
│   ├── references/               # 按需读的细节：ADB、儿童模式、去广告、屏保、构建链路
│   └── evals/                    # 触发与行为评测集
├── tvhud/                        # 电视端 App 源码（本项目的"产品"部分）
│   ├── AndroidManifest.xml
│   ├── build.sh                  # javac + d8 + aapt2 + apksigner，一条命令出 APK
│   ├── assets/                   # 字体等运行时资源（专有字体不随仓库分发）
│   └── src/com/dsh/tvhud/        # HomeActivity / MainActivity / HudService / UsageDay ...
├── LICENSE                       # MIT
└── README.md
```

## 快速开始

### 1. 电视开启 ADB 调试

新固件路径：`设置 → 账号与安全 → 安全 → ADB 调试`（部分固件在 `设置 → 关于 → 开发者选项`）。**打开后通常要重启电视**，`5555` 才开始监听；部分小米固件重启后会复位，需要重开。

### 2. 放好 skill 并配置 `.env`

```bash
git clone https://github.com/Dolov/tv-pilot.git ~/tv-pilot
ln -s ~/tv-pilot/.agents/skills/tv-pilot ~/.agents/skills/tv-pilot   # 让代理能自动加载
cp ~/tv-pilot/.agents/skills/tv-pilot/.env.example ~/tv-pilot/.agents/skills/tv-pilot/.env
# 编辑 .env，把 TV_IP 改成电视的局域网地址
```

> skill 不在 `~/.agents/skills/` 下也能用：跑脚本时设 `TVPILOT_SKILL=/path/to/tv-pilot/.agents/skills/tv-pilot`。

### 3. 连上电视（只读）

```bash
cd ~/tv-pilot/.agents/skills/tv-pilot
bash scripts/ensure-tv.sh -v     # 已有连接 → 缓存 IP → 扫描，输出 <ip>:5555
bash scripts/tv-state.sh         # 开机 / 熄屏待机 / 关机
bash scripts/probe.sh            # 型号、固件、应用清单、广告候选、儿童模式
bash scripts/remote-battery.sh   # 遥控器电量
bash scripts/usage.sh            # 今天看了多久、看了什么
```

首次使用需要下载 adb：`bash scripts/install-adb.sh`。连不上时按输出提示走（`unauthorized` 去电视上按「允许」；`offline` 先唤醒；`Connection refused` 多半是 ADB 开关没生效）。

### 4. 构建并安装 TV HUD

```bash
cd ~/tv-pilot/.agents/skills/tv-pilot
bash scripts/build-tools.sh ensure      # 下载 build-tools / android.jar（需 JDK 17/21，~50MB）
cd ~/tv-pilot/tvhud
bash build.sh                            # 产出 .build/out/tvhud.apk
cd ../.agents/skills/tv-pilot
bash scripts/app-deploy.sh --apk ~/tv-pilot/.build/out/tvhud.apk --overlay --usage --acc
```

`app-deploy.sh` 会按正确顺序做：安装 → 补 `SYSTEM_ALERT_WINDOW`/`GET_USAGE_STATS` appops → 授权 `WRITE_SECURE_SETTINGS` → 登记无障碍服务（开机自启）。顺序错了会丢权限。

### 5. TV HUD 能做什么

- 屏保右上角角标三行：开机状态 / 遥控器电量 / 今日使用长条（24 小时时间轴，按类别着色）
- 打开 App 是首页菜单：今日使用 / 使用记录（按天翻历史，保留 365 天）
- 「今日使用」是台账详情页：合计、分类图例、0:00→24:00 时间轴、明细（时间/看了什么/时长/类别），每分钟刷新
- 附带 ADB 守护（保持 `adb_enabled`）与无障碍开机自启

详细口径与坑见 `references/usage-stats.md`、`references/screensaver.md`。

## 配置（`.env`）

所有因机器而异的敏感信息都在**本地** `.env`（已被 `.gitignore` 排除），仓库里只有 `.env.example`：

| 变量 | 说明 |
|---|---|
| `TV_IP` / `TV_PORT` | 电视局域网地址；冷启动时 `ensure-tv.sh` 的第一跳 |
| `REMOTE_MAC` | 遥控器蓝牙 MAC（可选，便于核对电量键；脚本会自动发现） |
| `TVPILOT_KS_PASS` | 签名 keystore 密码（可选，默认 `android`） |

## 安全与隐私

仓库**不包含**：

- `.tools/`：本机平台工具链（adb、build-tools、android.jar、jadx）与签名私钥，均可按脚本重建
- `.env`：局域网 IP、遥控器 MAC、儿童模式密码等本机信息
- 专有字体（DIN Pro / MIUI EX）：不公开分发，缺失时代码自动回退系统字体
- 电视截图、应用清单中的真实账号信息

使用建议：

- 脚本**默认只读**；写操作（install / uninstall / settings put / disable）都会先说明再执行
- ADB 的 `5555` 是明文端口、且无密码；不用时在电视上关掉 ADB 调试
- 不要用「卸载重装」来重置数据：会连带丢掉 appops、无障碍条目与 App 偏好；重置使用统计用广播 `com.dsh.tvhud.RESET_USAGE`

## 许可

代码以 [MIT](LICENSE) 发布。`tvhud/assets/` 下的字体文件不随仓库分发，版权归各自厂商。
