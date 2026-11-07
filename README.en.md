# tv-pilot

[中文](README.md) | English

Control a TV over **LAN ADB** (tested on Xiaomi / Redmi MIUI TV; other Android TVs can be onboarded through device profiles): discover devices without a cable, run read-only probes, configure kids mode and viewing-time limits, remove ad components, sideload third-party APKs, read the remote's battery level, measure TV usage time and watch history — and run a small self-built app "**tv-pilot**" on the TV (screensaver corner badge + daily usage ledger + usage history).

The project has two layers:

- **`.agents/skills/tv-pilot/`**: the skill for AI coding agents (scripts + reference docs + evals + brand extension points). Entry point: [SKILL.md](.agents/skills/tv-pilot/SKILL.md); human-facing install/structure notes: [skill README](.agents/skills/tv-pilot/README.md).
- **`app/`**: the app that actually runs on the TV (plain Java + system APIs, no Gradle/Android Studio; `build.sh` produces the APK directly).

## Quick start

```bash
git clone https://github.com/Dolov/tv-pilot.git ~/tv-pilot
ln -s ~/tv-pilot/.agents/skills/tv-pilot ~/.agents/skills/tv-pilot   # let the agent auto-load it
cd ~/tv-pilot/.agents/skills/tv-pilot
bash scripts/install-adb.sh        # first run: download platform-tools
bash scripts/setup.sh              # generate .env + probe the TV + write references/local.md
bash scripts/probe.sh              # read-only: model / apps / ad candidates
bash scripts/usage.sh              # how long and what was watched today
```

On the TV, enable ADB debugging first (`Settings → Account & security → Security → ADB debugging`; some firmware only starts listening on 5555 after a reboot). Approve the authorization prompt on the TV the first time you connect.

## What tv-pilot does

- A three-line badge in the top-right corner of the screensaver: uptime / remote battery / today's usage bar (a 24-hour timeline colored by category)
- Opening the app shows a home menu: Today's usage / Usage history (browse day by day, kept for 365 days) / Reports / My Pet / AI voice assistant and Settings (coming soon)
- The "Today" ledger: total, category legend, 00:00→24:00 timeline, and entries (time / what was watched / duration / category), refreshed every minute
- Ships with ADB keep-alive (`adb_enabled`) and accessibility-based boot autostart

Build and deploy:

```bash
bash .agents/skills/tv-pilot/scripts/build-tools.sh ensure   # download build-tools / android.jar (~50MB)
bash .agents/skills/tv-pilot/scripts/app.sh build         # outputs .build/out/tv-pilot.apk
bash .agents/skills/tv-pilot/scripts/app.sh install       # permissions/accessibility ordering built in
```

Details and pitfalls live in `references/operations/` (usage stats, screensaver, kids mode, ad removal, boot autostart, dev workflow).

## About viewing statistics

tv-pilot records viewing time and content, but **it is not meant to monitor a child**, nor should it become a tool for checking up on them. Its purpose is to give the family a shared sense of how the TV is used: a child can see how long they have watched today, and adults can agree on a rhythm together instead of nagging over and over.

We asked the child for consent before building and using this; the data stays on your own TV, and is never uploaded or shared. We would rather it help a child spend time on what they love — watching TV is supposed to be fun. If these numbers ever make the home tense, something has been used the wrong way.

## Repository layout

```
tv-pilot/
├── AGENTS.md                     # repo-level agent guide (read before changing code)
├── CONTRIBUTING.md               # contribution flow: profiles / scripts / providers / app
├── README.md                     # this file (Chinese: README.en.md for English)
├── CHANGELOG.md / SECURITY.md
├── Makefile                      # check / test / apk / index
├── version.properties            # single source of truth for the version
├── .github/                      # CI + PR/issue templates
├── docs/                         # architecture, JSON contract, provider contract
├── tests/                        # offline tests (fake adb; no real TV needed)
├── app/                          # TV-side app "tv-pilot"
│   ├── AndroidManifest.xml
│   ├── build.sh                  # javac + d8 + aapt2 + apksigner, one command to an APK
│   └── src/com/dsh/tvhud/        # feature packages
│       ├── home/                 # home menu
│       ├── usage/                # usage stats / ledger / archive
│       ├── report/               # weekly/monthly/yearly reports
│       ├── hud/                  # service / badge / broadcasts / accessibility
│       └── pet/                  # electronic pet
└── .agents/skills/tv-pilot/      # the skill itself (see skill README)
    ├── SKILL.md                  # agent entry point: task index
    ├── scripts/                  # connect/probe/stats/deploy/profiles/check + providers/
    ├── references/               # operations/ + devices/ + porting.md + local.md
    └── evals/                    # trigger set + behavior evals
```

## Supported TVs / contributing

- Tested devices: see the [device support matrix](.agents/skills/tv-pilot/references/devices/README.md) (currently: MiTV-NSOP0 / MIUI TV Android 9).
- Unknown models: `identify-tv.sh` reports `similar` (same brand) or `unknown` (no profile). Read-only capabilities still work; disabling packages / changing settings / installing apps / building the screensaver badge are blocked by `require-profile.sh` until the model is verified.
- Onboarding a new TV: collect data per [porting.md](.agents/skills/tv-pilot/references/porting.md) → `device-profile.sh init` → fill in the profile → `lint` → open a PR. Put brand differences in `scripts/providers/` rather than editing generic scripts.
- Before contributing code, read [AGENTS.md](AGENTS.md) and [CONTRIBUTING.md](CONTRIBUTING.md), and run `make check && make test`.

## Security and privacy

The repository does **not** contain:

- `.tools/`: local toolchain (adb, build-tools, android.jar, jadx) and the signing key — all rebuildable via scripts
- `.env` / `.agents/skills/tv-pilot/references/local.md`: LAN IP, remote MAC, local TV model, etc.
- Proprietary fonts (DIN Pro / MIUI EX): not redistributed; the app falls back to system fonts when they are missing
- TV screenshots, or real account details in app inventories

Usage notes:

- Scripts are **read-only by default**; write operations explain themselves before running, and app installs go through `app-deploy.sh` (with the device-profile gate)
- ADB `5555` is a plaintext, password-less port; turn ADB debugging off on the TV when not in use
- Do not use uninstall/reinstall to reset data: it also loses appops, the accessibility entry and app preferences. Reset usage stats with the broadcast `com.dsh.tvhud.RESET_USAGE`
- Known limitations and the threat model are in [SECURITY.md](SECURITY.md)

## License

Code is released under [MIT](LICENSE). Font files under `app/assets/` are not distributed with the repository; copyright remains with their respective vendors.
