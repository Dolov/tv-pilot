package org.json;

import java.util.ArrayList;
import java.util.List;

/**
 * 单元测试用的迷你 JSON 实现（不是 Android 的 org.json，只实现用到的部分）。
 *
 * 为什么要写成「真的能跑」：归档的落盘→读回往返必须能离线验证文件没写坏，
 * 空的桩会让 HistoryStore.write/read 变成空转，测不出任何东西。
 *
 * 支持：put(long|int|Object)、toString()、从字符串解析、getJSONArray / getLong /
 * getInt / getString、length。字符串转义按 JSON 规范处理。
 */
public class JSONArray {
    private final List<Object> items = new ArrayList<Object>();

    public JSONArray() {
    }

    public JSONArray(String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        Parser p = new Parser(json);
        p.skipSpace();
        p.parseValue(this);
    }

    public JSONArray put(long value) {
        items.add(Long.valueOf(value));
        return this;
    }

    public JSONArray put(int value) {
        items.add(Integer.valueOf(value));
        return this;
    }

    public JSONArray put(Object value) {
        items.add(value);
        return this;
    }

    public int length() {
        return items.size();
    }

    public JSONArray getJSONArray(int index) {
        Object o = items.get(index);
        return o instanceof JSONArray ? (JSONArray) o : null;
    }

    public long getLong(int index) {
        Object o = items.get(index);
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Throwable t) {
            return 0L;
        }
    }

    public int getInt(int index) {
        return (int) getLong(index);
    }

    public String getString(int index) {
        Object o = items.get(index);
        return o == null ? "" : String.valueOf(o);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            escape(sb, items.get(i));
        }
        return sb.append(']').toString();
    }

    static void escape(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof JSONArray || o instanceof JSONObject) {
            sb.append(o.toString());
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(String.valueOf(o));
        } else {
            sb.append('"');
            String s = String.valueOf(o);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\').append(c);
                } else if (c == '\n') {
                    sb.append("\\n");
                } else if (c == '\r') {
                    sb.append("\\r");
                } else if (c == '\t') {
                    sb.append("\\t");
                } else {
                    sb.append(c);
                }
            }
            sb.append('"');
        }
    }

    /** 极简解析器：够用即可，不追求完备。 */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        void skipSpace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        void parseValue(JSONArray out) {
            skipSpace();
            if (i >= s.length()) {
                return;
            }
            char c = s.charAt(i);
            if (c == '[') {
                parseArray(out);
            } else if (c == '"') {
                out.items.add(parseString());
            } else if (c == 't' || c == 'f') {
                while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != ']') {
                    i++;
                }
                out.items.add(Boolean.valueOf(c == 't'));
            } else if (c == 'n') {
                while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != ']') {
                    i++;
                }
                out.items.add(null);
            } else {
                int start = i;
                while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                    i++;
                }
                String num = s.substring(start, i);
                try {
                    if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                        out.items.add(Double.valueOf(num));
                    } else {
                        out.items.add(Long.valueOf(num));
                    }
                } catch (Throwable t) {
                    out.items.add(0L);
                }
            }
        }

        void parseArray(JSONArray out) {
            i++;   // '['
            while (i < s.length()) {
                skipSpace();
                if (i >= s.length()) {
                    break;
                }
                char c = s.charAt(i);
                if (c == ']') {
                    i++;
                    return;
                }
                if (c == ',') {
                    i++;
                    continue;
                }
                JSONArray nested = new JSONArray();
                if (c == '[') {
                    parseArray(nested);
                } else {
                    parseValue(nested);
                }
                out.items.add(nested);
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
