package kr.fitorder.common;

import java.util.Map;

/** 원본 값 보관용 최소 JSON 직렬화(문자열 맵 전용). */
public final class Json {

    private Json() {
    }

    public static String object(Map<String, String> values) {
        StringBuilder sb = new StringBuilder("{");
        values.forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(',');
            }
            quote(sb, k);
            sb.append(':');
            if (v == null) {
                sb.append("null");
            } else {
                quote(sb, v);
            }
        });
        return sb.append('}').toString();
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
