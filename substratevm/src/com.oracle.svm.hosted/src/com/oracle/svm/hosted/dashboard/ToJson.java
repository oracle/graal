/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.dashboard;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class ToJson implements AutoCloseable {

    private static final String EMPTY = "";
    private static final String NEW_LINE = "\n";
    private static final char TAB = '\t';

    private static final String OPEN_OBJECT = "{";
    private static final String CLOSE_OBJECT = "}";

    private static final String OPEN_ARRAY = "[";
    private static final String CLOSE_ARRAY = "]";

    private static final char QUOTE = '\"';
    private static final char SLASH = '\\';

    private static final BitSet ESC;
    private static final Map<Character, Character> MAP;

    static {
        ESC = Arrays.asList('\\', '\"', '\b', '\f', '\n', '\r', '\t', '/').stream().collect(BitSet::new, BitSet::set, BitSet::or);
        MAP = new HashMap<>();
        MAP.put('\b', 'b');
        MAP.put('\f', 'f');
        MAP.put('\n', 'n');
        MAP.put('\r', 'r');
        MAP.put('\t', 't');
    }

    private final PrintWriter writer;
    private final boolean pretty;
    private final String newPrefix;
    private final String prefi;
    private final String colon;

    private int depth = 0;
    private String pref;

    private final List<String> prepends = new ArrayList<String>() {
        private static final long serialVersionUID = 1L;

        @Override
        public String get(int index) {
            while (size() <= index) {
                add(getFill(size()));
            }
            return super.get(index);
        }

        private String getFill(int size) {
            return size == 0 ? EMPTY : (super.get(size - 1) + TAB);
        }
    };

    public ToJson(PrintWriter writer) {
        this(writer, false);
    }

    public ToJson(PrintWriter writer, boolean pretty) {
        this.writer = writer;
        this.pretty = pretty;
        if (pretty) {
            prefi = ",\n";
            newPrefix = NEW_LINE;
            colon = ": ";
        } else {
            prefi = ",";
            newPrefix = EMPTY;
            colon = ":";
        }
        pref = newPrefix;
        writer.append(OPEN_OBJECT);
        ++depth;
    }

    private void prepend() {
        if (pretty) {
            writer.append(prepends.get(depth));
        }
    }

    public void put(String name, JsonValue value) {
        if (value != null) {
            writer.print(pref);
            putProperty(name, value);
            pref = prefi;
        }
    }

    private void putProperty(String name, JsonValue value) {
        prepend();
        dumpString(name);
        writer.append(colon);
        value.dump(this);
    }

    private void dumpString(String string) {
        writer.append(QUOTE);
        for (int i = 0, n = string.length(); i < n; ++i) {
            char c = string.charAt(i);
            if (ESC.get(c)) {
                writer.append(SLASH);
                writer.append(MAP.getOrDefault(c, c));
            } else {
                writer.append(c);
            }
        }
        writer.append(QUOTE);
    }

    private void dumpElement(String element) {
        writer.print(element);
    }

    private void dumpNumber(Number number) {
        writer.print(number);
    }

    private void dumpArray(Stream<JsonValue> values) {
        writer.append(OPEN_ARRAY);
        ++depth;
        String[] prefix = new String[]{newPrefix};
        values.sequential().forEach(val -> {
            if (val != null) {
                writer.print(prefix[0]);
                prepend();
                val.dump(this);
                prefix[0] = this.prefi;
            }
        });
        --depth;
        if (pretty && !prefix[0].equals(NEW_LINE)) {
            writer.append(NEW_LINE);
            prepend();
        }
        writer.append(CLOSE_ARRAY);
    }

    private void dumpObject(Stream<String> names, Function<String, JsonValue> func) {
        writer.append(OPEN_OBJECT);
        ++depth;
        String[] prefix = new String[]{newPrefix};
        names.sequential().forEach(name -> {
            JsonValue val = func.apply(name);
            if (val != null) {
                writer.print(prefix[0]);
                putProperty(name, val);
                prefix[0] = this.prefi;
            }
        });
        --depth;
        if (pretty && !prefix[0].equals(NEW_LINE)) {
            writer.append(NEW_LINE);
            prepend();
        }
        writer.append(CLOSE_OBJECT);
    }

    @Override
    public void close() {
        --depth;
        assert depth == 0;
        writer.append(CLOSE_OBJECT);
        writer.close();
    }

    public abstract static class JsonValue {

        public static final JsonValue NULL = JsonElement.get("null");
        public static final JsonValue TRUE = JsonElement.get("true");
        public static final JsonValue FALSE = JsonElement.get("false");

        abstract void dump(ToJson access);

        protected void build() {
        }
    }

    private abstract static class JsonElement extends JsonValue {

        static JsonElement get(String element) {
            return new JsonElement() {
                @Override
                String getElement() {
                    return element;
                }
            };
        }

        abstract String getElement();

        @Override
        final void dump(ToJson access) {
            access.dumpElement(this.getElement());
        }
    }

    public abstract static class JsonNumber extends JsonValue {

        public static JsonNumber get(Number number) {
            return number == null ? null : new JsonNumber() {
                @Override
                Number getNumber() {
                    return number;
                }
            };
        }

        abstract Number getNumber();

        @Override
        final void dump(ToJson access) {
            build();
            Number number = getNumber();
            if (number == null) {
                JsonValue.NULL.dump(access);
            } else {
                access.dumpNumber(number);
            }
        }
    }

    public abstract static class JsonString extends JsonValue {

        public static JsonString get(String string) {
            return string == null ? null : new JsonString() {
                @Override
                String getString() {
                    return string;
                }
            };
        }

        abstract String getString();

        @Override
        final void dump(ToJson access) {
            build();
            String string = getString();
            if (string == null) {
                JsonValue.NULL.dump(access);
            } else {
                access.dumpString(string);
            }
        }
    }

    public abstract static class JsonArray extends JsonValue {

        public static JsonArray get(Stream<JsonValue> values) {
            return values == null ? null : new JsonArray() {
                @Override
                Stream<JsonValue> getValues() {
                    return values;
                }
            };
        }

        abstract Stream<JsonValue> getValues();

        @Override
        final void dump(ToJson access) {
            build();
            Stream<JsonValue> values = getValues();
            if (values == null) {
                JsonValue.NULL.dump(access);
            } else {
                access.dumpArray(values);
            }
        }
    }

    public abstract static class JsonObject extends JsonValue {

        public static JsonObject get(Stream<String> names, Function<String, JsonValue> func) {
            return names == null || func == null ? null : new JsonObject() {
                @Override
                Stream<String> getNames() {
                    return names;
                }

                @Override
                JsonValue getValue(String name) {
                    return func.apply(name);
                }
            };
        }

        abstract Stream<String> getNames();

        abstract JsonValue getValue(String name);

        @Override
        final void dump(ToJson access) {
            build();
            Stream<String> names = getNames();
            if (names == null) {
                JsonValue.NULL.dump(access);
            } else {
                access.dumpObject(names, this::getValue);
            }
        }
    }
}
