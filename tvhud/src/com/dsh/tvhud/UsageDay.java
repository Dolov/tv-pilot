package com.dsh.tvhud;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * 当天使用分段：每段 = [start, end) 的 wall-clock 毫秒 + 内容类型。
 * 只记录「人在看」的时间；熄屏/关机/屏保期间没有段，长条自然留空。
 */
public class UsageDay {
    public static final long DAY_MS = 86400000L;

    /**
     * 类别编码方案版本：Categories 增删/重排时必须 +1，旧数据会被丢弃而不是画错颜色。
     * v3：播放页（tvhome DetailsActivity）改按 VIDEO 记录；旧数据全是 SYSTEM，升级后
     * 当天数据用 UsageEvents 重建一次。
     */
    public static final int SCHEME = 3;

    public static final class Seg {
        public long start;
        public long end;
        public int cat;

        Seg(long start, long end, int cat) {
            this.start = start;
            this.end = end;
            this.cat = cat;
        }
    }

    public long dayStart;
    private final ArrayList<Seg> segs = new ArrayList<>();

    public UsageDay(long dayStart) {
        this.dayStart = dayStart;
    }

    public static long dayStartOf(long wallMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(wallMs);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public void reset(long newDayStart) {
        dayStart = newDayStart;
        segs.clear();
    }

    public ArrayList<Seg> segments() {
        return segs;
    }

    /** 已记录的最后一个时间点；空的时候就是当天 0 点。 */
    public long lastEnd() {
        return segs.isEmpty() ? dayStart : segs.get(segs.size() - 1).end;
    }

    /** 追加一段 [from, to)，同类且间隔 ≤2s 时合并。 */
    public void append(long from, long to, int cat) {
        from = Math.max(from, dayStart);
        to = Math.min(to, dayStart + DAY_MS);
        if (to <= from) {
            return;
        }
        Seg last = segs.isEmpty() ? null : segs.get(segs.size() - 1);
        if (last != null && last.cat == cat && last.end <= to && from - last.end <= 2000L) {
            if (to > last.end) {
                last.end = to;
            }
        } else {
            segs.add(new Seg(from, to, cat));
        }
    }

    public void save(Context ctx) {
        try {
            JSONArray arr = new JSONArray();
            for (Seg s : segs) {
                JSONArray a = new JSONArray();
                a.put(s.start - dayStart).put(s.end - dayStart).put(s.cat);
                arr.put(a);
            }
            SharedPreferences sp = ctx.getSharedPreferences("hud", 0);
            sp.edit().putLong("usage_day_start", dayStart)
                    .putInt("usage_scheme", SCHEME)
                    .putString("usage_json", arr.toString())
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static UsageDay load(Context ctx, long todayStart) {
        UsageDay u = new UsageDay(todayStart);
        try {
            SharedPreferences sp = ctx.getSharedPreferences("hud", 0);
            long savedDay = sp.getLong("usage_day_start", 0L);
            if (savedDay != todayStart) {
                return u;
            }
            if (sp.getInt("usage_scheme", 0) != SCHEME) {
                return u; // 旧颜色编码，丢掉，避免画成错误颜色
            }
            String json = sp.getString("usage_json", null);
            if (json == null || json.isEmpty()) {
                return u;
            }
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray a = arr.getJSONArray(i);
                long s = todayStart + a.getLong(0);
                long e = todayStart + a.getLong(1);
                int c = a.getInt(2);
                if (e > s) {
                    // 直接塞进去，不合并（保存时已经合并过）
                    u.segs.add(new Seg(s, e, c));
                }
            }
        } catch (Throwable ignored) {
        }
        return u;
    }
}
