/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.util;

import com.oracle.truffle.regex.chardata.Constants;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class DebugUtil {

    public static final boolean DEBUG = false;
    public static final boolean DEBUG_STEP_EXECUTION = false;
    public static final boolean DEBUG_ALWAYS_EAGER = false;
    public static final boolean LOG_SWITCH_TO_EAGER = false;
    public static final boolean LOG_TOTAL_COMPILATION_TIME = false;
    public static final boolean LOG_PHASES = false;
    public static final boolean LOG_BAILOUT_MESSAGES = false;
    public static final boolean LOG_AUTOMATON_SIZES = false;

    private static final CompilationFinalBitSet validSpecialCharsForFileNames = CompilationFinalBitSet.valueOf(
                    '^', '$', '.', '*', '+', '-', '?', '(', ')', '[', ']', '{', '}', '|');

    @TruffleBoundary
    public static String charToString(int c) {
        if (Constants.WORD_CHARS.contains(c)) {
            return String.valueOf((char) c);
        } else if (c <= 0xff) {
            return String.format("\\x%02x", c);
        } else if (c <= 0xffff) {
            return String.format("\\u%04x", c);
        } else {
            return String.format("\\u{%06x}", c);
        }
    }

    @TruffleBoundary
    public static String escapeString(String s) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            ret.append(charToString(s.charAt(i)));
        }
        return ret.toString();
    }

    @TruffleBoundary
    public static StringBuilder appendNodeId(StringBuilder sb, int id) {
        return sb.append(nodeID(id));
    }

    @TruffleBoundary
    public static String nodeID(int id) {
        return String.format("%04x", id);
    }

    private static final Pattern specialChars = Pattern.compile("[\"\\\\\u0000-\u001F\u007F-\u009F]");

    @TruffleBoundary
    public static String jsStringEscape(String str) {
        StringBuffer escapedString = new StringBuffer();
        Matcher m = specialChars.matcher(str);
        while (m.find()) {
            String replacement;
            char c = str.charAt(m.start());
            if (c == '"') {
                replacement = "\\\\\"";
            } else if (c == '\\') {
                replacement = "\\\\\\\\";
            } else {
                assert Character.isISOControl(c);
                replacement = String.format("\\\\u%04x", (int) c);
            }
            m.appendReplacement(escapedString, replacement);
        }
        m.appendTail(escapedString);
        return escapedString.toString();
    }

    @TruffleBoundary
    public static String randomJsStringFromRanges(char[] ranges, int length) {
        Random random = new Random(System.currentTimeMillis());
        StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int rangeIndex = random.nextInt(ranges.length / 2);
            char lo = ranges[rangeIndex * 2];
            char hi = ranges[rangeIndex * 2 + 1];
            char randChar = (char) (lo + random.nextInt((hi + 1) - lo));
            if (randChar == '"') {
                stringBuilder.append("\\\\\"");
            } else if (randChar == '\\') {
                stringBuilder.append("\\\\\\\\");
            } else if (randChar > 0x7f || Character.isISOControl(randChar)) {
                stringBuilder.append(String.format("\\u%04x", (int) randChar));
            } else {
                stringBuilder.append(randChar);
            }
        }
        return stringBuilder.toString();
    }

    public static boolean isValidCharForFileName(int c) {
        return Character.isLetterOrDigit(c) || validSpecialCharsForFileNames.get(c);
    }

    public static class Timer {

        private long startTime = 0;

        public void start() {
            startTime = System.nanoTime();
        }

        public long getElapsed() {
            return System.nanoTime() - startTime;
        }

        public String elapsedToString() {
            return elapsedToString(getElapsed());
        }

        public static String elapsedToString(long elapsed) {
            return String.format("%fms", elapsed / 1e6);
        }
    }

    public static class DebugLogger {

        private final String prefix;
        private final boolean enable;

        public DebugLogger(String prefix, boolean enable) {
            this.prefix = prefix;
            this.enable = enable;
        }

        public void log(String msg) {
            if (enable) {
                System.out.println(prefix + msg);
            }
        }
    }

}
