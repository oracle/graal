/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.util.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;

/**
 * Parses JSON values from a character stream.
 * <p>
 * Example: Given the following JSON file:
 *
 * <pre>{@code
 * // test.json
 * {
 *     "key1": 42,
 *     "key2": [1,2,3],
 * }
 * }</pre>
 * <p>
 * This can be parsed as follows:
 *
 * <pre>{@code
 * JsonParser parser = new JsonParser(new FileReader("test.json"));
 * EconomicMap<String, Object> outer = (EconomicMap<String, Object>) parser.parse();
 * assert outer.get("key1") instanceof Integer;
 * assert (Integer) (outer.get("key1")) == 42;
 * assert outer.get("key2") instanceof List;
 * assert (List) (outer.get("key2")).equals(List.of(1, 2, 3));
 * }</pre>
 * <p>
 * See the main entrypoints: {@link #parse()}, {@link #parseAllowedKeys}.
 */
public final class JsonParser {

    private final Reader source;

    // Current reading position within source
    private int pos = 0;
    // Current line number, used for error reporting.
    private int line = 0;
    // Position of the start of the current line in source, used for error reporting.
    private int beginningOfLine = 0;
    // Next character to be scanned, obtained from source
    private int next;

    private static final int EOF = -1;

    private static final int STATE_EMPTY = 0;
    private static final int STATE_ELEMENT_PARSED = 1;
    private static final int STATE_COMMA_PARSED = 2;

    /**
     * Creates a new {@link JsonParser} to parse the given string.
     *
     * @param source JSON text to be parsed.
     */
    public JsonParser(String source) throws IOException {
        this(new StringReader(source));
    }

    /**
     * Creates a new {@link JsonParser} that reads characters from {@code source}.
     *
     * @param source character stream containing JSON text. Will be adapted internally through a
     *            {@link BufferedReader}.
     */
    public JsonParser(Reader source) throws IOException {
        this.source = new BufferedReader(source);
        next = source.read();
    }

    /**
     * @return retrieves the length of the source (e.g. file) received by the JSON parser.
     */
    public int getSourceLength() {
        try {
            source.reset();
            int length = 0;
            while (next() != -1) {
                length++;
            }
            source.reset();
            source.skip(pos);
            return length;
        } catch (IOException e) {
            throw new RuntimeException("Could not compute size of source", e);
        }
    }

    /**
     * Parses the next value from the underlying reader as a JSON value, which depending on the text
     * could be a literal ({@link Number}, {@link Boolean}, or {@code null}), an object (parsed as
     * an {@link EconomicMap} with {@link String} keys), or an array (parsed as a {@link List}).
     *
     * @return the parsed JSON Object
     */
    public Object parse() throws IOException {
        final Object value = parseLiteral();
        skipWhiteSpace();
        if (next != -1) {
            throw expectedError(pos, "eof", toString(peek()));
        }
        return value;
    }

    /**
     * Parses the next value from the underlying reader as a JSON object using a list of allowed
     * keys. The returned map contains values for the allowed keys only but not necessarily all of
     * them. The method returns as soon as all allowed keys are parsed, i.e., the rest of the JSON
     * may be left unparsed and unchecked. This is useful to parse only few keys from the beginning
     * of a large JSON object.
     *
     * @param allowedKeys the list of allowed keys
     * @return the parsed JSON map containing only values for (not necessarily all) allowed keys
     */
    public EconomicMap<String, Object> parseAllowedKeys(List<String> allowedKeys) throws IOException {
        EconomicMap<String, Object> result = EconomicMap.create();
        if (allowedKeys.isEmpty()) {
            next = -1;
            return result;
        }
        skipWhiteSpace();
        int state = STATE_EMPTY;

        int c = peek();
        if (c == EOF) {
            throw expectedError(pos, "json literal", "eof");
        }
        if (c != '{') {
            throw expectedError(pos, "{", toString(c));
        }
        next();

        while (next != -1) {
            skipWhiteSpace();
            c = peek();

            switch (c) {
                case '"':
                    if (state == STATE_ELEMENT_PARSED) {
                        throw expectedError(pos, ", or }", toString(c));
                    }
                    final String id = parseString();
                    expectColon();
                    final Object value = parseLiteral();
                    if (allowedKeys.contains(id)) {
                        result.put(id, value);
                    }
                    if (result.size() == allowedKeys.size()) {
                        next = -1;
                        return result;
                    }
                    state = STATE_ELEMENT_PARSED;
                    break;
                case ',':
                    if (state != STATE_ELEMENT_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    state = STATE_COMMA_PARSED;
                    next();
                    break;
                case '}':
                    if (state == STATE_COMMA_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    next();
                    return result;
                default:
                    throw expectedError(pos, ", or }", toString(c));
            }
        }
        throw expectedError(pos, ", or }", "eof");
    }

    /**
     * Utility method to parse a character stream containing a JSON object into an
     * {@link EconomicMap} directly.
     */
    @SuppressWarnings("unchecked")
    public static EconomicMap<String, Object> parseDict(Reader input) throws IOException {
        JsonParser parser = new JsonParser(input);
        return (EconomicMap<String, Object>) parser.parse();
    }

    /**
     * Utility method to parse a string containing a JSON object into an {@link EconomicMap}
     * directly.
     */
    @SuppressWarnings("unchecked")
    public static EconomicMap<String, Object> parseDict(String input) throws IOException {
        JsonParser parser = new JsonParser(input);
        return (EconomicMap<String, Object>) parser.parse();
    }

    private Object parseLiteral() throws IOException {
        skipWhiteSpace();

        final int c = peek();
        if (c == EOF) {
            throw expectedError(pos, "json literal", "eof");
        }
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 'f':
                return parseKeyword("false", Boolean.FALSE);
            case 't':
                return parseKeyword("true", Boolean.TRUE);
            case 'n':
                return parseKeyword("null", null);
            default:
                if (isDigit(c) || c == '-') {
                    return parseNumber();
                } else if (c == '.') {
                    throw numberError(pos);
                } else {
                    throw expectedError(pos, "json literal", toString(c));
                }
        }
    }

    private Object parseObject() throws IOException {
        EconomicMap<String, Object> result = EconomicMap.create();
        int state = STATE_EMPTY;

        int p = peek();
        assert p == '{' : "Must be } but was " + p;
        next();

        while (next != -1) {
            skipWhiteSpace();
            final int c = peek();

            switch (c) {
                case '"':
                    if (state == STATE_ELEMENT_PARSED) {
                        throw expectedError(pos, ", or }", toString(c));
                    }
                    final String id = parseString();
                    expectColon();
                    final Object value = parseLiteral();
                    final EconomicMap<String, Object> object = result;
                    object.put(id, value);
                    state = STATE_ELEMENT_PARSED;
                    break;
                case ',':
                    if (state != STATE_ELEMENT_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    state = STATE_COMMA_PARSED;
                    next();
                    break;
                case '}':
                    if (state == STATE_COMMA_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    next();
                    return result;
                default:
                    throw expectedError(pos, ", or }", toString(c));
            }
        }
        throw expectedError(pos, ", or }", "eof");
    }

    private void expectColon() throws IOException {
        skipWhiteSpace();
        final int n = next();
        if (n != ':') {
            throw expectedError(pos - 1, ":", toString(n));
        }
    }

    private Object parseArray() throws IOException {
        List<Object> result = new ArrayList<>();
        int state = STATE_EMPTY;

        int p = peek();
        assert p == '[' : "Must be [ but was " + p;
        next();

        while (next != -1) {
            skipWhiteSpace();
            final int c = peek();

            switch (c) {
                case ',':
                    if (state != STATE_ELEMENT_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    state = STATE_COMMA_PARSED;
                    next();
                    break;
                case ']':
                    if (state == STATE_COMMA_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    next();
                    return result;
                default:
                    if (state == STATE_ELEMENT_PARSED) {
                        throw expectedError(pos, ", or ]", toString(c));
                    }
                    result.add(parseLiteral());
                    state = STATE_ELEMENT_PARSED;
                    break;
            }
        }

        throw expectedError(pos, ", or ]", "eof");
    }

    private String parseString() throws IOException {
        // String buffer is only instantiated if string contains escape sequences.
        next();
        StringBuilder sb = new StringBuilder();

        while (next != -1) {
            final int c = next();
            if (c <= 0x1f) {
                // Characters <= 0x1f are not allowed in JSON strings.
                throw syntaxError(pos, "String contains control character: " + c);
            } else if (c == '\\') {
                sb.append(parseEscapeSequence());
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append((char) c);
            }
        }

        throw error("Missing close quote", pos);
    }

    private char parseEscapeSequence() throws IOException {
        final int c = next();
        return switch (c) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> parseUnicodeEscape();
            default -> throw error("Invalid escape character", pos - 1);
        };
    }

    private char parseUnicodeEscape() throws IOException {
        return (char) (parseHexDigit() << 12 | parseHexDigit() << 8 | parseHexDigit() << 4 | parseHexDigit());
    }

    private int parseHexDigit() throws IOException {
        final int c = next();
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'F') {
            return c + 10 - 'A';
        } else if (c >= 'a' && c <= 'f') {
            return c + 10 - 'a';
        }
        throw error("Invalid hex digit", pos - 1);
    }

    private static boolean isDigit(final int c) {
        return c >= '0' && c <= '9';
    }

    private void skipDigits(StringBuilder sb) throws IOException {
        while (next != -1) {
            final int c = peek();
            if (!isDigit(c)) {
                break;
            }
            next();
            sb.append((char) c);
        }
    }

    private Number parseNumber() throws IOException {
        boolean isFloating = false;
        final int start = pos;
        StringBuilder sb = new StringBuilder();
        int c = next();
        sb.append((char) c);

        if (c == '-') {
            c = next();
            sb.append((char) c);
        }
        if (!isDigit(c)) {
            throw numberError(start);
        }
        // no more digits allowed after 0
        if (c != '0') {
            skipDigits(sb);
        }

        // fraction
        if (peek() == '.') {
            isFloating = true;
            int ch = next();
            sb.append((char) ch);
            ch = next();
            sb.append((char) ch);
            if (!isDigit(ch)) {
                throw numberError(pos - 1);
            }
            skipDigits(sb);
        }

        // exponent
        c = peek();
        if (c == 'e' || c == 'E') {
            next();
            sb.append((char) c);
            c = next();
            sb.append((char) c);
            if (c == '-' || c == '+') {
                c = next();
                sb.append((char) c);
            }
            if (!isDigit(c)) {
                throw numberError(pos - 1);
            }
            skipDigits(sb);
        }

        String literalValue = sb.toString();
        if (isFloating) {
            return Double.parseDouble(literalValue);
        } else {
            final long l = Long.parseLong(literalValue);
            if ((int) l == l) {
                return (int) l;
            } else {
                return l;
            }
        }
    }

    private Object parseKeyword(final String keyword, final Object value) throws IOException {
        if (!read(keyword.length()).equals(keyword)) {
            throw expectedError(pos, "json literal", "ident");
        }
        return value;
    }

    private int peek() {
        return next;
    }

    private int next() throws IOException {
        int cur = next;
        next = source.read();
        pos++;
        return cur;
    }

    private String read(int length) throws IOException {
        char[] buffer = new char[length];

        buffer[0] = (char) peek();
        source.read(buffer, 1, length - 1);
        pos += length - 1;
        next();

        return String.valueOf(buffer);
    }

    private void skipWhiteSpace() throws IOException {
        while (next != -1) {
            switch (peek()) {
                case '\n':
                    line++;
                    beginningOfLine = pos + 1;
                    next();
                    break;
                case '\r':
                    beginningOfLine = pos + 1;
                    next();
                    break;
                case '\t':
                case ' ':
                    next();
                    break;
                default:
                    return;
            }
        }
    }

    private static String toString(final int c) {
        return c == EOF ? "eof" : String.valueOf((char) c);
    }

    private JsonParserException error(final String message, final int position) {
        final int columnNum = position - beginningOfLine;
        final String formatted = format(message, line, columnNum);
        return new JsonParserException(formatted);
    }

    /**
     * Format an error message to include source and line information.
     *
     * @param message Error message string.
     * @param line Source line number.
     * @param column Source column number.
     * @return formatted string
     */
    private static String format(final String message, final int line, final int column) {
        return "line " + line + " column " + column + " " + message;
    }

    private JsonParserException numberError(final int start) {
        return error("Invalid JSON number format", start);
    }

    private JsonParserException expectedError(final int start, final String expected, final String found) {
        return error("Expected " + expected + " but found " + found, start);
    }

    private JsonParserException syntaxError(final int start, final String reason) {
        return error("Invalid JSON: " + reason, start);
    }
}
