/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.logging;

import java.util.Locale;

/// Defines the levels understood by unified logging in increasing order of severity.
public enum LogLevel {
    OFF,
    TRACE,
    DEBUG,
    INFO,
    WARNING,
    ERROR;

    static final LogLevel[] VALUES = LogLevel.values();

    public static LogLevel forInt(int level, IllegalArgumentException onError) {
        if (level < 0 || level >= VALUES.length) {
            throw onError;
        }
        return VALUES[level];
    }

    /// External spelling used by selectors and decorations.
    private final String label;

    LogLevel() {
        this.label = name().toLowerCase(Locale.ROOT);
    }

    /// Parses the case-insensitive command-line spelling.
    public static LogLevel fromString(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            String suggestion = closestTo(value);
            String suffix = suggestion == null ? "" : " Did you mean '" + suggestion + "'?";
            throw new IllegalArgumentException("Invalid log level '" + value + "'." + suffix + " Available log levels are: off, trace, debug, info, warning, error", ex);
        }
    }

    private static String closestTo(String value) {
        String result = null;
        int distance = Integer.MAX_VALUE;
        for (LogLevel level : values()) {
            int candidateDistance = editDistance(level.label, value.toLowerCase(Locale.ROOT));
            if (candidateDistance < distance) {
                result = level.label;
                distance = candidateDistance;
            }
        }
        return distance <= Math.max(1, value.length() / 2) ? result : null;
    }

    /// Computes the Levenshtein distance between `left` and `right`. The lower
    /// the returned value, the more similar `left` and `right` are in terms
    /// of content.
    static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitution = previous[rightIndex - 1] + (left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1);
                current[rightIndex] = Math.min(Math.min(previous[rightIndex] + 1, current[rightIndex - 1] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    /// Gets the command-line spelling of this level.
    public String label() {
        return label;
    }

    /// Returns whether a threshold enables a message at `messageLevel`.
    public boolean enables(LogLevel messageLevel) {
        return this != OFF && messageLevel.ordinal() >= ordinal();
    }
}
