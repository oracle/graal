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

/// Represents a selection of decorators that should be prepended to
/// each log message for a given output. Decorators are always prepended
/// their enum order. For example, logging with `level,tags,uptime`
/// decorators results in:
/// ```
/// [0.019s][info][class,load,image] message
/// ```
public final class LogDecorators {

    /// Decorators in the same output order as HotSpot.
    public enum Decorator {
        TIME("time", "t"),
        UTCTIME("utctime", "utc"),
        UPTIME("uptime", "u"),
        TIMEMILLIS("timemillis", "tm"),
        UPTIMEMILLIS("uptimemillis", "um"),
        TIMENANOS("timenanos", "tn"),
        UPTIMENANOS("uptimenanos", "un"),
        HOSTNAME("hostname", "hn"),
        PID("pid", "p"),
        TID("tid", "ti"),
        LEVEL("level", "l"),
        TAGS("tags", "tg");

        /// Long command-line spelling.
        private final String label;

        /// Short command-line spelling.
        private final String abbreviation;

        Decorator(String label, String abbreviation) {
            this.label = label;
            this.abbreviation = abbreviation;
        }

        public String label() {
            return label;
        }

        public String abbreviation() {
            return abbreviation;
        }

        static Decorator fromString(String value) {
            for (Decorator decorator : VALUES) {
                if (decorator.label.equalsIgnoreCase(value) || decorator.abbreviation.equalsIgnoreCase(value)) {
                    return decorator;
                }
            }
            throw new IllegalArgumentException("Invalid log decorator '" + value + "'.");
        }

        public int bit() {
            return 1 << ordinal();
        }
    }

    /// Cached declaration-order values avoid enum-array allocation on emergency logging paths.
    static final Decorator[] VALUES = Decorator.values();

    /// Explicit configuration with no decorations.
    public static final LogDecorators NONE = new LogDecorators(0);

    /// Default uptime, level, and tags decorations.
    public static final LogDecorators DEFAULT = new LogDecorators(bit(Decorator.UPTIME) | bit(Decorator.LEVEL) | bit(Decorator.TAGS));

    /// Enabled decorators represented as an ordinal-indexed bit set.
    private final int decorators;

    private LogDecorators(int decorators) {
        this.decorators = decorators;
    }

    /// Parses a decorator argument, using defaults when it is absent or empty.
    public static LogDecorators parse(String value) {
        if (value == null || value.isEmpty()) {
            return DEFAULT;
        }
        if (value.equalsIgnoreCase("none")) {
            return NONE;
        }
        int result = 0;
        for (String item : value.split(",", -1)) {
            if (item.isEmpty()) {
                throw new IllegalArgumentException("Invalid empty log decorator.");
            }
            Decorator decorator = Decorator.fromString(item);
            int decoratorBit = bit(decorator);
            if ((result & decoratorBit) != 0) {
                throw new IllegalArgumentException("Duplicate log decorator '" + item + "'.");
            }
            result |= decoratorBit;
        }
        return new LogDecorators(result);
    }

    public boolean contains(Decorator decorator) {
        return (decorators & bit(decorator)) != 0;
    }

    public boolean containsAny(int decoratorsMask) {
        return (decorators & decoratorsMask) != 0;
    }

    public int size() {
        return Integer.bitCount(decorators);
    }

    /// Returns whether no decorators are enabled.
    public boolean isEmpty() {
        return decorators == 0;
    }

    /// Combines this decorator set with `other`.
    LogDecorators union(LogDecorators other) {
        return new LogDecorators(decorators | other.decorators);
    }

    /// Gets the bit corresponding to `decorator`'s declaration ordinal.
    private static int bit(Decorator decorator) {
        return decorator.bit();
    }
}
