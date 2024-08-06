/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver.launcher.json;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses JSON values from a string. This class is mostly a reimplementation of
 * {@code jdk.graal.compiler.util.json.JsonParser}. That class can't be used directly as we can't
 * rely on {@code jdk.graal.compiler} being available when executing a bundle on a vanilla JDK.
 */
public class BundleJSONParser {

    private final String source;
    private final int length;
    private int pos = 0;

    private static final int EOF = -1;

    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NULL = "null";

    private static final int STATE_EMPTY = 0;
    private static final int STATE_ELEMENT_PARSED = 1;
    private static final int STATE_COMMA_PARSED = 2;

    public BundleJSONParser(String source) {
        this.source = source;
        this.length = source.length();
    }

    public BundleJSONParser(Reader source) throws IOException {
        this(readFully(source));
    }

    /**
     * Public parse method. Parse a string into a JSON object.
     *
     * @return the parsed JSON Object
     */
    public Object parse() {
        final Object value = parseLiteral();
        skipWhiteSpace();
        if (pos < length) {
            throw expectedError(pos, "eof", toString(peek()));
        }
        return value;
    }

    private Object parseLiteral() {
        skipWhiteSpace();

        final int c = peek();
        if (c == EOF) {
            throw expectedError(pos, "json literal", "eof");
        }
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 'f' -> parseKeyword(FALSE, Boolean.FALSE);
            case 't' -> parseKeyword(TRUE, Boolean.TRUE);
            case 'n' -> parseKeyword(NULL, null);
            default -> {
                if (isDigit(c) || c == '-') {
                    yield parseNumber();
                } else if (c == '.') {
                    throw numberError(pos);
                } else {
                    throw expectedError(pos, "json literal", toString(c));
                }
            }
        };
    }

    private Object parseObject() {
        Map<String, Object> result = new HashMap<>();
        int state = STATE_EMPTY;

        assert peek() == '{';
        pos++;

        while (pos < length) {
            skipWhiteSpace();
            final int c = peek();

            switch (c) {
                case '"' -> {
                    if (state == STATE_ELEMENT_PARSED) {
                        throw expectedError(pos, ", or }", toString(c));
                    }
                    final String id = parseString();
                    expectColon();
                    final Object value = parseLiteral();
                    result.put(id, value);
                    state = STATE_ELEMENT_PARSED;
                }
                case ',' -> {
                    if (state != STATE_ELEMENT_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    state = STATE_COMMA_PARSED;
                    pos++;
                }
                case '}' -> {
                    if (state == STATE_COMMA_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    pos++;
                    return result;
                }
                default -> throw expectedError(pos, ", or }", toString(c));
            }
        }
        throw expectedError(pos, ", or }", "eof");
    }

    private void expectColon() {
        skipWhiteSpace();
        final int n = next();
        if (n != ':') {
            throw expectedError(pos - 1, ":", toString(n));
        }
    }

    private Object parseArray() {
        List<Object> result = new ArrayList<>();
        int state = STATE_EMPTY;

        assert peek() == '[';
        pos++;

        while (pos < length) {
            skipWhiteSpace();
            final int c = peek();

            switch (c) {
                case ',' -> {
                    if (state != STATE_ELEMENT_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    state = STATE_COMMA_PARSED;
                    pos++;
                }
                case ']' -> {
                    if (state == STATE_COMMA_PARSED) {
                        throw error("Trailing comma is not allowed in JSON", pos);
                    }
                    pos++;
                    return result;
                }
                default -> {
                    if (state == STATE_ELEMENT_PARSED) {
                        throw expectedError(pos, ", or ]", toString(c));
                    }
                    result.add(parseLiteral());
                    state = STATE_ELEMENT_PARSED;
                }
            }
        }

        throw expectedError(pos, ", or ]", "eof");
    }

    private String parseString() {
        // String buffer is only instantiated if string contains escape sequences.
        int start = ++pos;
        StringBuilder sb = null;

        while (pos < length) {
            final int c = next();
            if (c <= 0x1f) {
                // Characters < 0x1f are not allowed in JSON strings.
                throw syntaxError(pos, "String contains control character");

            } else if (c == '\\') {
                if (sb == null) {
                    sb = new StringBuilder(pos - start + 16);
                }
                sb.append(source, start, pos - 1);
                sb.append(parseEscapeSequence());
                start = pos;

            } else if (c == '"') {
                if (sb != null) {
                    sb.append(source, start, pos - 1);
                    return sb.toString();
                }
                return source.substring(start, pos - 1);
            }
        }

        throw error("Missing close quote", pos);
    }

    private char parseEscapeSequence() {
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

    private char parseUnicodeEscape() {
        return (char) (parseHexDigit() << 12 | parseHexDigit() << 8 | parseHexDigit() << 4 | parseHexDigit());
    }

    private int parseHexDigit() {
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

    private void skipDigits() {
        while (pos < length) {
            final int c = peek();
            if (!isDigit(c)) {
                break;
            }
            pos++;
        }
    }

    private Number parseNumber() {
        boolean isFloating = false;
        final int start = pos;
        int c = next();

        if (c == '-') {
            c = next();
        }
        if (!isDigit(c)) {
            throw numberError(start);
        }
        // no more digits allowed after 0
        if (c != '0') {
            skipDigits();
        }

        // fraction
        if (peek() == '.') {
            isFloating = true;
            pos++;
            if (!isDigit(next())) {
                throw numberError(pos - 1);
            }
            skipDigits();
        }

        // exponent
        c = peek();
        if (c == 'e' || c == 'E') {
            pos++;
            c = next();
            if (c == '-' || c == '+') {
                c = next();
            }
            if (!isDigit(c)) {
                throw numberError(pos - 1);
            }
            skipDigits();
        }

        String literalValue = source.substring(start, pos);
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

    private Object parseKeyword(final String keyword, final Object value) {
        if (!source.regionMatches(pos, keyword, 0, keyword.length())) {
            throw expectedError(pos, "json literal", "ident");
        }
        pos += keyword.length();
        return value;
    }

    private int peek() {
        if (pos >= length) {
            return -1;
        }
        return source.charAt(pos);
    }

    private int next() {
        final int next = peek();
        pos++;
        return next;
    }

    private void skipWhiteSpace() {
        while (pos < length) {
            switch (peek()) {
                case '\t', '\r', '\n', ' ' -> pos++;
                default -> {
                    return;
                }
            }
        }
    }

    private static String toString(final int c) {
        return c == EOF ? "eof" : String.valueOf((char) c);
    }

    private BundleJSONParserException error(final String message, final int start) {
        final int lineNum = getLine(start);
        final int columnNum = getColumn(start);
        final String formatted = format(message, lineNum, columnNum);
        return new BundleJSONParserException(formatted);
    }

    /**
     * Return line number of character position.
     *
     * <p>
     * This method can be expensive for large sources as it iterates through all characters up to
     * {@code position}.
     * </p>
     *
     * @param position Position of character in source content.
     * @return Line number.
     */
    private int getLine(final int position) {
        final CharSequence d = source;
        // Line count starts at 1.
        int line = 1;

        for (int i = 0; i < position; i++) {
            final char ch = d.charAt(i);
            // Works for both \n and \r\n.
            if (ch == '\n') {
                line++;
            }
        }

        return line;
    }

    /**
     * Return column number of character position.
     *
     * @param position Position of character in source content.
     * @return Column number.
     */
    private int getColumn(final int position) {
        return position - findBOLN(position);
    }

    /**
     * Find the beginning of the line containing position.
     *
     * @param position Index to offending token.
     * @return Index of first character of line.
     */
    private int findBOLN(final int position) {
        final CharSequence d = source;
        for (int i = position - 1; i > 0; i--) {
            final char ch = d.charAt(i);

            if (ch == '\n' || ch == '\r') {
                return i + 1;
            }
        }

        return 0;
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

    private BundleJSONParserException numberError(final int start) {
        return error("Invalid JSON number format", start);
    }

    private BundleJSONParserException expectedError(final int start, final String expected, final String found) {
        return error("Expected " + expected + " but found " + found, start);
    }

    private BundleJSONParserException syntaxError(final int start, final String reason) {
        return error("Invalid JSON: " + reason, start);
    }

    /**
     * Utility function to read all contents of a {@link Reader}, because the JSON parser does not
     * support streaming yet.
     */
    private static String readFully(final Reader reader) throws IOException {
        final char[] arr = new char[1024];
        final StringBuilder sb = new StringBuilder();

        try (reader) {
            int numChars;
            while ((numChars = reader.read(arr, 0, arr.length)) > 0) {
                sb.append(arr, 0, numChars);
            }
        }

        return sb.toString();
    }
}
