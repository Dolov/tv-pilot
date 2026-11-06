package com.dsh.tvhud.usage;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 按天归档「使用分段 + 播放记录」到 App 外部私有目录：
 *   /sdcard/Android/data/com.dsh.tvhud/files/history/2026-11-05.json
 * 只存结构化数据（每天几 KB），保留 {@link #KEEP_DAYS} 天，超期自动清理。
 */
public final class HistoryStore {
    private static final String TAG = "TVPILOT";
    public static final int KEEP_DAYS = UsageDay.KEEP_DAYS;

    /**
     * 归档里没有 `v` 字段时的分类版本：1.12 之前的归档只有 scheme 3。
     * 必须写死 3（不能用 UsageDay.SCHEME）：否则以后 bump SCHEME 时，
     * 所有旧归档会被误认成新口径、按错颜色画 —— 这正是加 `v` 要防的事。
     */
    private static final int LEGACY_SCHEME = 3;

    /**
     * 日期格式化不是线程安全的；报告页会在后台线程读归档，Service 在主线程写归档，
     * 两边同时 format 会输出错乱文件名/抛异常。用 ThreadLocal 各拿一份。
     */
    private static final ThreadLocal<SimpleDateFormat> NAME_FMT =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                }
            };

    private static String dayName(long dayStart) {
        return NAME_FMT.get().format(new Date(dayStart));
    }

    public static class DayData {
        public long dayStart;
        public int scheme = UsageDay.SCHEME;
        public final ArrayList<UsageDay.Seg> segs = new ArrayList<>();
        public final ArrayList<PlayEntry> plays = new ArrayList<>();
    }

    private HistoryStore() {
    }

    private static File dir(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) {
            base = c.getFilesDir();
        }
        File d = new File(base, "history");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static File fileFor(Context c, long dayStart) {
        return new File(dir(c), dayName(dayStart) + ".json");
    }

    public static boolean has(Context c, long dayStart) {
        return fileFor(c, dayStart).isFile();
    }

    /**
     * 写归档。返回是否真的写出去了：数据为空 / 磁盘失败返回 false。
     * 调用方据此决定要不要更新「上次写盘时刻」——空写不能占用节流窗口，
     * 否则当天第一份数据会被推迟 5 分钟才落盘。
     */
    public static boolean write(Context c, long dayStart, List<UsageDay.Seg> segs,
                                List<PlayEntry> plays) {
        if (dayStart <= 0L || ((segs == null || segs.isEmpty())
                && (plays == null || plays.isEmpty()))) {
            return false;
        }
        File tmp = new File(dir(c), "tmp.json");
        try {
            JSONObject o = new JSONObject();
            o.put("day", dayStart);
            o.put("v", UsageDay.SCHEME);
            JSONArray sa = new JSONArray();
            if (segs != null) {
                for (UsageDay.Seg s : segs) {
                    JSONArray a = new JSONArray();
                    a.put(s.start).put(s.end).put(s.cat);
                    sa.put(a);
                }
            }
            o.put("segs", sa);
            JSONArray pa = new JSONArray();
            if (plays != null) {
                for (PlayEntry e : plays) {
                    JSONArray a = new JSONArray();
                    a.put(e.ts).put(e.status).put(e.name).put(e.ci).put(e.totalCi).put(e.durMs);
                    pa.put(a);
                }
            }
            o.put("plays", pa);
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(o.toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
            File dest = fileFor(c, dayStart);
            if (tmp.renameTo(dest)) {
                return true;
            }
            tmp.delete();
            Log.w(TAG, "ARCHIVE_WRITE_FAILED rename " + dayName(dayStart));
            return false;
        } catch (Throwable t) {
            tmp.delete();
            Log.w(TAG, "ARCHIVE_WRITE_FAILED " + dayName(dayStart) + ": " + t);
            return false;
        }
    }

    public static DayData read(Context c, long dayStart) {
        try {
            File f = fileFor(c, dayStart);
            if (!f.isFile() || f.length() <= 0) {
                return null;
            }
            int len = (int) Math.min(4 << 20, f.length());
            byte[] buf = new byte[len];
            FileInputStream in = new FileInputStream(f);
            int off = 0;
            try {
                // 单次 read 不保证读满，截断的 JSON 会被当成「没有归档」而悄悄少一天
                while (off < len) {
                    int n = in.read(buf, off, len - off);
                    if (n < 0) {
                        break;
                    }
                    off += n;
                }
            } finally {
                in.close();
            }
            if (off <= 0) {
                return null;
            }
            JSONObject o = new JSONObject(new String(buf, 0, off, "UTF-8"));
            DayData d = new DayData();
            d.dayStart = o.optLong("day", dayStart);
            // 旧归档没有 v；1.12 之前只有 scheme 3（LEGACY_SCHEME 写死，不跟 SCHEME 走）
            d.scheme = o.optInt("v", LEGACY_SCHEME);
            JSONArray sa = o.optJSONArray("segs");
            if (sa != null) {
                for (int i = 0; i < sa.length(); i++) {
                    JSONArray a = sa.getJSONArray(i);
                    d.segs.add(new UsageDay.Seg(a.getLong(0), a.getLong(1), a.getInt(2)));
                }
            }
            JSONArray pa = o.optJSONArray("plays");
            if (pa != null) {
                for (int i = 0; i < pa.length(); i++) {
                    JSONArray a = pa.getJSONArray(i);
                    PlayEntry e = new PlayEntry();
                    e.ts = a.getLong(0);
                    e.status = a.getInt(1);
                    e.name = a.getString(2);
                    e.ci = a.getInt(3);
                    e.totalCi = a.getInt(4);
                    e.durMs = a.getLong(5);
                    d.plays.add(e);
                }
            }
            String problem = d.dayStart != dayStart ? "day 字段与文件名不一致" : verify(d);
            if (problem != null) {
                Log.w(TAG, "ARCHIVE_CORRUPT " + dayName(dayStart) + ": " + problem);
            }
            return d;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 归档完整性自检：分段必须非负、在当天之内、互不重叠；播放记录也不能越出当天。
     * 返回问题描述；没问题返回 null。读取时调用，坏文件会打 ARCHIVE_CORRUPT 日志，
     * 真机 E2E 会把这条日志当失败——防止「数字悄悄偏小」这类问题被漏掉。
     */
    public static String verify(DayData d) {
        if (d == null) {
            return "day data == null";
        }
        long dayEnd = d.dayStart + UsageDay.DAY_MS;
        long prevEnd = Long.MIN_VALUE;
        for (int i = 0; i < d.segs.size(); i++) {
            UsageDay.Seg sg = d.segs.get(i);
            if (sg.end <= sg.start) {
                return "段 " + i + " 时长非正";
            }
            if (sg.start < d.dayStart || sg.end > dayEnd) {
                return "段 " + i + " 越出当天";
            }
            if (sg.cat < 0 || sg.cat > 4) {
                return "段 " + i + " 类别越界: " + sg.cat;
            }
            if (sg.start < prevEnd) {
                return "段 " + i + " 与上一段重叠";
            }
            prevEnd = sg.end;
        }
        for (int i = 0; i < d.plays.size(); i++) {
            PlayEntry p = d.plays.get(i);
            if (p == null) {
                return "播放记录 " + i + " 为空";
            }
            if (p.ts < d.dayStart || p.ts >= dayEnd) {
                return "播放记录 " + i + " 越出当天";
            }
            if (p.name == null) {
                return "播放记录 " + i + " 片名为空";
            }
        }
        return null;
    }

    /** 删除超过 {@link #KEEP_DAYS} 天的归档（服务启动时调用一次）。 */
    public static void cleanup(Context c) {
        try {
            long minDay = UsageDay.dayStartOf(System.currentTimeMillis())
                    - (KEEP_DAYS - 1L) * UsageDay.DAY_MS;
            File[] files = dir(c).listFiles();
            if (files == null) {
                return;
            }
            for (File f : files) {
                String n = f.getName();
                if (!n.endsWith(".json") || "tmp.json".equals(n)) {
                    continue;
                }
                try {
                    Date d = NAME_FMT.get().parse(n.substring(0, n.length() - 5));
                    if (d != null && d.getTime() < minDay) {
                        f.delete();
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
