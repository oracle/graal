/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import java.io.PrintStream;

/**
 * Utilities and global definitions for creating CSV output.
 */
public final class CSVUtil {
    public static final char SEPARATOR = ';';
    public static final String SEPARATOR_STR = String.valueOf(SEPARATOR);
    public static final char QUOTE = '"';
    public static final char ESCAPE = '\\';

    public static String buildFormatString(String... format) {
        return String.join(SEPARATOR_STR, format);
    }

    public static String buildFormatString(String format, char separator, int num) {
        StringBuilder sb = new StringBuilder(num * (format.length() + 1) - 1);
        sb.append(format);
        for (int i = 1; i < num; i++) {
            sb.append(separator).append(format);
        }
        return sb.toString();
    }

    public static final class Escape {

        public static PrintStream println(PrintStream out, String format, Object... args) {
            return println(out, SEPARATOR, QUOTE, ESCAPE, format, args);
        }

        public static LogStream println(LogStream out, String format, Object... args) {
            return println(out, SEPARATOR, QUOTE, ESCAPE, format, args);
        }

        public static String escape(String str) {
            return escape(str, SEPARATOR, QUOTE, ESCAPE);
        }

        public static String escapeRaw(String str) {
            return escapeRaw(str, QUOTE, ESCAPE);
        }

        public static PrintStream println(PrintStream out, char separator, char quote, char escape, String format, Object... args) {
            out.printf(format, escapeArgs(separator, quote, escape, args));
            out.println();
            return out;
        }

        public static LogStream println(LogStream out, char separator, char quote, char escape, String format, Object... args) {
            out.printf(format, escapeArgs(separator, quote, escape, args));
            out.println();
            return out;
        }

        public static Object[] escapeArgs(Object... args) {
            return escapeArgs(SEPARATOR, QUOTE, ESCAPE, args);
        }

        public static Object[] escapeArgsFormatString(Object... args) {
            return escapeArgs(SEPARATOR, QUOTE, ESCAPE, true, args);
        }

        public static Object[] escapeArgs(char separator, char quote, char escape, Object... args) {
            return escapeArgs(separator, quote, escape, false, args);
        }

        public static Object[] escapeArgs(char separator, char quote, char escape, boolean isFormatString, Object... args) {
            String separatorStr = String.valueOf(separator);
            for (int i = 0; i < args.length; i++) {
                Object obj = args[i];
                if (obj instanceof String) {
                    String str = (String) obj;
                    if (str.contains(separatorStr)) {
                        args[i] = escapeRaw(str, quote, escape);
                    }
                    if (isFormatString && str.contains("%")) {
                        args[i] = str.replace("%", "%%");
                    }
                }
            }
            return args;
        }

        public static String escape(String str, char separator, char quote, char escape) {
            String separatorStr = String.valueOf(separator);
            if (str.contains(separatorStr) || str.contains("%")) {
                return escapeRaw(str, quote, escape);
            }
            return str;
        }

        public static String escapeRaw(String str, char quote, char escape) {
            String quoteStr = String.valueOf(quote);
            String escapeStr = String.valueOf(escape);
            String escapedEscapeStr = escapeStr + escape;
            String escapedQuoteStr = escapeStr + quote;
            String str1 = str.replace(escapeStr, escapedEscapeStr);
            String str2 = str1.replace(quoteStr, escapedQuoteStr);
            String str3 = quoteStr + str2 + quote;
            return str3;
        }
    }
}
