package pt.ipt.technochained.model;

import java.util.*;

/**
 * Serializador e parser JSON (RFC 8259) em Java puro sem dependências externas.
 * Permite gerar um executável .jar único em projetos Java with Ant.
 */
public final class JsonUtils {

    private JsonUtils() {}

    public static String toPrettyJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        writeObject(sb, map, 0);
        return sb.toString();
    }

    private static void writeObject(StringBuilder sb, Map<String, Object> map, int indent) {
        sb.append("{\n");
        var it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            appendIndent(sb, indent + 2);
            writeString(sb, entry.getKey());
            sb.append(": ");
            writeValue(sb, entry.getValue(), indent + 2);
            if (it.hasNext()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        appendIndent(sb, indent);
        sb.append("}");
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                appendIndent(sb, indent + 2);
                writeValue(sb, list.get(i), indent + 2);
                if (i < list.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            appendIndent(sb, indent);
            sb.append("]");
        } else if (value instanceof Map<?, ?> nested) {
            writeObject(sb, (Map<String, Object>) nested, indent);
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
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

    private static void appendIndent(StringBuilder sb, int spaces) {
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
    }

    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Conteúdo JSON vazio.");
        }
        Parser parser = new Parser(json);
        Map<String, Object> result = parser.readObject();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("Caracteres inesperados após o fim do JSON.");
        }
        return result;
    }

    private static final class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        boolean isAtEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            if (isAtEnd()) {
                throw new IllegalArgumentException("Fim inesperado do ficheiro JSON.");
            }
            return src.charAt(pos);
        }

        void expect(char expected) {
            skipWhitespace();
            char actual = peek();
            if (actual != expected) {
                throw new IllegalArgumentException(
                        "JSON inválido na posição " + pos + ": esperado '" + expected + "', encontrado '" + actual + "'.");
            }
            pos++;
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                expect(':');
                Object val = readValue();
                map.put(key, val);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return map;
                } else {
                    throw new IllegalArgumentException("Esperado ',' ou '}' na posição " + pos);
                }
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw new IllegalArgumentException("Esperado ',' ou ']' na posição " + pos);
                }
            }
        }

        Object readValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> {
                    if (c == '-' || Character.isDigit(c)) {
                        yield readNumber();
                    }
                    throw new IllegalArgumentException("Valor JSON inesperado '" + c + "' na posição " + pos);
                }
            };
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (isAtEnd()) {
                        throw new IllegalArgumentException("Sequência de escape incompleta.");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("Escape unicode inválido.");
                            }
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Escape inválido: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("String JSON não terminada.");
        }

        Number readNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            while (!isAtEnd() && (Character.isDigit(src.charAt(pos))
                    || src.charAt(pos) == '.'
                    || src.charAt(pos) == 'e' || src.charAt(pos) == 'E'
                    || src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                if ((src.charAt(pos) == '+' || src.charAt(pos) == '-') && pos > start
                        && src.charAt(pos - 1) != 'e' && src.charAt(pos - 1) != 'E') {
                    break;
                }
                pos++;
            }
            String token = src.substring(start, pos);
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return Double.parseDouble(token);
            }
            return Long.parseLong(token);
        }

        Object readLiteral(String literal, Object value) {
            if (!src.startsWith(literal, pos)) {
                throw new IllegalArgumentException("Esperado '" + literal + "' na posição " + pos);
            }
            pos += literal.length();
            return value;
        }
    }
}
