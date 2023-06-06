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
package org.graalvm.util.json;

import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

public class JSONFormatter {

    private static final String DEFAULT_INDENT = "    ";

    private static final String EMPTY_STRING = "";

    private static final String DOUBLE_QUOTE_ESCAPED = "\\\"";

    private static final String BACKSLASH_ESCAPED = "\\\\";

    private static final String BACKSPACE_ESCAPED = "\\b";

    private static final String FORM_FEED_ESCAPED = "\\f";

    private static final String NEWLINE_ESCAPED = "\\n";

    private static final String CARRIAGE_RETURN_ESCAPED = "\\r";

    private static final String TAB_ESCAPED = "\\t";

    private static final String UNICODE_CHARACTER_PREFIX = "\\u00";

    private static final char LEFT_CURLY_BRACKET = '{';

    private static final char RIGHT_CURLY_BRACKET = '}';

    private static final char LEFT_SQUARE_BRACKET = '[';

    private static final char RIGHT_SQUARE_BRACKET = ']';

    private static final String COMMA_NEWLINE = ",\n";

    private static final String COMMA_SPACE = ", ";

    private static final String COLON_SPACE = ": ";

    private static final char NEWLINE = '\n';

    private static final char DOUBLE_QUOTE = '"';

    private static final char BACKSLASH = '\\';

    private static final char BACKSPACE = '\b';

    private static final char FORM_FEED = '\f';

    private static final char CARRIAGE_RETURN = '\r';

    private static final char TAB = '\t';

    private static final char SPACE = ' ';

    public static String formatJSON(EconomicMap<String, Object> map) {
        return formatJSON(map, false);
    }

    public static String formatJSON(EconomicMap<String, Object> map, boolean indent) {
        StringBuilder sb = new StringBuilder();
        appendTo(sb, map, indent ? DEFAULT_INDENT : null, EMPTY_STRING);
        return sb.toString();
    }

    public static String formatJSON(List<?> elements) {
        return formatJSON(elements, false);
    }

    public static String formatJSON(List<?> elements, boolean indent) {
        StringBuilder sb = new StringBuilder();
        appendTo(sb, elements, indent ? DEFAULT_INDENT : null, EMPTY_STRING);
        return sb.toString();
    }

    private static String quote(CharSequence value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append(DOUBLE_QUOTE);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case DOUBLE_QUOTE:
                    builder.append(DOUBLE_QUOTE_ESCAPED);
                    break;
                case BACKSLASH:
                    builder.append(BACKSLASH_ESCAPED);
                    break;
                case BACKSPACE:
                    builder.append(BACKSPACE_ESCAPED);
                    break;
                case FORM_FEED:
                    builder.append(FORM_FEED_ESCAPED);
                    break;
                case NEWLINE:
                    builder.append(NEWLINE_ESCAPED);
                    break;
                case CARRIAGE_RETURN:
                    builder.append(CARRIAGE_RETURN_ESCAPED);
                    break;
                case TAB:
                    builder.append(TAB_ESCAPED);
                    break;
                default: {
                    if (c < SPACE) {
                        builder.append(UNICODE_CHARACTER_PREFIX);
                        builder.append(Character.forDigit((c >> 4) & 0xF, 16));
                        builder.append(Character.forDigit(c & 0xF, 16));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        builder.append(DOUBLE_QUOTE);
        return builder.toString();
    }

    static void appendValue(StringBuilder sb, Object value, String indent, String currentIndent) {
        if (value instanceof EconomicMap<?, ?>) {
            appendTo(sb, (EconomicMap<?, ?>) value, indent, currentIndent);
        } else if (value instanceof List<?>) {
            appendTo(sb, (List<?>) value, indent, currentIndent);
        } else if (value instanceof Number || value instanceof Boolean || value == null) {
            sb.append(value);
        } else {
            if (value instanceof Map<?, ?>) {
                throw new IllegalArgumentException(value + " must use EconomicMap");
            }
            sb.append(quote(String.valueOf(value)));
        }
    }

    static void appendTo(StringBuilder sb, List<?> contents, String indent, String currentIndent) {
        String newIndent = indent + currentIndent;
        sb.append(LEFT_SQUARE_BRACKET);
        if (indent != null) {
            sb.append(NEWLINE);
        }
        boolean comma = false;
        for (Object value : contents) {
            if (comma) {
                if (indent != null) {
                    sb.append(COMMA_NEWLINE);
                } else {
                    sb.append(COMMA_SPACE);
                }
            }
            if (indent != null) {
                sb.append(newIndent);
            }
            appendValue(sb, value, indent, newIndent);
            comma = true;
        }
        if (indent != null) {
            sb.append(NEWLINE);
            sb.append(currentIndent);
        }
        sb.append(RIGHT_SQUARE_BRACKET);
    }

    static void appendTo(StringBuilder sb, EconomicMap<?, ?> contents, String indent, String currentIndent) {
        String newIndent = indent + currentIndent;
        if (indent != null) {
            sb.append(currentIndent);
        }
        sb.append(LEFT_CURLY_BRACKET);
        if (indent != null) {
            sb.append(NEWLINE);
        }
        boolean comma = false;
        MapCursor<?, ?> cursor = contents.getEntries();
        while (cursor.advance()) {
            if (comma) {
                if (indent != null) {
                    sb.append(COMMA_NEWLINE);
                } else {
                    sb.append(COMMA_SPACE);
                }
            }
            if (indent != null) {
                sb.append(newIndent);
            }
            sb.append(quote((String) cursor.getKey()));
            sb.append(COLON_SPACE);
            appendValue(sb, cursor.getValue(), indent, newIndent);
            comma = true;
        }
        if (indent != null) {
            sb.append(NEWLINE);
            sb.append(currentIndent);
        }
        sb.append(RIGHT_CURLY_BRACKET);
    }
}
