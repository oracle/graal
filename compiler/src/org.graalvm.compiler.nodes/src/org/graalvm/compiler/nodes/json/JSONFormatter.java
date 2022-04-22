/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.json;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import java.util.List;

public class JSONFormatter {
    public static String formatJSON(EconomicMap<String, Object> map) {
        return formatJSON(map, false);
    }

    public static String formatJSON(EconomicMap<String, Object> map, boolean indent) {
        StringBuilder sb = new StringBuilder();
        appendTo(sb, map, indent ? "    " : null, "");
        return sb.toString();
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

    static void appendValue(StringBuilder sb, Object value, String indent, String currentIndent) {
        if (value instanceof EconomicMap<?, ?>) {
            appendTo(sb, (EconomicMap<?, ?>) value, indent, currentIndent);
        } else if (value instanceof List<?>) {
            appendTo(sb, (List<?>) value, indent, currentIndent);
        } else if (value instanceof Integer || value instanceof Boolean || value == null) {
            sb.append(value);
        } else {
            sb.append(quote(String.valueOf(value)));
        }
    }

    static void appendTo(StringBuilder sb, List<?> contents, String indent, String currentIndent) {
        String newIndent = indent + currentIndent;
        sb.append("[");
        if (indent != null) {
            sb.append('\n');
        }
        boolean comma = false;
        for (Object value : contents) {
            if (comma) {
                if (indent != null) {
                    sb.append(",\n");
                } else {
                    sb.append(", ");
                }
            }
            appendValue(sb, value, indent, newIndent);
            comma = true;
        }
        if (indent != null) {
            sb.append('\n');
            sb.append(currentIndent);
        }
        sb.append("]");
    }

    static void appendTo(StringBuilder sb, EconomicMap<?, ?> contents, String indent, String currentIndent) {
        String newIndent = indent + currentIndent;
        if (indent != null) {
            sb.append(currentIndent);
        }
        sb.append("{");
        if (indent != null) {
            sb.append('\n');
        }
        boolean comma = false;
        MapCursor<?, ?> cursor = contents.getEntries();
        while (cursor.advance()) {
            if (comma) {
                if (indent != null) {
                    sb.append(",\n");
                } else {
                    sb.append(", ");
                }
            }
            if (indent != null) {
                sb.append(newIndent);
            }
            sb.append(quote((String) cursor.getKey()));
            sb.append(": ");
            appendValue(sb, cursor.getValue(), indent, newIndent);
            comma = true;
        }
        if (indent != null) {
            sb.append('\n');
            sb.append(currentIndent);
        }
        sb.append("}");
    }
}
