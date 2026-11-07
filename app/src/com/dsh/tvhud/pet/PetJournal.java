package com.dsh.tvhud.pet;

import java.util.ArrayList;
import java.util.List;

/**
 * 宠物每日流水：只存「天级摘要」，给家长周报用，纯逻辑不碰 Android。
 *
 * 为什么单独存：UsageDay/HistoryStore 只有观看时长，没有「按时休息/答题/超时」这些
 * 宠物自己的事件；周报要复盘「这周他有没有自己按休息」，必须有这层。只保留最近 14 天，
 * 存储量 < 1KB。
 *
 * 编码：`dayStart|sessions|extensions|quizCorrect|quizTotal|overtime|expGain`，多条用 `;` 分隔。
 */
public final class PetJournal {
    public static final int KEEP_DAYS = 14;

    public static final class Day {
        public long dayStart;
        public int sessions;
        public int extensions;
        public int quizCorrect;
        public int quizTotal;
        public boolean overtime;
        public int expGain;

        public Day() {
        }

        public Day(long dayStart) {
            this.dayStart = dayStart;
        }

        public boolean compliant() {
            return !overtime;
        }
    }

    /** 汇总一段时间的宠物行为。 */
    public static final class Summary {
        public int days;
        public int sessions;
        public int extensions;
        public int quizCorrect;
        public int quizTotal;
        public int overtimeDays;
        public int compliantDays;
        public int expGain;

        public int quizRate() {
            if (quizTotal <= 0) {
                return -1;
            }
            return Math.round(quizCorrect * 100f / quizTotal);
        }
    }

    private PetJournal() {
    }

    /** 追加一天摘要；同一天重复写就覆盖（服务重启/多次 ensureDay 不会重复）。 */
    public static String append(String csv, Day day) {
        List<Day> days = parse(csv);
        for (int i = days.size() - 1; i >= 0; i--) {
            if (days.get(i).dayStart == day.dayStart) {
                days.set(i, day);
                return encode(days);
            }
        }
        days.add(day);
        while (days.size() > KEEP_DAYS) {
            days.remove(0);
        }
        return encode(days);
    }

    public static List<Day> parse(String csv) {
        List<Day> out = new ArrayList<Day>();
        if (csv == null || csv.isEmpty()) {
            return out;
        }
        String[] rows = csv.split(";");
        for (String row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String[] cols = row.split("\\|");
            if (cols.length < 7) {
                continue;
            }
            try {
                Day d = new Day();
                d.dayStart = Long.parseLong(cols[0]);
                d.sessions = Integer.parseInt(cols[1]);
                d.extensions = Integer.parseInt(cols[2]);
                d.quizCorrect = Integer.parseInt(cols[3]);
                d.quizTotal = Integer.parseInt(cols[4]);
                d.overtime = "1".equals(cols[5]);
                d.expGain = Integer.parseInt(cols[6]);
                out.add(d);
            } catch (Throwable ignored) {
                // 单行坏了跳过，不影响整份周报
            }
        }
        return out;
    }

    public static String encode(List<Day> days) {
        StringBuilder sb = new StringBuilder();
        for (Day d : days) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(d.dayStart).append('|')
                    .append(d.sessions).append('|')
                    .append(d.extensions).append('|')
                    .append(d.quizCorrect).append('|')
                    .append(d.quizTotal).append('|')
                    .append(d.overtime ? 1 : 0).append('|')
                    .append(d.expGain);
        }
        return sb.toString();
    }

    /** 从 fromDayStart（含）开始汇总。 */
    public static Summary summarize(String csv, long fromDayStart) {
        Summary sum = new Summary();
        for (Day d : parse(csv)) {
            if (d.dayStart < fromDayStart) {
                continue;
            }
            sum.days++;
            sum.sessions += d.sessions;
            sum.extensions += d.extensions;
            sum.quizCorrect += d.quizCorrect;
            sum.quizTotal += d.quizTotal;
            sum.expGain += d.expGain;
            if (d.overtime) {
                sum.overtimeDays++;
            } else {
                sum.compliantDays++;
            }
        }
        return sum;
    }

    public static Day find(String csv, long dayStart) {
        for (Day d : parse(csv)) {
            if (d.dayStart == dayStart) {
                return d;
            }
        }
        return null;
    }
}
