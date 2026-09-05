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

import static com.oracle.svm.core.logging.LogDecorators.Decorator.TID;
import static com.oracle.svm.core.logging.LogDecorators.Decorator.TIME;
import static com.oracle.svm.core.logging.LogDecorators.Decorator.TIMEMILLIS;
import static com.oracle.svm.core.logging.LogDecorators.Decorator.TIMENANOS;
import static com.oracle.svm.core.logging.LogDecorators.Decorator.UPTIME;
import static com.oracle.svm.core.logging.LogDecorators.Decorator.UPTIMEMILLIS;
import static com.oracle.svm.core.logging.LogDecorators.Decorator.UPTIMENANOS;
import static com.oracle.svm.core.logging.LogDecorators.Decorator.UTCTIME;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.LibCHelper;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.util.TimeUtils;
import com.oracle.svm.shared.util.VMError;

/// Keeps resolved values for a [LogDecorators] value, as well as the
/// code to format them. The values are captured when [writing][LogTagSet#write]
/// a log message to its outputs.
public final class LogDecorations {
    /// Number of seconds in a civil day.
    private static final long SECONDS_PER_DAY = 86_400;

    /// Number of days between the Java epoch and the proleptic Gregorian year zero.
    private static final long DAYS_0000_TO_1970 = 719_528;

    /// Shared facade that resolves event data from the current thread.
    private static final LogDecorations THREAD_LOCAL = new LogDecorations();

    /// Tag set associated with a queued event, or null for the thread-local facade.
    private LogTagSet tagSet;

    /// Identifies whether this instance resolves values from the current thread.
    private final boolean threadLocal;

    /// The [System#currentTimeMillis()] timestamp associated with this event.
    private long systemMillis;

    /// The [System#nanoTime()] timestamp associated with this event.
    private long systemNanos;

    /// Isolate uptime associated with this event.
    private long uptimeNanos;

    /// Thread identifier associated with this event.
    private long threadId;

    /// Creates an empty decoration record associated with `tagSet`.
    public LogDecorations(LogTagSet tagSet) {
        this.tagSet = tagSet;
        this.threadLocal = false;
    }

    private LogDecorations() {
        this.tagSet = null;
        this.threadLocal = true;
    }

    public LogTagSet getTagSet() {
        return threadLocal ? LogThreadLocal.activeTagSet() : tagSet;
    }

    /// Copies event metadata from `source`.
    void copyFrom(LogDecorations source) {
        VMError.guarantee(source.threadLocal, "Expected thread-local decorations");
        LogThreadLocal.Data data = LogThreadLocal.get();
        systemMillis = data.getSystemMillis();
        systemNanos = data.getSystemNanos();
        uptimeNanos = data.getUptimeNanos();
        threadId = data.getThreadId();
        tagSet = LogThreadLocal.activeTagSet();
    }

    private static final int TIME_MILLIS_DECORATORS = TIME.bit() | UTCTIME.bit() | TIMEMILLIS.bit();
    private static final int TIME_NANOS_DECORATORS = TIMENANOS.bit();
    private static final int UPTIME_DECORATORS = UPTIME.bit() | UPTIMEMILLIS.bit() | UPTIMENANOS.bit();

    /// Captures event metadata in the current thread's logging state.
    static LogDecorations capture(LogDecorators decorators) {
        LogThreadLocal.Data data = LogThreadLocal.get();
        data.setSystemMillis(decorators.containsAny(TIME_MILLIS_DECORATORS) ? TimeUtils.currentTimeMillis() : 0);
        data.setSystemNanos(decorators.containsAny(TIME_NANOS_DECORATORS) ? System.nanoTime() : 0);
        data.setUptimeNanos(decorators.containsAny(UPTIME_DECORATORS) ? System.nanoTime() - Isolates.getStartTimeNanos() : 0);
        data.setThreadId(decorators.contains(TID) ? Thread.currentThread().threadId() : 0);
        return THREAD_LOCAL;
    }

    /// Writes one decorator value for `level` from this event record to `target`.
    public void value(LogDecorators.Decorator decorator, LogLevel level, NativeMemoryLog target) {
        switch (decorator) {
            case TIME -> {
                long value = systemMillis();
                writeDateTime(target, value, LibCHelper.SVM_localUTCOffsetSeconds(value), false);
            }
            case UTCTIME -> writeDateTime(target, systemMillis(), 0, true);
            case UPTIME -> writeRoundedUptime(target, uptimeNanos());
            case TIMEMILLIS -> target.signed(systemMillis()).string("ms");
            case UPTIMEMILLIS -> target.signed(uptimeNanos() / 1_000_000).string("ms");
            case TIMENANOS -> target.signed(systemNanos()).string("ns");
            case UPTIMENANOS -> target.signed(uptimeNanos()).string("ns");
            case HOSTNAME -> target.string(LogConfiguration.hostname());
            case PID -> target.signed(LogConfiguration.pid());
            case TID -> target.signed(threadId());
            case LEVEL -> target.string(level.label());
            case TAGS -> target.string(getTagSet().commaSeparatedLabel());
        }
    }

    private long systemMillis() {
        return threadLocal ? LogThreadLocal.get().getSystemMillis() : systemMillis;
    }

    private long systemNanos() {
        return threadLocal ? LogThreadLocal.systemNanos() : systemNanos;
    }

    private long uptimeNanos() {
        return threadLocal ? LogThreadLocal.uptimeNanos() : uptimeNanos;
    }

    private long threadId() {
        return threadLocal ? LogThreadLocal.threadId() : threadId;
    }

    /// Writes either ISO offset time or the fixed millisecond form used by the low-level path.
    private static void writeDateTime(Log target, long systemMillis, int utcOffsetSeconds, boolean utc) {
        long localSeconds = Math.floorDiv(systemMillis, 1_000) + utcOffsetSeconds;
        long epochDay = Math.floorDiv(localSeconds, SECONDS_PER_DAY);
        int secondOfDay = (int) Math.floorMod(localSeconds, SECONDS_PER_DAY);
        long zeroDay = epochDay + DAYS_0000_TO_1970 - 60;
        long adjust = 0;
        if (zeroDay < 0) {
            long adjustCycles = (zeroDay + 1) / 146_097 - 1;
            adjust = adjustCycles * 400;
            zeroDay += -adjustCycles * 146_097;
        }
        long yearEstimate = (400 * zeroDay + 591) / 146_097;
        long dayOfYearEstimate = zeroDay - (365 * yearEstimate + yearEstimate / 4 - yearEstimate / 100 + yearEstimate / 400);
        if (dayOfYearEstimate < 0) {
            yearEstimate--;
            dayOfYearEstimate = zeroDay - (365 * yearEstimate + yearEstimate / 4 - yearEstimate / 100 + yearEstimate / 400);
        }
        int marchDayOfYear = (int) dayOfYearEstimate;
        int marchMonth = (marchDayOfYear * 5 + 2) / 153;
        int month = (marchMonth + 2) % 12 + 1;
        int day = marchDayOfYear - (marchMonth * 306 + 5) / 10 + 1;
        long year = yearEstimate + adjust + marchMonth / 10;
        int hour = secondOfDay / 3_600;
        int minute = secondOfDay / 60 % 60;
        int second = secondOfDay % 60;
        int millisecondsAfterSecond = Math.floorMod(systemMillis, 1_000);

        writeYear(target, year);
        target.character('-');
        writePadded(target, month, 2);
        target.character('-');
        writePadded(target, day, 2);
        target.character('T');
        writePadded(target, hour, 2);
        target.character(':');
        writePadded(target, minute, 2);
        target.character(':');
        writePadded(target, second, 2);
        target.character('.');
        writePadded(target, millisecondsAfterSecond, 3);
        if (utc) {
            target.character('Z');
        } else {
            int absoluteOffset = utcOffsetSeconds < 0 ? -utcOffsetSeconds : utcOffsetSeconds;
            target.character(utcOffsetSeconds < 0 ? '-' : '+');
            writePadded(target, absoluteOffset / 3_600, 2);
            target.character(':');
            writePadded(target, absoluteOffset / 60 % 60, 2);
        }
    }

    /// Writes the rounded uptime representation used by the normal decoration path.
    private static void writeRoundedUptime(Log target, long uptimeNanos) {
        long milliseconds = uptimeNanos / 1_000_000;
        if (uptimeNanos % 1_000_000 >= 500_000) {
            milliseconds++;
        }
        long seconds = milliseconds / 1_000;
        target.unsigned(seconds).character('.');
        writePadded(target, milliseconds % 1_000, 3);
        target.character('s');
    }

    /// Writes an unsigned value with at least `width` decimal digits.
    private static void writePadded(Log target, long value, int width) {
        int digits = decimalLength(value);
        for (int index = digits; index < width; index++) {
            target.character('0');
        }
        target.unsigned(value);
    }

    /// Writes a proleptic Gregorian year using the ISO minimum width and sign rules.
    private static void writeYear(Log target, long year) {
        if (year < 0) {
            target.character('-');
            writePadded(target, -year, 4);

        } else {
            writePadded(target, year, 4);
        }
    }

    /// Returns the decimal width of `value`, including its sign when present.
    private static int decimalLength(long value) {
        int result = value < 0 ? 2 : 1;
        long remaining = value;
        while (remaining <= -10 || remaining >= 10) {
            remaining /= 10;
            result++;
        }
        return result;
    }
}
