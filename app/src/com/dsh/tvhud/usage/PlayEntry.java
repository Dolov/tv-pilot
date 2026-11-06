package com.dsh.tvhud.usage;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一条「开始播放某集」的记录，来自 /sdcard/miio/miothost.txt 里 MiTVOt 的米家上报。
 * 只保留当天、有片名的事件；同一集（片名+集号）只留第一次出现的时间。
 */
public class PlayEntry {
    private static final Pattern TS = Pattern.compile(
            "^(\\d{4}):(\\d{2}):(\\d{2}):(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})");
    private static final Pattern BODY = Pattern.compile(
            "\"play_status\":(\\d+),\"media_id\":\"(\\d+)\",\"video_name\":\"([^\"]*)\","
                    + "\"ci\":(\\d+),\"total_ci\":(\\d+).*?\"total_duration\":(\\d+)");

    public long ts;
    public String name = "";
    public int ci;
    public int totalCi;
    public long durMs;
    public int status;

    /** 同一集去重用：片名 + 集号（同一天重看也只记第一次）。 */
    public String key() {
        return name + "|" + ci;
    }

    /** 解析一行 miothost 日志；不是播放上报或不在 [dayStart, dayEnd) 返回 null。 */
    public static PlayEntry parse(String line, long dayStart, long dayEnd) {
        if (line == null || !line.contains("play_status") || !line.contains("video_name")) {
            return null;
        }
        Matcher t = TS.matcher(line);
        if (!t.find()) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.set(Integer.parseInt(t.group(1)), Integer.parseInt(t.group(2)) - 1,
                Integer.parseInt(t.group(3)), Integer.parseInt(t.group(4)),
                Integer.parseInt(t.group(5)), Integer.parseInt(t.group(6)));
        c.set(Calendar.MILLISECOND, Integer.parseInt(t.group(7)));
        long ts = c.getTimeInMillis();
        if (ts < dayStart || ts >= dayEnd) {
            return null;
        }
        Matcher m = BODY.matcher(line.replace("\\\"", "\""));
        if (!m.find()) {
            return null;
        }
        PlayEntry e = new PlayEntry();
        e.ts = ts;
        e.status = Integer.parseInt(m.group(1));
        e.name = m.group(3).trim();
        e.ci = Integer.parseInt(m.group(4));
        e.totalCi = Integer.parseInt(m.group(5));
        try {
            e.durMs = Long.parseLong(m.group(6));
        } catch (Throwable ignored) {
        }
        return e.name.isEmpty() ? null : e;
    }
}
