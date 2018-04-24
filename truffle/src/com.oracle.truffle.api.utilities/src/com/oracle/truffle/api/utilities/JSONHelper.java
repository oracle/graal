/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Helper function that allows to dump the AST during creation to a JSON format.
 *
 * @since 0.8 or earlier
 */
public class JSONHelper {
    /**
     * @deprecated accidentally public - to be removed.
     * @since 0.8 or earlier
     */
    @Deprecated
    public JSONHelper() {
    }

    private static StringBuilder AstJsonDumpBuilder = new StringBuilder();

    /** @since 0.8 or earlier */
    @Deprecated
    public static void dumpNewChild(Node parentNode, Node childNode) {
        if (AstJsonDumpBuilder != null) {
            AstJsonDumpBuilder.append("{ \"action\": \"insertNode\", \"parentId\": \"" + getID(parentNode) + "\", \"newId\": \"" + getID(childNode) + "\" },\n");
        }
    }

    /** @since 0.8 or earlier */
    @Deprecated
    public static void dumpReplaceChild(Node oldNode, Node newNode, CharSequence reason) {
        if (AstJsonDumpBuilder != null) {
            AstJsonDumpBuilder.append("{ \"action\": \"replaceNode\", \"oldId\": \"" + getID(oldNode) + "\", \"newId\": \"" + getID(newNode) + "\", \"reason\": " + quote(reason) + " },\n");
        }
    }

    /** @since 0.8 or earlier */
    @Deprecated
    public static void dumpNewNode(Node newNode) {
        if (AstJsonDumpBuilder != null) {
            String language = "";
            RootNode root = newNode.getRootNode();
            if (root != null) {
                TruffleLanguage<?> clazz = root.getLanguage(TruffleLanguage.class);
                if (clazz != null) {
                    language = clazz.getClass().getName();
                }
            }
            AstJsonDumpBuilder.append("{ \"action\": \"createNode\", \"newId\": \"" + getID(newNode) + "\", \"type\": \"" + getType(newNode) + "\", \"description\": \"" + newNode.getDescription() +
                            "\", \"language\": \"" + language + "\"" + " },\n");
        }
    }

    /** @since 0.8 or earlier */
    @Deprecated
    public static String getResult() {
        return AstJsonDumpBuilder.toString();
    }

    private static String getID(Node newChild) {
        return String.valueOf(newChild.hashCode());
    }

    private static String getType(Node node) {
        return node.getClass().getSimpleName();
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
    @Deprecated
    public static void restart() {
        AstJsonDumpBuilder = new StringBuilder();
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
        /**
         * @deprecated accidentally public - don't use
         * @since 0.8 or earlier
         */
        @Deprecated
        protected JSONStringBuilder() {
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

    static final DumpAccessor ACCESSOR = new DumpAccessor();

    private static final class DumpAccessor extends Accessor {
        private static final DumpSupport DUMP_SUPPORT = new DumpSupport() {
            @Override
            public void dump(Node newNode, Node newChild, CharSequence reason) {
                if (reason != null) {
                    dumpReplaceChild(newNode, newChild, reason);
                } else {
                    if (newChild != null) {
                        dumpNewChild(newNode, newChild);
                    } else {
                        dumpNewNode(newNode);
                    }
                }
            }
        };

        @Override
        protected DumpSupport dumpSupport() {
            return DUMP_SUPPORT;
        }
    }
}
