/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.Writer;

/**
 * Subclass of {@link JsonWriter} that pretty-prints its output. More specifically, a newline will
 * be added after each value separator (in objects and arrays), as well as at the beginning and end
 * of each object. The contents of objects will also be indented.
 * <p>
 * JSON output should never be pretty-printed to make it human-readable (use 'jq' and other tools if
 * it needs to be read by humans). Only pretty-print JSON output to reduce merge conflicts, so when
 * it is intended to be stored in files that can be checked into repositories (for example
 * reachability metadata).
 */
public final class JsonPrettyWriter extends JsonWriter {
    public JsonPrettyWriter(Writer writer) {
        super(writer);
    }

    @Override
    public JsonWriter appendObjectStart() throws IOException {
        return super.appendObjectStart().indent().newline();
    }

    @Override
    public JsonWriter appendObjectEnd() throws IOException {
        unindent().newline();
        return super.appendObjectEnd();
    }

    @Override
    public JsonWriter appendFieldSeparator() throws IOException {
        return super.appendFieldSeparator().append(' ');
    }

    @Override
    public JsonWriter appendSeparator() throws IOException {
        return super.appendSeparator().newline();
    }
}
