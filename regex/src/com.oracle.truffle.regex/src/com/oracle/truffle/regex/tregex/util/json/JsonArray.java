/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.util.json;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Stream;

public class JsonArray extends JsonValue {

    private final ArrayList<JsonConvertible> values;

    JsonArray(ArrayList<JsonConvertible> values) {
        this.values = values;
    }

    JsonArray(JsonConvertible... values) {
        this(new ArrayList<>());
        if (values != null) {
            Collections.addAll(this.values, values);
        }
    }

    JsonArray(Iterable<? extends JsonConvertible> values) {
        this(new ArrayList<>());
        if (values != null) {
            for (JsonConvertible v : values) {
                this.values.add(v);
            }
        }
    }

    JsonArray(Stream<? extends JsonConvertible> values) {
        this(new ArrayList<>());
        if (values != null) {
            values.forEach(this.values::add);
        }
    }

    public JsonArray append(JsonConvertible value) {
        values.add(value);
        return this;
    }

    @Override
    public void dump(PrintWriter writer, int indent) {
        writer.print("[");
        boolean first = true;
        for (JsonConvertible v : values) {
            if (first) {
                first = false;
            } else {
                writer.print(",");
            }
            writer.println();
            printIndent(writer, indent + 2);
            if (v == null) {
                Json.nullValue().dump(writer, indent + 2);
            } else {
                v.toJson().dump(writer, indent + 2);
            }
        }
        if (!first) {
            writer.println();
            printIndent(writer, indent);
        }
        writer.print("]");
    }
}
