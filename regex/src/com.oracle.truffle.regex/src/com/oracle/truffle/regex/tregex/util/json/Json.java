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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.util.ArrayList;
import java.util.stream.Stream;

public final class Json {

    @TruffleBoundary
    public static JsonBool val(boolean val) {
        return new JsonBool(val);
    }

    @TruffleBoundary
    public static JsonInt val(int val) {
        return new JsonInt(val);
    }

    @TruffleBoundary
    public static JsonInt val(long val) {
        return new JsonInt(val);
    }

    @TruffleBoundary
    public static JsonString val(String val) {
        return new JsonString(val);
    }

    @TruffleBoundary
    public static JsonNull nullValue() {
        return JsonNull.INSTANCE;
    }

    @TruffleBoundary
    public static JsonValue array(char[] array) {
        ArrayList<JsonConvertible> list = new ArrayList<>(array.length);
        for (char c : array) {
            list.add(val(String.valueOf(c)));
        }
        return new JsonArray(list);
    }

    @TruffleBoundary
    public static JsonValue array(short[] array) {
        ArrayList<JsonConvertible> list = new ArrayList<>(array.length);
        for (int i : array) {
            list.add(val(i));
        }
        return new JsonArray(list);
    }

    @TruffleBoundary
    public static JsonValue array(int[] array) {
        ArrayList<JsonConvertible> list = new ArrayList<>(array.length);
        for (int i : array) {
            list.add(val(i));
        }
        return new JsonArray(list);
    }

    @TruffleBoundary
    public static JsonArray array(JsonConvertible... values) {
        return new JsonArray(values);
    }

    @TruffleBoundary
    public static JsonArray array(Iterable<? extends JsonConvertible> values) {
        return new JsonArray(values);
    }

    @TruffleBoundary
    public static JsonArray array(Stream<? extends JsonConvertible> values) {
        return new JsonArray(values);
    }

    @TruffleBoundary
    public static JsonArray arrayUnsigned(byte[] array) {
        if (array == null) {
            return new JsonArray();
        }
        ArrayList<JsonConvertible> list = new ArrayList<>(array.length);
        for (byte b : array) {
            list.add(val(Byte.toUnsignedInt(b)));
        }
        return new JsonArray(list);
    }

    @TruffleBoundary
    public static JsonObject obj(JsonObject.JsonObjectProperty... properties) {
        return new JsonObject(properties);
    }

    @TruffleBoundary
    public static JsonObject.JsonObjectProperty prop(String name, boolean value) {
        return new JsonObject.JsonObjectProperty(name, val(value));
    }

    @TruffleBoundary
    public static JsonObject.JsonObjectProperty prop(String name, int value) {
        return new JsonObject.JsonObjectProperty(name, val(value));
    }

    @TruffleBoundary
    public static JsonObject.JsonObjectProperty prop(String name, long value) {
        return new JsonObject.JsonObjectProperty(name, val(value));
    }

    @TruffleBoundary
    public static JsonObject.JsonObjectProperty prop(String name, String value) {
        return new JsonObject.JsonObjectProperty(name, val(value));
    }

    @TruffleBoundary
    public static JsonObject.JsonObjectProperty prop(String name, JsonConvertible value) {
        return new JsonObject.JsonObjectProperty(name, value);
    }

    @TruffleBoundary
    public static JsonObject.JsonObjectProperty prop(String name, Iterable<? extends JsonConvertible> value) {
        return new JsonObject.JsonObjectProperty(name, array(value));
    }

    @TruffleBoundary
    public static JsonObject.JsonObjectProperty prop(String name, Stream<? extends JsonConvertible> value) {
        return new JsonObject.JsonObjectProperty(name, array(value));
    }
}
