package com.medichain.lite;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON writer and reader, enough for the MediChain API. No external libraries. */
final class Json {
    private Json() {
    }

    // ------------------------------------------------------------ writing

    static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb);
        return sb.toString();
    }

    private static void write(Object o, StringBuilder sb) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof CharSequence || o instanceof Temporal || o instanceof Enum) {
            quote(o.toString(), sb);
        } else if (o instanceof Collection<?> c) {
            sb.append('[');
            boolean first = true;
            for (Object x : c) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(x, sb);
            }
            sb.append(']');
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else {
            write(toMap(o), sb);
        }
    }

    /** Records by component, ordinary objects by their public getters. */
    static Map<String, Object> toMap(Object o) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            if (o.getClass().isRecord()) {
                for (RecordComponent rc : o.getClass().getRecordComponents()) {
                    Method accessor = rc.getAccessor();
                    accessor.setAccessible(true);
                    map.put(rc.getName(), accessor.invoke(o));
                }
            } else {
                for (Method m : o.getClass().getMethods()) {
                    String n = m.getName();
                    if (m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()) && n.startsWith("get")
                            && n.length() > 3 && !n.equals("getClass")) {
                        map.put(Character.toLowerCase(n.charAt(3)) + n.substring(4), m.invoke(o));
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return map;
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------ reading

    /** Parses a JSON object body into a map (strings, numbers, booleans, null, nested objects and arrays). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> readObject(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object value = new Reader(text).value();
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) value;
    }

    private static final class Reader {
        private final String s;
        private int i;

        Reader(String s) {
            this.s = s;
        }

        Object value() {
            skip();
            if (i >= s.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> map = new LinkedHashMap<>();
                skip();
                if (peek('}')) {
                    return map;
                }
                do {
                    skip();
                    String key = string();
                    skip();
                    expect(':');
                    map.put(key, value());
                    skip();
                } while (peek(','));
                expect('}');
                return map;
            }
            if (c == '[') {
                i++;
                List<Object> list = new ArrayList<>();
                skip();
                if (peek(']')) {
                    return list;
                }
                do {
                    list.add(value());
                    skip();
                } while (peek(','));
                expect(']');
                return list;
            }
            if (c == '"') {
                return string();
            }
            int start = i;
            while (i < s.length() && ",}] \n\r\t".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            String token = s.substring(start, i);
            switch (token) {
                case "null": return null;
                case "true": return Boolean.TRUE;
                case "false": return Boolean.FALSE;
                default:
                    if (token.contains(".") || token.contains("e") || token.contains("E")) {
                        return Double.valueOf(token);
                    }
                    return Long.valueOf(token);
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private boolean peek(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + i);
            }
        }
    }
}
