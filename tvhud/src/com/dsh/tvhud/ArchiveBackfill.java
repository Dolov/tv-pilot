package com.dsh.tvhud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动补写（backfill）的合并规则：把「SharedPreferences 里还留着的那一天」和
 * 「归档里已有的那一天」合成一份最全的数据。
 *
 * 单独抽成纯类的原因：这是最关键的安全网逻辑（电视午夜关机时，历史数据全靠它），
 * 但它原本埋在 HudService（依赖 Android）里没法离线测。
 *
 * 规则：
 * - 分段：谁的时长合计大用谁（归档通常更新，但 prefs 有 60 秒精度、归档有 5 分钟精度，
 *   谁更全用谁；不会拼接，避免出现重叠段）；
 * - 播放记录：按「片名+集号」取并集。
 */
public final class ArchiveBackfill {
    private ArchiveBackfill() {
    }

    /** 分段时长合计（毫秒）。 */
    public static long totalMs(List<UsageDay.Seg> segs) {
        long sum = 0L;
        if (segs == null) {
            return 0L;
        }
        for (int i = 0; i < segs.size(); i++) {
            UsageDay.Seg s = segs.get(i);
            if (s != null) {
                sum += Math.max(0L, s.end - s.start);
            }
        }
        return sum;
    }

    /** 分段：归档更全就用归档的，否则用待补写的。 */
    public static List<UsageDay.Seg> mergeSegs(List<UsageDay.Seg> archived,
                                                List<UsageDay.Seg> pending) {
        long a = totalMs(archived);
        long p = totalMs(pending);
        if (a >= p) {
            return archived == null ? new ArrayList<UsageDay.Seg>() : archived;
        }
        return pending == null ? new ArrayList<UsageDay.Seg>() : pending;
    }

    /** 播放记录：按「片名+集号」取并集，保留归档原有顺序。 */
    public static List<PlayEntry> mergePlays(List<PlayEntry> archived, List<PlayEntry> pending) {
        List<PlayEntry> out = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        if (archived != null) {
            for (int i = 0; i < archived.size(); i++) {
                PlayEntry e = archived.get(i);
                if (e != null && keys.add(e.key())) {
                    out.add(e);
                }
            }
        }
        if (pending != null) {
            for (int i = 0; i < pending.size(); i++) {
                PlayEntry e = pending.get(i);
                if (e != null && keys.add(e.key())) {
                    out.add(e);
                }
            }
        }
        return out;
    }
}
