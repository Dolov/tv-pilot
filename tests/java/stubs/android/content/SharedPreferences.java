package android.content;

/** 单元测试用的最小桩；测试不会真的读写 SharedPreferences。 */
public class SharedPreferences {
    public long getLong(String key, long defValue) {
        return defValue;
    }

    public int getInt(String key, int defValue) {
        return defValue;
    }

    public String getString(String key, String defValue) {
        return defValue;
    }

    public Editor edit() {
        return new Editor();
    }

    public static final class Editor {
        public Editor putLong(String key, long value) {
            return this;
        }

        public Editor putInt(String key, int value) {
            return this;
        }

        public Editor putString(String key, String value) {
            return this;
        }

        public void apply() {
        }
    }
}
