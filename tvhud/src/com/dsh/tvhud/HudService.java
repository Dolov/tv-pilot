package com.dsh.tvhud;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;

/**
 * 屏保时在右上角显示：开机 N 天 HH:MM，下面一行是蓝牙遥控器电量，
 * 第三行是今日使用长条（0:00→24:00，按内容类型着色，长度=时长）。
 */
public class HudService extends Service {
    private static final String TAG = "TVHUD";
    private static final String CJK_FONT = "/system/fonts/MILanPro_Normal.ttf";
    private static final String SCREENSAVER_PKG = "com.mitv.screensaver";
    private static final String BATT_PREFIX = "bluetooth_hid_battery_";

    private WindowManager wm;
    private UsageStatsManager usm;
    private PowerManager power;

    private HudView hud;
    private Typeface cjk;
    private Typeface num;

    private boolean attached = false;
    private boolean adbKeep = false;
    private int tickCount = 0;
    private String lastTop = null;
    private String lastClass = null;   // 当前前台页面的类名（用于区分「详情页在播片」和「桌面」）
    private long lastQueryEnd = 0;
    private String battKey = null;
    private int battPct = -1;

    private UsageDay usage;
    /** 同进程的详情页（MainActivity）直接读这个快照，避免只读 60s 前的落盘数据。 */
    private static volatile UsageDay sUsage;

    // 播放内容（片名/集数）：从米家上报日志 /sdcard/miio/miothost.txt 抓
    private static final String MIIO_LOG = "/sdcard/miio/miothost.txt";
    private static final String SEED_LOG =
            "/sdcard/Android/data/com.dsh.tvhud/files/play-import.txt";
    private static final long PLAY_SCAN_MS = 5000L;

    private final ArrayList<PlayEntry> plays = new ArrayList<>();
    private final HashSet<String> playKeys = new HashSet<>();
    private long playDayStart = 0;
    private long miioOffset = -1;
    private String miioHead = "";
    private long miioLastScan = 0;
    private static volatile List<PlayEntry> sPlays;
    private long lastTickWall = 0;      // 上一次 tick 的墙钟毫秒
    private long lastTickElapsed = 0;   // 上一次 tick 的 elapsedRealtime
    private boolean lastUsing = false;
    private boolean bootstrapped = false;
    private boolean previewMode = false;

    private final UsageEvents.Event event = new UsageEvents.Event();
    private final Handler handler = new Handler();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            boolean screensaverOnTop = screensaverOnTop();
            sampleUsage();
            if (!bootstrapped) {
                bootstrapped = true;
                backfillToday();
            }
            if (screensaverOnTop && !attached) {
                attach();
            } else if (!screensaverOnTop && attached) {
                detach();
            }
            if (attached) {
                render();
            }
            if (tickCount % 5 == 0) {
                samplePlayLog();
            }
            if (++tickCount % 10 == 0) {
                keepAdbOn();
                ensureAccessibilityEntry();
                updateBattery();
                if (attached) {
                    render();
                }
            }
            // 每 60 秒落盘一次；切换空闲/销毁时也会立即落盘，重启最多丢 60 秒
            if (tickCount % 60 == 0) {
                saveUsage();
                archiveToday();
            }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        power = (PowerManager) getSystemService(POWER_SERVICE);
        adbKeep = getSharedPreferences("hud", 0).getBoolean("adb_keep", false);
        previewMode = getSharedPreferences("hud", 0).getBoolean("usage_preview", false);
        usage = UsageDay.load(this, UsageDay.dayStartOf(System.currentTimeMillis()));
        sUsage = usage;
        loadPlays();
        readMiio(true);
        importSeed();
        HistoryStore.cleanup(this);
        archiveToday();
        handler.post(ticker);
    }

    // ---------- 今日使用：采样 + 持久化 ----------

    /** 自己（TV HUD 详情页）打开的这段时间不算「看电视」，否则长条会被自己刷灰。 */
    private boolean isOwn(String pkg) {
        return pkg != null && pkg.equals(getPackageName());
    }

    private boolean isUsing() {
        try {
            return power != null && power.isInteractive()
                    && lastTop != null && !SCREENSAVER_PKG.equals(lastTop)
                    && !isOwn(lastTop);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 给同进程的详情页用：内存里的今日分段（服务没起来时为 null，调用方回落读 prefs）。 */
    static UsageDay peekUsage() {
        return sUsage;
    }

    private void sampleUsage() {
        try {
            long wall = System.currentTimeMillis();
            long dayStart = UsageDay.dayStartOf(wall);
            if (usage == null) {
                usage = UsageDay.load(this, dayStart);
            } else if (usage.dayStart != dayStart) {
                HistoryStore.write(this, usage.dayStart, usage.segments(), plays);
                saveUsage();
                usage = new UsageDay(dayStart);
                sUsage = usage;
            }
            long nowElapsed = SystemClock.elapsedRealtime();
            // 只有 tick 连续（interval ≤2.5s）才累计；待机/进程被杀留下的空档不算
            boolean continuous = lastTickElapsed != 0 && nowElapsed - lastTickElapsed <= 2500L;
            boolean using = isUsing();
            if (lastTickWall == 0L) {
                // 服务刚起来的第一轮：没有「上一个时间点」，直接累计会把 00:00→现在
                // 当成一段（长条整天变一个色块）。这里不记，交给 backfillToday() 用
                // UsageEvents 精确重建。
            } else if (!continuous) {
                // tick 被拖慢（切 App、系统卡顿、待机唤醒）：用 UsageEvents 把空档精确补回来，
                // 否则不同颜色交界处会留下 1~2px 的空白。
                replayEvents(lastTickWall, wall);
            } else if (using) {
                usage.append(lastTickWall, wall, Categories.of(lastTop, lastClass));
            }
            if (!using && lastUsing) {
                saveUsage();
            }
            lastUsing = using;
            lastTickWall = wall;
            lastTickElapsed = nowElapsed;
        } catch (Throwable t) {
            Log.w(TAG, "sampleUsage failed: " + t);
        }
    }

    private void saveUsage() {
        try {
            if (usage != null) {
                usage.save(this);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 服务刚起来时，用 UsageStats 事件把「上次记录结束 → 现在」补回来。
     * （进程被杀 / 电视重启后，中间那段没人采样，不补就永远缺一块。）
     * 事件只保留到最近一次开机以来的 24h；更早的补不了。
     */
    private void backfillToday() {
        if (usage == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long from = usage.lastEnd();
        if (now - from <= 15000L) {
            return;
        }
        replayEvents(from, now);
        usage.save(this);
        StringBuilder cats = new StringBuilder();
        for (UsageDay.Seg sg : usage.segments()) {
            cats.append(sg.cat).append(',');
        }
        Log.i(TAG, "backfilled usage from " + from + " -> " + now
                + " segs=" + usage.segments().size()
                + " top=" + lastTop + "/" + lastClass
                + " cats=" + cats);
    }

    /** 用 UsageEvents 重建 [from,to] 的「谁在前台 + 亮没亮」，把分段补进去。 */
    private void replayEvents(long from, long to) {
        try {
            if (usage == null || usm == null) {
                return;
            }
            long minFrom = to - UsageDay.DAY_MS + 60000L;
            if (from < minFrom) {
                from = Math.max(usage.dayStart, minFrom);
            }
            if (from >= to) {
                return;
            }
            UsageEvents events = usm.queryEvents(from, to);
            if (events == null) {
                return;
            }
            UsageEvents.Event e = new UsageEvents.Event();
            long t = from;
            boolean interactive = false;
            String top = null;
            String topClass = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                int type = e.getEventType();
                if (type != UsageEvents.Event.SCREEN_INTERACTIVE
                        && type != UsageEvents.Event.SCREEN_NON_INTERACTIVE
                        && type != UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    continue;
                }
                long et = e.getTimeStamp();
                if (et < from) {
                    continue;
                }
                if (et > to) {
                    et = to;
                }
                if (interactive && top != null && !SCREENSAVER_PKG.equals(top)
                        && !isOwn(top) && et > t) {
                    usage.append(t, et, Categories.of(top, topClass));
                }
                t = et;
                switch (type) {
                    case UsageEvents.Event.SCREEN_INTERACTIVE:
                        interactive = true;
                        break;
                    case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                        interactive = false;
                        break;
                    case UsageEvents.Event.MOVE_TO_FOREGROUND:
                        top = e.getPackageName();
                        topClass = classNameOf(e);
                        break;
                    default:
                        break;
                }
            }
            if (interactive && top != null && !SCREENSAVER_PKG.equals(top)
                    && !isOwn(top) && to > t) {
                usage.append(t, to, Categories.of(top, topClass));
            }
        } catch (Throwable t) {
            Log.w(TAG, "replayEvents failed: " + t);
        }
    }

    /** 把当天的使用分段 + 播放记录写到按天归档文件。 */
    private void archiveToday() {
        try {
            if (usage != null && usage.dayStart > 0L) {
                HistoryStore.write(this, usage.dayStart, usage.segments(), plays);
            }
        } catch (Throwable t) {
            Log.w(TAG, "archiveToday failed: " + t);
        }
    }

    // ---------- 播放内容（片名/集数） ----------

    /** 服务销毁/重启后从 prefs 恢复当天播放记录。 */
    private void loadPlays() {
        playDayStart = UsageDay.dayStartOf(System.currentTimeMillis());
        plays.clear();
        playKeys.clear();
        try {
            android.content.SharedPreferences sp = getSharedPreferences("hud", 0);
            if (sp.getLong("play_day_start", 0L) != playDayStart) {
                publishPlays();
                return;
            }
            JSONArray arr = new JSONArray(sp.getString("play_json", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONArray a = arr.getJSONArray(i);
                PlayEntry e = new PlayEntry();
                e.ts = a.getLong(0);
                e.status = a.getInt(1);
                e.name = a.getString(2);
                e.ci = a.getInt(3);
                e.totalCi = a.getInt(4);
                e.durMs = a.getLong(5);
                if (playKeys.add(e.key())) {
                    plays.add(e);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "loadPlays failed: " + t);
        }
        publishPlays();
    }

    private void savePlays() {
        try {
            JSONArray arr = new JSONArray();
            for (PlayEntry e : plays) {
                JSONArray a = new JSONArray();
                a.put(e.ts).put(e.status).put(e.name).put(e.ci).put(e.totalCi).put(e.durMs);
                arr.put(a);
            }
            getSharedPreferences("hud", 0).edit()
                    .putLong("play_day_start", playDayStart)
                    .putString("play_json", arr.toString())
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private void publishPlays() {
        ArrayList<PlayEntry> copy = new ArrayList<>(plays);
        Collections.sort(copy, new Comparator<PlayEntry>() {
            @Override
            public int compare(PlayEntry a, PlayEntry b) {
                return Long.compare(a.ts, b.ts);
            }
        });
        sPlays = copy;
    }

    /** 加一条；同一集只留第一次出现。返回是否有变化。 */
    private boolean addPlay(PlayEntry e) {
        if (e == null || !playKeys.add(e.key())) {
            return false;
        }
        plays.add(e);
        return true;
    }

    private void samplePlayLog() {
        try {
            long dayStart = UsageDay.dayStartOf(System.currentTimeMillis());
            if (dayStart != playDayStart) {
                plays.clear();
                playKeys.clear();
                playDayStart = dayStart;
                miioOffset = -1;
                miioHead = "";
                savePlays();
                publishPlays();
            }
            if (readMiio(false)) {
                savePlays();
                publishPlays();
            }
        } catch (Throwable t) {
            Log.w(TAG, "samplePlayLog failed: " + t);
        }
    }

    /**
     * 增量读米家日志；forceFull 时从头重扫。
     * 只处理完整行（尾部半行留到下次），文件被替换（开头变了）时自动重扫。
     */
    private boolean readMiio(boolean forceFull) {
        long now = System.currentTimeMillis();
        if (!forceFull && now - miioLastScan < PLAY_SCAN_MS) {
            return false;
        }
        miioLastScan = now;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(MIIO_LOG, "r");
            long len = raf.length();
            if (len <= 0) {
                return false;
            }
            byte[] headBytes = new byte[(int) Math.min(64, len)];
            raf.seek(0);
            raf.readFully(headBytes);
            String head = new String(headBytes, "UTF-8");
            if (forceFull || miioOffset < 0 || len < miioOffset || !head.equals(miioHead)) {
                miioOffset = 0;
                miioHead = head;
            }
            if (len == miioOffset) {
                return false;
            }
            int cap = (int) Math.min(1 << 20, len - miioOffset);
            byte[] buf = new byte[cap];
            raf.seek(miioOffset);
            int n = raf.read(buf);
            if (n <= 0) {
                return false;
            }
            int lastNl = -1;
            for (int i = n - 1; i >= 0; i--) {
                if (buf[i] == '\n') {
                    lastNl = i;
                    break;
                }
            }
            if (lastNl < 0) {
                return false;
            }
            String block = new String(buf, 0, lastNl + 1, "UTF-8");
            long readFrom = miioOffset;
            miioOffset = readFrom + lastNl + 1;
            long dayStart = UsageDay.dayStartOf(now);
            long dayEnd = dayStart + UsageDay.DAY_MS;
            boolean changed = false;
            for (String line : block.split("\n")) {
                if (addPlay(PlayEntry.parse(line, dayStart, dayEnd))) {
                    changed = true;
                }
            }
            if (changed) {
                Log.i(TAG, "play entries=" + plays.size() + " offset=" + miioOffset);
            }
            return changed;
        } catch (Throwable t) {
            Log.w(TAG, "readMiio failed: " + t);
            return false;
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 一次性导入被轮转掉的日志备份（/sdcard/Android/data/com.dsh.tvhud/files/play-import.txt）。 */
    private void importSeed() {
        try {
            File f = new File(SEED_LOG);
            if (!f.exists() || f.length() <= 0) {
                return;
            }
            int size = (int) Math.min(8 << 20, f.length());
            byte[] buf = new byte[size];
            FileInputStream in = new FileInputStream(f);
            int n = in.read(buf);
            in.close();
            if (n <= 0) {
                return;
            }
            long dayStart = UsageDay.dayStartOf(System.currentTimeMillis());
            long dayEnd = dayStart + UsageDay.DAY_MS;
            boolean changed = false;
            for (String line : new String(buf, 0, n, "UTF-8").split("\n")) {
                if (addPlay(PlayEntry.parse(line, dayStart, dayEnd))) {
                    changed = true;
                }
            }
            if (changed) {
                savePlays();
                publishPlays();
            }
            Log.i(TAG, "seed import changed=" + changed + " plays=" + plays.size());
        } catch (Throwable t) {
            Log.w(TAG, "importSeed failed: " + t);
        }
    }

    /** 给详情页读的播放记录快照（按时间排序）。 */
    static List<PlayEntry> peekPlays() {
        List<PlayEntry> p = sPlays;
        return p == null ? new ArrayList<PlayEntry>() : p;
    }

    // ---------- 自愈：无障碍条目 / ADB 开关 ----------

    private void ensureAccessibilityEntry() {
        try {
            String me = getPackageName() + "/" + BootAccessibilityService.class.getName();
            String cur = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
            if (cur == null || !cur.contains(me)) {
                if (cur != null && !cur.isEmpty() && !"null".equals(cur)) {
                    me = cur + ":" + me;
                }
                Settings.Secure.putString(getContentResolver(), "enabled_accessibility_services", me);
                Settings.Secure.putInt(getContentResolver(), "accessibility_enabled", 1);
                Log.i(TAG, "accessibility entry restored");
            }
        } catch (Throwable ignored) {
        }
    }

    private void keepAdbOn() {
        if (!adbKeep) {
            return;
        }
        try {
            if (Settings.Global.getInt(getContentResolver(), "adb_enabled", 0) != 1) {
                Settings.Global.putInt(getContentResolver(), "adb_enabled", 1);
                Log.i(TAG, "adb_enabled 0 -> 1");
            }
            if (Settings.Global.getInt(getContentResolver(), "development_settings_enabled", 0) != 1) {
                Settings.Global.putInt(getContentResolver(), "development_settings_enabled", 1);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------- 屏保判定（UsageStats 增量查询） ----------

    private boolean screensaverOnTop() {
        if (usm == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long begin = (lastQueryEnd == 0) ? now - 86400000L : lastQueryEnd - 2000L;
        lastQueryEnd = now;
        try {
            UsageEvents events = usm.queryEvents(begin, now);
            if (events == null) {
                return SCREENSAVER_PKG.equals(lastTop);
            }
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastTop = event.getPackageName();
                    lastClass = classNameOf(event);
                }
            }
            return SCREENSAVER_PKG.equals(lastTop);
        } catch (Throwable t) {
            Log.w(TAG, "queryEvents failed: " + t);
            return SCREENSAVER_PKG.equals(lastTop);
        }
    }

    /** UsageEvents.Event#getClassName()；老固件若没有就返回 null（分类退回包名）。 */
    private String classNameOf(UsageEvents.Event e) {
        try {
            return e.getClassName();
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- 字体 ----------

    private Typeface cjkTypeface() {
        try {
            Typeface tf = Typeface.createFromFile(CJK_FONT);
            if (tf != null) {
                return tf;
            }
        } catch (Throwable ignored) {
        }
        return Typeface.DEFAULT;
    }

    private Typeface numTypeface() {
        try {
            return Typeface.createFromAsset(getAssets(), "MIUI_EX_Light.ttf");
        } catch (Throwable t) {
            return Typeface.DEFAULT;
        }
    }

    // ---------- 遥控器电量 ----------

    private int readBatteryPct() {
        try {
            if (battKey == null) {
                battKey = findBatteryKey();
            }
            if (battKey == null) {
                return -1;
            }
            String v = Settings.Global.getString(getContentResolver(), battKey);
            if (v == null) {
                battKey = null;
                return -1;
            }
            return Integer.parseInt(v.trim());
        } catch (Throwable t) {
            Log.w(TAG, "read battery failed: " + t);
            return -1;
        }
    }

    /** Settings 键名是 bluetooth_hid_battery_<MAC大写>，MAC 从已配对蓝牙设备里取。 */
    private String findBatteryKey() {
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba != null) {
                for (BluetoothDevice d : ba.getBondedDevices()) {
                    String key = BATT_PREFIX + d.getAddress().toUpperCase(Locale.US);
                    if (Settings.Global.getString(getContentResolver(), key) != null) {
                        return key;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "bonded bluetooth scan failed: " + t);
        }
        return null;
    }

    private void updateBattery() {
        battPct = readBatteryPct();
    }

    // ---------- 视图 ----------

    private void buildHud() {
        if (hud != null) {
            return;
        }
        cjk = cjkTypeface();
        num = numTypeface();
        hud = new HudView(this, cjk, num);
    }

    private void render() {
        if (hud == null) {
            return;
        }
        long sec = SystemClock.elapsedRealtime() / 1000;
        String days = String.valueOf(sec / 86400);
        String time = String.format("%02d:%02d", (sec % 86400) / 3600, (sec % 3600) / 60);
        if (previewMode) {
            long dayStart = UsageDay.dayStartOf(System.currentTimeMillis());
            hud.setData(days, time, battPct, demoDay(dayStart), dayStart, System.currentTimeMillis());
        } else if (usage != null) {
            hud.setData(days, time, battPct, usage.segments(), usage.dayStart, System.currentTimeMillis());
        } else {
            hud.setData(days, time, battPct);
        }
    }

    /** 设计预览用的一天（PREVIEW broadcast 打开；不写入真实数据）。 */
    private java.util.List<UsageDay.Seg> demoDay(long dayStart) {
        java.util.ArrayList<UsageDay.Seg> list = new java.util.ArrayList<>();
        int[][] demo = {
                {7 * 3600 + 20 * 60, 7 * 3600 + 35 * 60, Categories.SYSTEM},
                {7 * 3600 + 35 * 60, 8 * 3600 + 10 * 60, Categories.KIDS},
                {8 * 3600 + 10 * 60, 8 * 3600 + 40 * 60, Categories.VIDEO},
                {8 * 3600 + 40 * 60, 8 * 3600 + 50 * 60, Categories.SYSTEM},
                {12 * 3600 + 30 * 60, 13 * 3600 + 10 * 60, Categories.VIDEO},
                {17 * 3600 + 40 * 60, 18 * 3600, Categories.SYSTEM},
                {18 * 3600, 18 * 3600 + 40 * 60, Categories.VIDEO},
                {18 * 3600 + 40 * 60, 19 * 3600 + 10 * 60, Categories.VIDEO},
                {19 * 3600 + 10 * 60, 19 * 3600 + 50 * 60, Categories.VIDEO},
                {19 * 3600 + 50 * 60, 20 * 3600, Categories.MUSIC},
                {20 * 3600, 20 * 3600 + 30 * 60, Categories.KIDS}
        };
        for (int[] d : demo) {
            list.add(new UsageDay.Seg(dayStart + d[0] * 1000L, dayStart + d[1] * 1000L, d[2]));
        }
        return list;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        previewMode = getSharedPreferences("hud", 0).getBoolean("usage_preview", false);
        if (intent != null && intent.getBooleanExtra("reset_usage", false)) {
            resetUsage();
        }
        if (intent != null && intent.getBooleanExtra("reload_plays", false)) {
            importSeed();
            readMiio(true);
            archiveToday();
            Log.i(TAG, "plays reloaded: " + plays.size());
        }
        if (intent != null && intent.getBooleanExtra("archive_now", false)) {
            archiveToday();
            Log.i(TAG, "archive written: " + HistoryStore.fileFor(this,
                    UsageDay.dayStartOf(System.currentTimeMillis())));
        }
        if (attached) {
            render();
        }
        return START_STICKY;
    }

    /** 清空当天分段，再用 UsageEvents 重建（换了统计口径 / 误记时用）。 */
    private void resetUsage() {
        try {
            usage = new UsageDay(UsageDay.dayStartOf(System.currentTimeMillis()));
            sUsage = usage;
            lastTickWall = 0L;
            lastTickElapsed = 0L;
            bootstrapped = false;
            backfillToday();
            saveUsage();
            if (attached) {
                render();
            }
            Log.i(TAG, "usage reset -> segs=" + usage.segments().size());
        } catch (Throwable t) {
            Log.w(TAG, "resetUsage failed: " + t);
        }
    }

    private void attach() {
        buildHud();
        updateBattery();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                2003, // TYPE_SYSTEM_ALERT
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.x = (int) (getResources().getDisplayMetrics().density * 36.0f);
        lp.y = (int) (getResources().getDisplayMetrics().density * 26.0f);
        try {
            wm.addView(hud, lp);
            attached = true;
            render();
            Log.i(TAG, "HUD attached");
        } catch (Throwable t) {
            Log.e(TAG, "addView failed: " + t);
            attached = false;
        }
    }

    private void detach() {
        if (hud != null) {
            try {
                wm.removeView(hud);
            } catch (Throwable ignored) {
            }
        }
        attached = false;
        Log.i(TAG, "HUD detached");
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (attached) {
            detach();
        }
        saveUsage();
        archiveToday();
        sUsage = null;
        sPlays = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
