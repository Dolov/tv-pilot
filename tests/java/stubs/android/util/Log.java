package android.util;

/** 单元测试用的最小桩；测试不需要真的写日志。 */
public final class Log {
    private Log() {
    }

    public static int v(String tag, String msg) {
        return 0;
    }

    public static int d(String tag, String msg) {
        return 0;
    }

    public static int i(String tag, String msg) {
        return 0;
    }

    public static int w(String tag, String msg) {
        return 0;
    }

    public static int e(String tag, String msg) {
        return 0;
    }
}
