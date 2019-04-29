/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.json;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public class JsonWriter implements AutoCloseable {
    private static final String WHITESPACE = "                                ";

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

    public JsonWriter quote(Object o) throws IOException {
        if (o == null) {
            return append("null");
        } else if (Boolean.TRUE.equals(o)) {
            return append("true");
        } else if (Boolean.FALSE.equals(o)) {
            return append("false");
        }
        return quote(o.toString());
    }

    public JsonWriter quote(String s) throws IOException {
        if (s == null) {
            return append("null");
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
        writer.write(sb.toString());
        return this;
    }

    public JsonWriter newline() throws IOException {
        writer.write('\n');
        writer.write(WHITESPACE, 0, Math.min(2 * indentation, WHITESPACE.length()));
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
