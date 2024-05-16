/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.debug.GraalError;

public class JSONFormatter {
    public static <T> String formatJSON(EconomicMap<String, T> map) {
        return formatJSON(map, false);
    }

    public static <T> String formatJSON(EconomicMap<String, T> map, boolean prettyPrint) {
        StringWriter sw = new StringWriter();
        printJSON(map, sw, prettyPrint);
        return sw.toString();
    }

    public static <T> void printJSON(EconomicMap<String, T> map, Writer writer) {
        printJSON(map, writer, false);
    }

    public static <T> void printJSON(EconomicMap<String, T> map, Writer writer, boolean prettyPrint) {
        JsonWriter jw = prettyPrint ? new JsonPrettyWriter(writer) : new JsonWriter(writer);
        try (JsonBuilder.ObjectBuilder builder = JsonBuilder.object(jw)) {
            var cursor = map.getEntries();
            while (cursor.advance()) {
                builder.append(cursor.getKey(), cursor.getValue());
            }
        } catch (IOException e) {
            GraalError.shouldNotReachHere(e, "StringWriter threw IOException");
        }
    }

    public static <T> String formatJSON(Collection<T> collection) {
        return formatJSON(collection, false);
    }

    public static <T> String formatJSON(Collection<T> collection, boolean prettyPrint) {
        StringWriter sw = new StringWriter();
        printJSON(collection, sw, prettyPrint);
        return sw.toString();
    }

    public static <T> void printJSON(Collection<T> collection, Writer writer) {
        printJSON(collection, writer, false);
    }

    public static <T> void printJSON(Collection<T> collection, Writer writer, boolean prettyPrint) {
        JsonWriter jw = prettyPrint ? new JsonPrettyWriter(writer) : new JsonWriter(writer);
        try (JsonBuilder.ArrayBuilder builder = JsonBuilder.array(jw)) {
            for (T item : collection) {
                builder.append(item);
            }
        } catch (IOException e) {
            GraalError.shouldNotReachHere(e, "StringWriter threw IOException");
        }
    }
}
