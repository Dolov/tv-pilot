package com.dsh.tvhud;

import android.content.Context;

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
    public static final int KEEP_DAYS = 365;

    private static final SimpleDateFormat NAME_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static class DayData {
        public long dayStart;
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
        return new File(dir(c), NAME_FMT.format(new Date(dayStart)) + ".json");
    }

    public static boolean has(Context c, long dayStart) {
        return fileFor(c, dayStart).isFile();
    }

    public static void write(Context c, long dayStart, List<UsageDay.Seg> segs,
                             List<PlayEntry> plays) {
        if (dayStart <= 0L || ((segs == null || segs.isEmpty())
                && (plays == null || plays.isEmpty()))) {
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("day", dayStart);
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
            File tmp = new File(dir(c), "tmp.json");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(o.toString().getBytes("UTF-8"));
            out.close();
            File dest = fileFor(c, dayStart);
            if (!tmp.renameTo(dest)) {
                tmp.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    public static DayData read(Context c, long dayStart) {
        try {
            File f = fileFor(c, dayStart);
            if (!f.isFile() || f.length() <= 0) {
                return null;
            }
            byte[] buf = new byte[(int) Math.min(4 << 20, f.length())];
            FileInputStream in = new FileInputStream(f);
            int n = in.read(buf);
            in.close();
            if (n <= 0) {
                return null;
            }
            JSONObject o = new JSONObject(new String(buf, 0, n, "UTF-8"));
            DayData d = new DayData();
            d.dayStart = o.optLong("day", dayStart);
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
            return d;
        } catch (Throwable ignored) {
            return null;
        }
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
                    Date d = NAME_FMT.parse(n.substring(0, n.length() - 5));
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
