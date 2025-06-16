/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;

/**
 * A wrapper around {@link Writer} for writing JSON tokens to a character stream. Example:
 *
 * <pre>{@code
 * try (JsonWriter writer = new JsonWriter(Path.of("/some/file.json"))) {
 *     writer.appendObjectStart().quote("key").appendFieldSeparator().appendArrayStart().printValue(1).appendSeparator().printValue(2).appendArrayEnd().appendObjectEnd();
 * }
 * }</pre>
 * <p>
 * Produces (pretty-printed for readability, though actual output is unformatted):
 *
 * <pre>{@code
 * {
 *  "key": [1,2]
 * }
 * }</pre>
 * <p>
 * For a higher-level declarative interface for building JSON objects, see {@link JsonBuilder} and
 * {@link JsonBuilder#object(JsonWriter)}.
 *
 * @see JsonPrettyWriter
 */
public class JsonWriter implements AutoCloseable {
    private final Writer writer;

    private int indentation = 0;

    /**
     * Utility constructor that opens a {@link BufferedWriter} to the file at {@code path}, using
     * UTF-8 encoding and the given options.
     *
     * @see Files#newBufferedWriter
     */
    public JsonWriter(Path path, OpenOption... options) throws IOException {
        this(Files.newBufferedWriter(path, StandardCharsets.UTF_8, options));
    }

    /**
     * Creates a new {@link JsonWriter} wrapping the given {@link Writer}. Note that characters are
     * written to the underlying writer directly, so it is advisable to pass a
     * {@link BufferedWriter} to the constructor if buffering is required.
     */
    public JsonWriter(Writer writer) {
        this.writer = writer;
    }

    /**
     * See {@link JsonBuilder} and {@link JsonBuilder#object(JsonWriter)}.
     * <p>
     * While the builder is active, this class must not be used directly for printing, it may
     * otherwise produce invalid JSON.
     */
    public JsonBuilder.ObjectBuilder objectBuilder() throws IOException {
        return JsonBuilder.object(this);
    }

    /**
     * See {@link JsonBuilder} and {@link JsonBuilder#array(JsonWriter)}.
     * <p>
     * While the builder is active, this class must not be used directly for printing, it may
     * otherwise produce invalid JSON.
     */
    public JsonBuilder.ArrayBuilder arrayBuilder() throws IOException {
        return JsonBuilder.array(this);
    }

    /**
     * See {@link JsonBuilder} and {@link JsonBuilder#value(JsonWriter)}.
     * <p>
     * While the builder is active, this class must not be used directly for printing, it may
     * otherwise produce invalid JSON.
     */
    public JsonBuilder.ValueBuilder valueBuilder() {
        return JsonBuilder.value(this);
    }

    /**
     * Appends a single character to the underlying writer.
     */
    public JsonWriter append(char c) throws IOException {
        writer.write(c);
        return this;
    }

    /**
     * Appends a raw unquoted string to the underlying writer.
     */
    public JsonWriter append(String s) throws IOException {
        writer.write(s);
        return this;
    }

    /**
     * Appends an object-begin token (i.e. an open curly bracket) to the underlying writer.
     */
    public JsonWriter appendObjectStart() throws IOException {
        return append('{');
    }

    /**
     * Appends an object-end token (i.e. a closed curly bracket) to the underlying writer.
     */
    public JsonWriter appendObjectEnd() throws IOException {
        return append('}');
    }

    /**
     * Appends an array-start token (i.e. an open square bracket) to the underlying writer.
     */
    public JsonWriter appendArrayStart() throws IOException {
        return append('[');
    }

    /**
     * Appends an array-end token (i.e. a closed square bracket) to the underlying writer.
     */
    public JsonWriter appendArrayEnd() throws IOException {
        return append(']');
    }

    /**
     * Appends a value-separator token (i.e. a comma) to the underlying writer.
     */
    public JsonWriter appendSeparator() throws IOException {
        return append(',');
    }

    /**
     * Appends a name-separator token (i.e. a colon) to the underlying writer.
     */
    public JsonWriter appendFieldSeparator() throws IOException {
        return append(':');
    }

    /**
     * Appends a key-value pair of the form {@code "key": value} to the underlying writer. The key
     * will be {@linkplain #quote(String) quoted}, and the value will be printed as per
     * {@link #printValue(Object)}.
     */
    public JsonWriter appendKeyValue(String key, Object value) throws IOException {
        return quote(key).appendFieldSeparator().printValue(value);
    }

    /**
     * Appends a JSON representation of {@code value} to the underlying writer. The chosen
     * representation depends on the object's type:
     * <ul>
     * <li>{@link Map} and {@link EconomicMap} are printed as JSON objects. The keys are converted
     * to strings and {@linkplain #quote quoted}, and values are themselves printed recursively.
     * </li>
     * <li>{@link Iterator} and {@link List} are printed as JSON arrays, where each element appears
     * in iteration order and is itself printed recursively.</li>
     * <li>{@link Number}s and {@link Boolean}s are converted to strings and appended directly to
     * the writer as raw values, without quoting. Floating-point infinity and NaNs cannot be
     * represented as raw values and are thus quoted.</li>
     * <li>{@code null} is translated to a raw {@code null} value.</li>
     * <li>Every other type is converted to a {@linkplain #quote quoted} string.</li>
     * </ul>
     */
    public JsonWriter print(Object value) throws IOException {
        if (value instanceof Map<?, ?> map) {
            printMap(map);
        } else if (value instanceof EconomicMap<?, ?> economicMap) {
            printMap(economicMap); // Must always be <String, Object>
        } else if (value instanceof Iterator<?> it) {
            printIterator(it);
        } else if (value instanceof List<?> list) {
            printIterator(list.iterator());
        } else {
            printValue(value);
        }
        return this;
    }

    private void printMap(Map<?, ?> map) throws IOException {
        if (map.isEmpty()) {
            append("{}");
            return;
        }
        appendObjectStart();
        boolean separator = false;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (separator) {
                appendSeparator();
            }
            quote(entry.getKey().toString()).appendFieldSeparator();
            print(entry.getValue());
            separator = true;
        }
        appendObjectEnd();
    }

    private void printMap(EconomicMap<?, ?> map) throws IOException {
        if (map.isEmpty()) {
            append("{}");
            return;
        }
        appendObjectStart();
        boolean separator = false;
        var cursor = map.getEntries();
        while (cursor.advance()) {
            if (separator) {
                appendSeparator();
            }
            quote(cursor.getKey().toString()).appendFieldSeparator();
            print(cursor.getValue());
            separator = true;
        }
        appendObjectEnd();
    }

    private void printIterator(Iterator<?> iter) throws IOException {
        if (!iter.hasNext()) {
            append("[]");
            return;
        }
        appendArrayStart();
        boolean separator = false;
        while (iter.hasNext()) {
            Object item = iter.next();
            if (separator) {
                appendSeparator();
            }
            print(item);
            separator = true;
        }
        appendArrayEnd();
    }

    /**
     * Appends a representation of {@code o} as a raw JSON value (i.e. not an object or an array) to
     * the underlying writer.
     * <ul>
     * <li>{@link Number}s and {@link Boolean}s are converted to strings and appended directly to
     * the writer as raw values, without quoting. Floating-point infinity and NaNs cannot be
     * represented as raw values and are thus quoted.</li>
     * <li>{@code null} is translated to a raw {@code null} value.</li>
     * <li>Every other type is converted to a {@linkplain #quote quoted} string.</li>
     * </ul>
     */
    public JsonWriter printValue(Object o) throws IOException {
        if (o == null) {
            return append("null");
        } else if (o instanceof Boolean || o instanceof Byte || o instanceof Short || o instanceof Integer || o instanceof Long) {
            /*
             * Note that sub-integer values here most likely become Integer objects when parsing,
             * and comparisons such as equals() or compareTo() on boxed values only work on the
             * exact same type. (Boolean values, however, should be deserialized as Boolean).
             */
            return append(o.toString());
        } else if (o instanceof Float f) {
            if (f.isNaN() || f.isInfinite()) {
                return quote(f.toString()); // cannot express, best we can do without failing
            }
            return append(f.toString());
        } else if (o instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                return quote(d.toString()); // cannot express, best we can do without failing
            }
            return append(d.toString());
        } else {
            return quote(o.toString());
        }
    }

    /**
     * Appends a JSON string to the underlying writer. The resulting string will be surrounded by
     * double-quotes and have characters replaced by escape sequences as necessary. Refer to <a
     * href=https://www.ietf.org/rfc/rfc4627.txt>the JSON RFC</a> for character escaping rules.
     */
    public JsonWriter quote(String s) throws IOException {
        writer.write(quoteString(s));
        return this;
    }

    private static void escapeSequence(StringBuilder sb, char c) {
        sb.append('\\');
        sb.append(c);
    }

    private static String quoteString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(2 + s.length() + 8 /* room for escaping */);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Escaping rules according to https://www.ietf.org/rfc/rfc4627.txt section 2.5
            // except for '/' because it is unnecessarily verbose.
            if (c == '"' || c == '\\') {
                escapeSequence(sb, c);
            } else if (c == '\b') {
                escapeSequence(sb, 'b');
            } else if (c == '\f') {
                escapeSequence(sb, 'f');
            } else if (c == '\n') {
                escapeSequence(sb, 'n');
            } else if (c == '\r') {
                escapeSequence(sb, 'r');
            } else if (c == '\t') {
                escapeSequence(sb, 't');
            } else if (c <= 0x1f) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Appends a new line to the underlying writer, followed by whitespace according to the current
     * indentation level. Each indent will be two spaces wide.
     *
     * @see #indent()
     * @see #unindent()
     */
    public JsonWriter newline() throws IOException {
        writer.write('\n');
        appendIndentation();
        return this;
    }

    /**
     * Appends <code>2 * indentation</code> whitespaces. This call is used to print objects that
     * start indented.
     *
     * @see #indent()
     * @see #unindent()
     */
    public JsonWriter appendIndentation() throws IOException {
        writer.write("  ".repeat(indentation));
        return this;
    }

    /**
     * Increases the current indentation level by one. This does not print any character to the
     * writer directly, but modifies how many indents will be printed at the next call to
     * {@link #newline()} or {@link #appendIndentation()}.
     */
    public JsonWriter indent() {
        indentation++;
        return this;
    }

    /**
     * Decreases the current indentation level by one. This does not print any character to the
     * writer directly, but modifies how many indents will be printed at the next call to
     * {@link #newline()} or {@link #appendIndentation()}.
     */
    public JsonWriter unindent() {
        assert indentation > 0 : "Json indentation underflowed";
        indentation--;
        return this;
    }

    /**
     * Flushes the underlying writer.
     *
     * @see Writer#flush()
     */
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
