/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper function that allows to dump the AST during creation to a JSON format.
 *
 * @since 0.8 or earlier
 */
public final class JSONHelper {

    private JSONHelper() {
    }

    private static String quote(CharSequence value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default: {
                    if (c < ' ') {
                        builder.append("\\u00");
                        builder.append(Character.forDigit((c >> 4) & 0xF, 16));
                        builder.append(Character.forDigit(c & 0xF, 16));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    /** @since 0.8 or earlier */
    public static JSONObjectBuilder object() {
        return new JSONObjectBuilder();
    }

    /** @since 0.8 or earlier */
    public static JSONArrayBuilder array() {
        return new JSONArrayBuilder();
    }

    /**
     * @since 0.8 or earlier
     */
    public abstract static class JSONStringBuilder {

        private JSONStringBuilder() {
        }

        /** @since 0.8 or earlier */
        @Override
        public final String toString() {
            StringBuilder sb = new StringBuilder();
            appendTo(sb);
            return sb.toString();
        }

        /** @since 0.8 or earlier */
        protected abstract void appendTo(StringBuilder sb);

        /** @since 0.8 or earlier */
        protected static void appendValue(StringBuilder sb, Object value) {
            if (value instanceof JSONStringBuilder) {
                ((JSONStringBuilder) value).appendTo(sb);
            } else if (value instanceof Integer || value instanceof Boolean || value == null) {
                sb.append(value);
            } else {
                sb.append(quote(String.valueOf(value)));
            }
        }
    }

    /** @since 0.8 or earlier */
    public static final class JSONObjectBuilder extends JSONStringBuilder {
        private final Map<String, Object> contents = new LinkedHashMap<>();

        private JSONObjectBuilder() {
        }

        /** @since 0.8 or earlier */
        public JSONObjectBuilder add(String key, String value) {
            contents.put(key, value);
            return this;
        }

        /** @since 0.8 or earlier */
        public JSONObjectBuilder add(String key, Number value) {
            contents.put(key, value);
            return this;
        }

        /** @since 0.8 or earlier */
        public JSONObjectBuilder add(String key, Boolean value) {
            contents.put(key, value);
            return this;
        }

        /** @since 0.8 or earlier */
        public JSONObjectBuilder add(String key, JSONStringBuilder value) {
            contents.put(key, value);
            return this;
        }

        /** @since 0.8 or earlier */
        @Override
        protected void appendTo(StringBuilder sb) {
            sb.append("{");
            boolean comma = false;
            for (Map.Entry<String, Object> entry : contents.entrySet()) {
                if (comma) {
                    sb.append(", ");
                }
                sb.append(quote(entry.getKey()));
                sb.append(": ");
                appendValue(sb, entry.getValue());
                comma = true;
            }
            sb.append("}");
        }
    }

    /** @since 0.8 or earlier */
    public static final class JSONArrayBuilder extends JSONStringBuilder {
        private final List<Object> contents = new ArrayList<>();

        private JSONArrayBuilder() {
        }

        /** @since 0.8 or earlier */
        public JSONArrayBuilder add(String value) {
            contents.add(value);
            return this;
        }

        /** @since 0.8 or earlier */
        public JSONArrayBuilder add(Number value) {
            contents.add(value);
            return this;
        }

        /** @since 0.8 or earlier */
        public JSONArrayBuilder add(Boolean value) {
            contents.add(value);
            return this;
        }

        /** @since 0.8 or earlier */
        public JSONArrayBuilder add(JSONStringBuilder value) {
            contents.add(value);
            return this;
        }

        /** @since 0.8 or earlier */
        @Override
        protected void appendTo(StringBuilder sb) {
            sb.append("[");
            boolean comma = false;
            for (Object value : contents) {
                if (comma) {
                    sb.append(", ");
                }
                appendValue(sb, value);
                comma = true;
            }
            sb.append("]");
        }
    }

}
