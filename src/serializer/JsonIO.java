package serializer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader/writer for project-specific serialization. */
public final class JsonIO {
    private JsonIO() {
    }

    public static Object readFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String json = new String(bytes, StandardCharsets.UTF_8);
        return parse(json);
    }

    public static Object readFile(String filename) throws IOException {
        return readFile(new File(filename));
    }

    public static void writeFile(File file, Object value) throws IOException {
        String json = toJson(value, true);
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(json);
        }
    }

    public static void writeFile(String filename, Object value) throws IOException {
        writeFile(new File(filename), value);
    }

    public static Object parse(String json) throws IOException {
        Parser p = new Parser(json);
        Object value = p.parseValue();
        p.skipWhitespace();
        if (!p.isEnd()) {
            throw new IOException("Unexpected trailing characters at position " + p.pos);
        }
        return value;
    }

    public static String toJson(Object value, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb, pretty, 0);
        if (pretty) sb.append("\n");
        return sb.toString();
    }

    private static void writeValue(Object value, StringBuilder sb, boolean pretty, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append('"').append(escape((String) value)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            writeObject(map, sb, pretty, indent);
        } else if (value instanceof Iterable) {
            @SuppressWarnings("unchecked")
            Iterable<Object> list = (Iterable<Object>) value;
            writeArray(list, sb, pretty, indent);
        } else {
            sb.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb, boolean pretty, int indent) {
        sb.append("{");
        if (pretty && !map.isEmpty()) sb.append("\n");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (pretty) indent(sb, indent + 2);
            sb.append('"').append(escape(entry.getKey())).append('"').append(":");
            if (pretty) sb.append(" ");
            writeValue(entry.getValue(), sb, pretty, indent + 2);
            i++;
            if (i < map.size()) sb.append(",");
            if (pretty) sb.append("\n");
        }
        if (pretty && !map.isEmpty()) indent(sb, indent);
        sb.append("}");
    }

    private static void writeArray(Iterable<Object> list, StringBuilder sb, boolean pretty, int indent) {
        sb.append("[");
        List<Object> values = new ArrayList<>();
        for (Object v : list) values.add(v);
        if (pretty && !values.isEmpty()) sb.append("\n");
        for (int i = 0; i < values.size(); i++) {
            if (pretty) indent(sb, indent + 2);
            writeValue(values.get(i), sb, pretty, indent + 2);
            if (i < values.size() - 1) sb.append(",");
            if (pretty) sb.append("\n");
        }
        if (pretty && !values.isEmpty()) indent(sb, indent);
        sb.append("]");
    }

    private static void indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append(' ');
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int j = hex.length(); j < 4; j++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static final class Parser {
        private final String s;
        private int pos;

        private Parser(String s) {
            this.s = s;
            this.pos = 0;
        }

        private boolean isEnd() {
            return pos >= s.length();
        }

        private void skipWhitespace() {
            while (!isEnd()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private Object parseValue() throws IOException {
            skipWhitespace();
            if (isEnd()) {
                throw new IOException("Unexpected end of input");
            }
            char c = s.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw new IOException("Unexpected character '" + c + "' at position " + pos);
        }

        private Map<String, Object> parseObject() throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                if (peek('}')) {
                    pos++;
                    break;
                }
                throw new IOException("Expected ',' or '}' at position " + pos);
            }
            return map;
        }

        private List<Object> parseArray() throws IOException {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                if (peek(']')) {
                    pos++;
                    break;
                }
                throw new IOException("Expected ',' or ']' at position " + pos);
            }
            return list;
        }

        private String parseString() throws IOException {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!isEnd()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (isEnd()) throw new IOException("Unterminated escape sequence at position " + pos);
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 > s.length()) {
                                throw new IOException("Invalid unicode escape at position " + pos);
                            }
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                int code = Integer.parseInt(hex, 16);
                                sb.append((char) code);
                            } catch (NumberFormatException ex) {
                                throw new IOException("Invalid unicode escape at position " + (pos - 4));
                            }
                            break;
                        default:
                            throw new IOException("Invalid escape character '" + esc + "' at position " + pos);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IOException("Unterminated string at position " + pos);
        }

        private Object parseNumber() throws IOException {
            int start = pos;
            if (peek('-')) pos++;
            while (!isEnd() && Character.isDigit(s.charAt(pos))) {
                pos++;
            }
            boolean isFloat = false;
            if (!isEnd() && s.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (!isEnd() && Character.isDigit(s.charAt(pos))) {
                    pos++;
                }
            }
            if (!isEnd() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (!isEnd() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (!isEnd() && Character.isDigit(s.charAt(pos))) {
                    pos++;
                }
            }
            String num = s.substring(start, pos);
            try {
                if (isFloat) {
                    return Double.parseDouble(num);
                }
                long val = Long.parseLong(num);
                return val;
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid number '" + num + "' at position " + start);
            }
        }

        private Boolean parseBoolean() throws IOException {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IOException("Invalid boolean at position " + pos);
        }

        private Object parseNull() throws IOException {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IOException("Invalid literal at position " + pos);
        }

        private void expect(char expected) throws IOException {
            skipWhitespace();
            if (isEnd() || s.charAt(pos) != expected) {
                throw new IOException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }

        private boolean peek(char c) {
            return !isEnd() && s.charAt(pos) == c;
        }
    }
}
