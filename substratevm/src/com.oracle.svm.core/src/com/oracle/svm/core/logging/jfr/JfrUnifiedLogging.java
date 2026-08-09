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
package com.oracle.svm.core.logging.jfr;

import java.util.Locale;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.logging.HasULSupport;
import com.oracle.svm.core.logging.LogLevel;
import com.oracle.svm.core.logging.LogMessage;
import com.oracle.svm.core.logging.LogTagSet;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.jfr.internal.LogTag;

/// Routes JFR log records to the SVM unified logging framework.
public final class JfrUnifiedLogging {
    /// Numeric level used by JFR to represent a disabled tag set.
    public static final int OFF_LEVEL = 100;

    /// Preallocated exception used when JFR supplies an invalid level.
    public static final IllegalArgumentException verifyLogLevelException = new IllegalArgumentException("LogLevel passed is outside valid range");

    /// Preallocated exception used when JFR supplies an invalid tag set identifier.
    public static final IllegalArgumentException verifyLogTagSetIdException = new IllegalArgumentException("LogTagSet id is outside valid range");

    /// Unified tag set indexed by the corresponding JDK JFR tag set identifier.
    private final LogTagSet[] logTagSets;

    /// Creates the image-heap mapping when unified logging is supported.
    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrUnifiedLogging() {
        if (!HasULSupport.get()) {
            logTagSets = null;
            return;
        }
        LogTag[] values = LogTag.values();
        logTagSets = new LogTagSet[getMaxLogTagSetId(values) + 1];
        for (LogTag logTag : values) {
            logTagSets[getId(logTag)] = LogTagSet.valueOf(logTag.name().toLowerCase(Locale.ROOT));
        }
    }

    /// Writes a JFR system error when the corresponding unified tag set is enabled.
    public static void logJfrSystemError(String message) {
        if (!HasULSupport.get()) {
            return;
        }
        LogTagSet.jfr_system.log(LogLevel.ERROR, message);
    }

    /// Writes a JFR informational message when the corresponding unified tag set is enabled.
    public static void logJfrInfo(String message) {
        if (!HasULSupport.get()) {
            return;
        }
        LogTagSet.jfr.log(LogLevel.INFO, message);
    }

    /// Writes a JFR warning when the corresponding unified tag set is enabled.
    public static void logJfrWarning(String message) {
        if (!HasULSupport.get()) {
            return;
        }
        LogTagSet.jfr.log(LogLevel.WARNING, message);
    }

    /// Writes a JFR setting warning when the corresponding unified tag set is enabled.
    public static void logJfrSettingWarning(String message) {
        if (!HasULSupport.get()) {
            return;
        }
        LogTagSet.jfr_setting.log(LogLevel.WARNING, message);
    }

    /// Routes one JFR record to unified logging.
    public void log(int tagSetId, int level, String message) {
        if (message == null || !HasULSupport.get()) {
            return;
        }
        LogLevel logLevel = LogLevel.forInt(level, verifyLogLevelException);
        logTagSets[tagSetId].log(logLevel, message);
    }

    /// Routes one multiline JFR event to unified logging as a single message.
    public static void logEvent(int level, String[] lines, boolean system) {
        if (lines == null || !HasULSupport.get()) {
            return;
        }
        LogLevel logLevel = LogLevel.forInt(level, verifyLogLevelException);
        LogTagSet logTag = system ? LogTagSet.jfr_system_event : LogTagSet.jfr_event;
        if (logTag.isLevel(logLevel)) {
            LogMessage message = logTag.message();
            try {
                for (String line : lines) {
                    message.line(logLevel).string(line);
                }
            } finally {
                message.close();
            }
        }
    }

    /// Returns the numeric JFR threshold selected by unified logging for `tagSetId`.
    public int levelFor(int tagSetId) {
        if (!HasULSupport.get()) {
            return OFF_LEVEL;
        }
        return toJfrLevel(logTagSets[tagSetId].getMostDetailedLevel());
    }

    /// Returns the largest tag set identifier in `values`.
    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getMaxLogTagSetId(LogTag[] values) {
        int result = 0;
        for (LogTag logTagSet : values) {
            result = Math.max(result, getId(logTagSet));
        }
        return result;
    }

    /// Reads the JDK tag set identifier assigned to `logTag`.
    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getId(LogTag logTag) {
        return ReflectionUtil.readField(LogTag.class, "id", logTag);
    }

    /// Converts an SVM level to the numeric value used by `jdk.jfr.internal.LogLevel`.
    private static int toJfrLevel(LogLevel level) {
        // JFR uses the SVM ordinal for TRACE through ERROR and 100 to disable logging.
        return level == LogLevel.OFF ? OFF_LEVEL : level.ordinal();
    }
}
