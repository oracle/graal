/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.log.Log;

/** Utility class for logging metrics. */
public class MetricsLogUtils {

    /* Formatting constants. */

    static final int INDENT_LEVEL_0 = 0;
    static final int INDENT_LEVEL_1 = 2;
    static final int INDENT_LEVEL_2 = 4;

    static final int CATEGORY_FILL = 25;
    static final int VALUE_FILL = 10;
    static final int UNIT_FILL = 3;

    /* Logging methods. */

    public static void logSection(String section) {
        logSection(section, INDENT_LEVEL_0);
    }

    public static void logSubSection(String section) {
        logSection(section, INDENT_LEVEL_1);
    }

    public static void logSection(String section, int ident) {
        Log log = Log.log();
        log.newline();
        log.spaces(ident);
        log.string(section);
        log.newline();
    }

    public static void logMemoryMetric(String category, long bytes) {
        logMemoryMetric(category, WordFactory.unsigned(bytes));
    }

    public static void logMemoryMetric(String category, UnsignedWord bytes) {
        logMemoryMetric(category, bytes, DEFAULT_MEMORY_UNIT);
    }

    public static void logMemoryMetric(String category, UnsignedWord bytes, MemoryUnit unit) {
        Log log = Log.log();
        log.spaces(INDENT_LEVEL_2);
        log.string(category, CATEGORY_FILL, Log.LEFT_ALIGN).unsigned(bytesToUnit(bytes, unit), VALUE_FILL, Log.RIGHT_ALIGN).string(unit.symbol(), UNIT_FILL, Log.RIGHT_ALIGN).newline();
    }

    public static void logTimeMetric(String category, long nanos) {
        logTimeMetric(category, nanos, DEFAULT_TIME_UNIT);
    }

    public static void logTimeMetric(String category, long nanos, TimeUnit unit) {
        Log log = Log.log();
        log.spaces(INDENT_LEVEL_2);
        log.string(category, CATEGORY_FILL, Log.LEFT_ALIGN).unsigned(nanosToUnit(nanos, unit), VALUE_FILL, Log.RIGHT_ALIGN).string(unit.symbol(), UNIT_FILL, Log.RIGHT_ALIGN).newline();
    }

    public static void logCounterMetric(String category, UnsignedWord value) {
        logCounterMetric(category, value.rawValue());
    }

    public static void logCounterMetric(String category, long value) {
        Log log = Log.log();
        log.spaces(INDENT_LEVEL_2);
        log.string(category, CATEGORY_FILL, Log.LEFT_ALIGN).unsigned(value, VALUE_FILL, Log.RIGHT_ALIGN).string("#", UNIT_FILL, Log.RIGHT_ALIGN).newline();
    }

    public static void logCounterMetric(String category, String value) {
        Log log = Log.log();
        log.spaces(INDENT_LEVEL_2);
        log.string(category, CATEGORY_FILL, Log.LEFT_ALIGN).string(value, VALUE_FILL, Log.RIGHT_ALIGN).string("#", UNIT_FILL, Log.RIGHT_ALIGN).newline();
    }

    public static void logPercentMetric(String category, long value) {
        Log log = Log.log();
        log.spaces(INDENT_LEVEL_2);
        log.string(category, CATEGORY_FILL, Log.LEFT_ALIGN).unsigned(value, VALUE_FILL, Log.RIGHT_ALIGN).string("%", UNIT_FILL, Log.RIGHT_ALIGN).newline();
    }

    public static void logPercentMetric(String category, String value) {
        Log log = Log.log();
        log.spaces(INDENT_LEVEL_2);
        log.string(category, CATEGORY_FILL, Log.LEFT_ALIGN).string(value, VALUE_FILL, Log.RIGHT_ALIGN).string("%", UNIT_FILL, Log.RIGHT_ALIGN).newline();
    }

    public static void logConfig(String category, String value) {
        Log log = Log.log();
        log.spaces(INDENT_LEVEL_2);
        log.string(category, CATEGORY_FILL, Log.LEFT_ALIGN).string(value, VALUE_FILL, Log.RIGHT_ALIGN).newline();
    }

    /* Time metrics. */

    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MILLIS;

    private enum TimeUnit {
        NANOS("ns") {
            @Override
            long fromNanos(long nanos) {
                return nanos;
            }
        },
        MILLIS("ms") {
            @Override
            long fromNanos(long nanos) {
                return TimeUtils.roundNanosToMillis(nanos);
            }
        },
        SECONDS("s") {
            @Override
            long fromNanos(long nanos) {
                return TimeUtils.roundNanosToSeconds(nanos);
            }
        };

        private final String symbol;

        TimeUnit(String unit) {
            this.symbol = unit;
        }

        public String symbol() {
            return symbol;
        }

        abstract long fromNanos(long nanos);
    }

    public static long nanosToUnit(final long nanos, TimeUnit unit) {
        return unit.fromNanos(nanos);
    }

    /* Memory metrics. */

    private static final MemoryUnit DEFAULT_MEMORY_UNIT = MemoryUnit.KILO;

    protected enum MemoryUnit {
        BYTE("B") {
            @Override
            UnsignedWord fromBytes(UnsignedWord bytes) {
                return bytes;
            }
        },
        KILO("KB") {
            @Override
            UnsignedWord fromBytes(UnsignedWord bytes) {
                return toKilo(bytes);
            }
        },
        MEGA("MB") {
            @Override
            UnsignedWord fromBytes(UnsignedWord bytes) {
                return toKilo(toKilo(bytes));
            }
        },
        GIGA("GB") {
            @Override
            UnsignedWord fromBytes(UnsignedWord bytes) {
                return toKilo(toKilo(toKilo(bytes)));
            }
        };

        private final String symbol;

        MemoryUnit(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        abstract UnsignedWord fromBytes(UnsignedWord bytes);

        private static UnsignedWord toKilo(final UnsignedWord bytes) {
            final UnsignedWord bytedPerKilo = WordFactory.unsigned(1_024L);
            final UnsignedWord result = bytes.unsignedDivide(bytedPerKilo);
            return result;
        }
    }

    public static UnsignedWord bytesToUnit(final UnsignedWord bytes) {
        return bytesToUnit(bytes, DEFAULT_MEMORY_UNIT);
    }

    public static UnsignedWord bytesToUnit(UnsignedWord bytes, MemoryUnit unit) {
        return unit.fromBytes(bytes);
    }
}
