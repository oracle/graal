/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This is an interruptible wrapper around some of the {@link String} methods that we usually use to
 * avoid pulling regular expression support into small Native Images (such as HelloWorld).
 */
public class StringUtil {

    /**
     * Similar to {@link String#split(String)} but with a fixed separator string instead of a
     * regular expression. This avoids making regular expression code reachable.
     */
    public static String[] split(String value, String separator) {
        return split(value, separator, 0);
    }

    /**
     * Similar to {@link String#split(String, int)} but with a fixed separator string instead of a
     * regular expression. This avoids making regular expression code reachable.
     */
    public static String[] split(String value, String separator, int limit) {
        int offset = 0;
        int next;
        ArrayList<String> list = null;
        while ((next = value.indexOf(separator, offset)) != -1) {
            if (list == null) {
                list = new ArrayList<>();
            }
            boolean limited = limit > 0;
            if (!limited || list.size() < limit - 1) {
                list.add(value.substring(offset, next));
                offset = next + separator.length();
            } else {
                break;
            }
        }

        if (offset == 0) {
            /* No match found. */
            return new String[]{value};
        }

        /* Add remaining segment. */
        list.add(value.substring(offset));

        return list.toArray(new String[list.size()]);
    }

    /**
     * Joins an array of strings using single quotes, and `, ` as a delimiter. Useful to print
     * possible values for options.
     */
    public static final String joinSingleQuoted(String... values) {
        return "'" + String.join("', '", values) + "'";
    }

    /**
     * See {@link #joinSingleQuoted(String...)}.
     */
    public static final String joinSingleQuoted(Object[] values) {
        String[] strings = new String[values.length];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = Objects.toString(values[i]);
        }
        return joinSingleQuoted(strings);
    }

    /**
     * See {@link #joinSingleQuoted(String...)}.
     */
    public static final String joinSingleQuoted(List<String> strings) {
        return joinSingleQuoted(strings.toArray(new String[strings.size()]));
    }

    public static String toSlashSeparated(String string) {
        return string.replace('.', '/');
    }

    public static String toDotSeparated(String string) {
        return string.replace('/', '.');
    }

    public static int numberOfCharsInString(char c, String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                count++;
            }
        }

        return count;
    }
}
