/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.truffle.regex.tregex.util.json;

import java.util.ArrayList;
import java.util.stream.Stream;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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
