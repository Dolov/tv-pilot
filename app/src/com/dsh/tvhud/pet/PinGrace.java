package com.dsh.tvhud.pet;

/**
 * 家长设置 PIN 的免输入窗口（纯逻辑，离线单测覆盖）。
 *
 * 输对一次 PIN 后 5 分钟内再进家长设置不用重输：存的是「窗口截止的绝对时间戳」，
 * 不看进程活没活、Activity 有没有被系统回收，退出重进、切到别的 App 再回来都算数。
 * 超过 5 分钟自然失效，不需要定时器；家长连错 5 次锁 60 秒的锁定优先于免输入。
 *
 * 为什么不做成「每次进来刷新 5 分钟」：那样家长在设置里待着不动，
 * 窗口会一直被续上，等于只在校验成功的那一次之后永久免密；
 * 固定窗口更好解释——「上一次输对后的 5 分钟内」。
 */
public final class PinGrace {
    /** 输对一次 PIN 后多少毫秒内再进免输。 */
    public static final long WINDOW_MS = 5L * 60L * 1000L;

    private PinGrace() {
    }

    /** 输对 PIN 的时刻 now 对应的免输入窗口截止时间。 */
    public static long okUntil(long now) {
        return now + WINDOW_MS;
    }

    /** 窗口是否还没过期（截止时刻等于当前时刻即视为过期，边界取「不到 5 分钟」）。 */
    public static boolean active(long okUntil, long now) {
        return okUntil > now;
    }

    /** 这次进页面要不要显示 PIN：锁定中一定要拦；没锁定则看窗口。 */
    public static boolean needPin(boolean locked, long okUntil, long now) {
        return locked || !active(okUntil, now);
    }
}
