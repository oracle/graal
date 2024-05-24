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
import java.util.List;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.debug.GraalError;

/**
 * Contains some utility wrappers around {@link JsonWriter} to simplify converting maps and lists to
 * their JSON representation. Please refer to {@link JsonPrettyWriter} to learn when to and when not
 * to pretty-print JSON output. This class should not be instantiated.
 */
public final class JsonFormatter {
    private JsonFormatter() {
    }

    /**
     * Converts {@code map} to a JSON object.
     *
     * @return a string containing a JSON object representation of the given map.
     * @see JsonWriter#print
     */
    public static <T> String formatJson(EconomicMap<String, T> map) {
        return formatObject(map, false);
    }

    /**
     * Converts {@code list} to a JSON array.
     *
     * @return a string containing a JSON array representation of the given list.
     * @see JsonWriter#print
     */
    public static <T> String formatJson(List<T> list) {
        return formatObject(list, false);
    }

    /**
     * Converts {@code map} to a pretty-printed JSON object.
     *
     * @return a string containing a JSON object representation of the given map.
     * @see JsonWriter#print
     * @see JsonPrettyWriter
     */
    public static <T> String formatJsonPretty(EconomicMap<String, T> map) {
        return formatObject(map, true);
    }

    /**
     * Converts {@code list} to a pretty-printed JSON array.
     *
     * @return a string containing a JSON array representation of the given list.
     * @see JsonWriter#print
     * @see JsonPrettyWriter
     */
    public static <T> String formatJsonPretty(List<T> list) {
        return formatObject(list, true);
    }

    private static String formatObject(Object object, boolean prettyPrint) {
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = prettyPrint ? new JsonPrettyWriter(stringWriter) : new JsonWriter(stringWriter)) {
            jsonWriter.print(object);
        } catch (IOException e) {
            throw GraalError.shouldNotReachHere(e);
        }
        return stringWriter.toString();
    }
}
