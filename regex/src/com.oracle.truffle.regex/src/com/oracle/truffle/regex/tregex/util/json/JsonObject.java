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

public class JsonObject extends JsonValue {

    public static class JsonObjectProperty {

        private final String name;
        private final JsonConvertible value;

        JsonObjectProperty(String name, JsonConvertible value) {
            this.name = name;
            this.value = value;
        }
    }

    private final ArrayList<JsonObjectProperty> properties;

    JsonObject(JsonObjectProperty... props) {
        properties = new ArrayList<>();
        Collections.addAll(properties, props);
    }

    public JsonObject append(JsonObjectProperty... props) {
        Collections.addAll(properties, props);
        return this;
    }

    @Override
    public void dump(PrintWriter writer, int indent) {
        writer.println("{");
        boolean first = true;
        for (JsonObjectProperty p : properties) {
            if (first) {
                first = false;
            } else {
                writer.println(",");
            }
            printIndent(writer, indent + 2);
            writer.print('"');
            writer.print(p.name);
            writer.print("\": ");
            if (p.value == null) {
                Json.nullValue().dump(writer, indent + 2);
            } else {
                p.value.toJson().dump(writer, indent + 2);
            }
        }
        writer.println();
        printIndent(writer, indent);
        writer.print("}");
    }
}
