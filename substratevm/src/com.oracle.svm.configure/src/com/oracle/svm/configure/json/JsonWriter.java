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

public class JsonWriter implements AutoCloseable {
    private static final String WHITESPACE = "                                ";

    private final String lineSep = System.lineSeparator();
    private final Writer writer;

    private int indentation = 0;

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

    public JsonWriter newline() throws IOException {
        writer.write(lineSep);
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

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
