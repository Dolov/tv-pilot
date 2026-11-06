package android.content;

import java.io.File;

/**
 * 单元测试用的最小桩。
 * `getExternalFilesDir` / `getFilesDir` 指向一个临时目录，保证离线测试不会碰到真机目录。
 */
public class Context {
    private static final File TMP = new File(System.getProperty("java.io.tmpdir"),
            "tvhud-unit-stubs");

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return null;
    }

    public File getExternalFilesDir(String type) {
        return TMP;
    }

    public File getFilesDir() {
        return TMP;
    }
}
