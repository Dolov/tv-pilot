package com.dsh.tvhud;

/**
 * 归档落盘策略：决定「这一次要不要写归档文件」。
 *
 * 为什么单独抽出来：HudService 依赖 Android，没法离线跑单元测试；
 * 这里是纯逻辑，可以在 tests/java 里断言（写放大优化最容易改坏的地方）。
 *
 * 规则：
 * - 强制写（跨天定稿 / 空闲 / 销毁 / 手动）：永远写；
 * - 非强制：数据没变 → 不写；距上次写不足间隔 → 不写。
 */
public final class ArchivePolicy {
    /** 归档最小间隔：5 分钟。 */
    public static final long DEFAULT_INTERVAL_MS = 300000L;

    private ArchivePolicy() {
    }

    /**
     * @param force     是否强制写
     * @param changed   数据相对上次写盘是否有变化
     * @param now       当前时刻
     * @param lastWriteMs 上次写盘时刻；从未写过用 0
     * @param intervalMs 最小间隔
     * @return true = 应该写
     */
    public static boolean shouldWrite(boolean force, boolean changed, long now,
                                      long lastWriteMs, long intervalMs) {
        if (force) {
            return true;
        }
        if (!changed) {
            return false;   // 数据跟上次写盘一模一样，不必再写
        }
        if (lastWriteMs <= 0L) {
            return true;    // 从没写过
        }
        return now - lastWriteMs >= intervalMs;
    }
}
