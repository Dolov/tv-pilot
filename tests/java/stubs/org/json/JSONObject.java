package org.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单元测试用的迷你 JSON 实现（只实现用到的部分）。
 * 和 JSONArray 配套：能 put / toString / 从字符串解析，够归档文件读写往返用。
 */
public class JSONObject {
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();

    public JSONObject() {
    }

    public JSONObject(String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        Parser p = new Parser(json);
        p.parse(this);
    }

    public JSONObject put(String key, long value) {
        values.put(key, Long.valueOf(value));
        return this;
    }

    public JSONObject put(String key, int value) {
        values.put(key, Integer.valueOf(value));
        return this;
    }

    public JSONObject put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public long optLong(String key, long defValue) {
        Object o = values.get(key);
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o == null) {
            return defValue;
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Throwable t) {
            return defValue;
        }
    }

    public int optInt(String key, int defValue) {
        long v = optLong(key, defValue);
        return (int) v;
    }

    public JSONArray optJSONArray(String key) {
        Object o = values.get(key);
        return o instanceof JSONArray ? (JSONArray) o : null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            JSONArray.escape(sb, e.getKey());
            sb.append(':');
            JSONArray.escape(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        void parse(JSONObject out) {
            i = 0;
            skipSpace();
            if (i >= s.length() || s.charAt(i) != '{') {
                return;
            }
            i++;   // '{'
            while (i < s.length()) {
                skipSpace();
                if (i >= s.length()) {
                    break;
                }
                char c = s.charAt(i);
                if (c == '}') {
                    return;
                }
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c != '"') {
                    i++;
                    continue;
                }
                String key = parseString();
                skipSpace();
                if (i < s.length() && s.charAt(i) == ':') {
                    i++;
                }
                skipSpace();
                Object value = parseValue();
                out.values.put(key, value);
            }
        }

        Object parseValue() {
            skipSpace();
            if (i >= s.length()) {
                return null;
            }
            char c = s.charAt(i);
            if (c == '[') {
                JSONArray arr = new JSONArray();
                i++;   // '['
                while (i < s.length()) {
                    skipSpace();
                    if (i >= s.length()) {
                        break;
                    }
                    char k = s.charAt(i);
                    if (k == ']') {
                        i++;
                        break;
                    }
                    if (k == ',') {
                        i++;
                        continue;
                    }
                    JSONArray nested = new JSONArray();
                    if (k == '[') {
                        JSONArray tmp = new JSONArray();
                        // 复用 JSONArray 的数组解析：借它把嵌套数组读出来
                        Object v = readNestedArray();
                        arr.put(v);
                        continue;
                    }
                    Object v = readScalar(nested);
                    arr.put(v);
                }
                return arr;
            }
            if (c == '{') {
                JSONObject obj = new JSONObject();
                i++;
                skipSpace();
                if (i < s.length() && s.charAt(i) == '{') {
                    i--;
                    new Parser(s.substring(i)).parse(obj);
                    // 跳过整个对象
                    int depth = 0;
                    boolean inStr = false;
                    for (; i < s.length(); i++) {
                        char ch = s.charAt(i);
                        if (inStr) {
                            if (ch == '\\') {
                                i++;
                            } else if (ch == '"') {
                                inStr = false;
                            }
                            continue;
                        }
                        if (ch == '"') {
                            inStr = true;
                        } else if (ch == '{') {
                            depth++;
                        } else if (ch == '}') {
                            depth--;
                            if (depth == 0) {
                                i++;
                                break;
                            }
                        }
                    }
                    return obj;
                }
                return obj;
            }
            JSONArray holder = new JSONArray();
            return readScalar(holder);
        }

        Object readNestedArray() {
            JSONArray arr = new JSONArray();
            i++;   // '['
            while (i < s.length()) {
                skipSpace();
                if (i >= s.length()) {
                    break;
                }
                char k = s.charAt(i);
                if (k == ']') {
                    i++;
                    break;
                }
                if (k == ',') {
                    i++;
                    continue;
                }
                JSONArray nested = new JSONArray();
                if (k == '[') {
                    Object v = readNestedArray();
                    arr.put(v);
                } else {
                    Object v = readScalar(nested);
                    arr.put(v);
                }
            }
            return arr;
        }

        Object readScalar(JSONArray holder) {
            skipSpace();
            if (i >= s.length()) {
                return null;
            }
            char c = s.charAt(i);
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != ']'
                        && s.charAt(i) != '}') {
                    i++;
                }
                return Boolean.valueOf(c == 't');
            }
            if (c == 'n') {
                while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != ']'
                        && s.charAt(i) != '}') {
                    i++;
                }
                return null;
            }
            int start = i;
            while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String num = s.substring(start, i);
            try {
                if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                    return Double.valueOf(num);
                }
                return Long.valueOf(num);
            } catch (Throwable t) {
                return 0L;
            }
        }

        void skipSpace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        String parseString() {
            StringBuilder sb = new StringBuilder();
            i++;   // opening quote
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    if (e == 'n') {
                        sb.append('\n');
                    } else if (e == 'r') {
                        sb.append('\r');
                    } else if (e == 't') {
                        sb.append('\t');
                    } else if (e == 'u' && i + 4 <= s.length()) {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    } else {
                        sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
