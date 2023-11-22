/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util.json;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JsonWriter implements AutoCloseable {
    private final Writer writer;

    private int indentation = 0;

    public JsonWriter(Path path, OpenOption... options) throws IOException {
        this(Files.newBufferedWriter(path, StandardCharsets.UTF_8, options));
    }

    public JsonWriter(Writer writer) {
        this.writer = writer;
    }

    public JsonWriter append(char c) throws IOException {
        writer.write(c);
        return this;
    }

    public JsonWriter append(String s) throws IOException {
        writer.write(s);
        return this;
    }

    public JsonWriter appendObjectStart() throws IOException {
        return append('{');
    }

    public JsonWriter appendObjectEnd() throws IOException {
        return append('}');
    }

    public JsonWriter appendArrayStart() throws IOException {
        return append('[');
    }

    public JsonWriter appendArrayEnd() throws IOException {
        return append(']');
    }

    public JsonWriter appendSeparator() throws IOException {
        return append(',');
    }

    public JsonWriter appendFieldSeparator() throws IOException {
        return append(':');
    }

    public JsonWriter appendKeyValue(String key, Object value) throws IOException {
        return quote(key).appendFieldSeparator().printValue(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public JsonWriter print(Object value) throws IOException {
        if (value instanceof Map map) {
            printMap(map); // Must always be <String, Object>
        } else if (value instanceof Iterator it) {
            printIterator(it);
        } else if (value instanceof List list) {
            printIterator(list.iterator());
        } else {
            printValue(value);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    private void printMap(Map<String, Object> map) throws IOException {
        if (map.isEmpty()) {
            append("{}");
            return;
        }
        append('{');
        Iterator<String> keySetIter = map.keySet().iterator();
        while (keySetIter.hasNext()) {
            String key = keySetIter.next();
            Object value = map.get(key);
            quote(key).append(':');
            print(value);
            if (keySetIter.hasNext()) {
                append(',');
            }
        }
        append('}');
    }

    private void printIterator(Iterator<?> iter) throws IOException {
        append('[');
        if (iter.hasNext()) {
            print(iter.next());
            while (iter.hasNext()) {
                append(',');
                print(iter.next());
            }
        }
        append(']');
    }

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

    public JsonWriter quote(String s) throws IOException {
        writer.write(quoteString(s));
        return this;
    }

    public static String quoteString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(2 + s.length() + 8 /* room for escaping */);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
                sb.append(c);
            } else if (c < 0x001F) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public JsonWriter newline() throws IOException {
        StringBuilder builder = new StringBuilder(1 + 2 * indentation);
        builder.append("\n");
        for (int i = 0; i < indentation; ++i) {
            builder.append("  ");
        }
        writer.write(builder.toString());
        return this;
    }

    public JsonWriter indent() {
        indentation++;
        return this;
    }

    public JsonWriter unindent() {
        assert indentation > 0;
        indentation--;
        return this;
    }

    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
