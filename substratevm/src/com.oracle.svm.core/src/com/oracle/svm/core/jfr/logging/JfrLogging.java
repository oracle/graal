/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Red Hat Inc. All rights reserved.
 * Copyright (c) 2025, 2025, IBM Inc. All rights reserved.
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
package com.oracle.svm.core.jfr.logging;

import static com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import java.util.Locale;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import com.oracle.svm.core.logging.HasXlogSupport;
import com.oracle.svm.core.logging.jfr.JfrUnifiedLogging;

import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;

/// Routes JFR records through unified logging, with a low-level fallback when `-Xlog` is unavailable
/// or native allocation is forbidden.
public class JfrLogging {

    /// Legacy level decorations indexed by the JDK numeric level.
    private final String[] logLevels;

    /// Legacy tag decorations indexed by the JDK tag set identifier.
    private final String[] logTagSets;

    /// JDK tag instances indexed by their tag set identifiers.
    private final LogTag[] logTags;

    /// Unified tag set mapping for JFR records.
    private final JfrUnifiedLogging unifiedLogging;

    private int levelDecorationFill;
    private int tagSetDecorationFill;

    /// Creates the image-heap state for JFR logging.
    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrLogging() {
        logLevels = createLogLevels();
        logTagSets = createLogTagSets();
        logTags = createLogTags();
        unifiedLogging = new JfrUnifiedLogging();
    }

    /// Applies `FlightRecorderLogging` to the active logging implementation.
    public void parseConfiguration(String config) {
        JfrLogConfiguration.parse(config, this);
    }

    /// Writes a JFR system error through the low-level sink, subject to the published JFR threshold.
    /// Unified logging is always bypassed because native allocation is forbidden during emergency
    /// dumps.
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "May be used during OOME emergency dump.")
    public void logJfrSystemError(String message) {
        logLegacyIfEnabled(tagSetId(LogTag.JFR_SYSTEM), JfrLogConfiguration.JfrLogLevel.ERROR.level, message);
    }

    /// Writes a JFR informational message through the low-level sink, subject to the published JFR
    /// threshold. Unified logging is always bypassed because native allocation is forbidden during
    /// emergency dumps.
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "May be used during OOME emergency dump.")
    public void logJfrInfo(String message) {
        logLegacyIfEnabled(tagSetId(LogTag.JFR), JfrLogConfiguration.JfrLogLevel.INFO.level, message);
    }

    /// Writes a JFR warning through the low-level sink, subject to the published JFR threshold.
    /// Unified logging is always bypassed because native allocation is forbidden during emergency
    /// dumps.
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "May be used during OOME emergency dump.")
    public void logJfrWarning(String message) {
        logLegacyIfEnabled(tagSetId(LogTag.JFR), JfrLogConfiguration.JfrLogLevel.WARNING.level, message);
    }

    /// Writes a JFR setting warning through the active sink.
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "May be used during OOME emergency dump.")
    public void logJfrSettingWarning(String message) {
        if (HasXlogSupport.get()) {
            JfrUnifiedLogging.logJfrSettingWarning(message);
        } else {
            logLegacyIfEnabled(tagSetId(LogTag.JFR_SETTING), JfrLogConfiguration.JfrLogLevel.WARNING.level, message);
        }
    }

    /// Routes one JFR record through the active sink.
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "May be used during OOME emergency dump.")
    public void log(int tagSetId, int level, String message) {
        if (message == null) {
            return;
        }
        verifyLogLevel(level);
        verifyLogTagSetId(tagSetId);

        if (HasXlogSupport.get()) {
            unifiedLogging.log(tagSetId, level, message);
        } else {
            logLegacyIfEnabled(tagSetId, level, message);
        }
    }

    /// Routes one multiline JFR event through the active sink.
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "May be used during OOME emergency dump.")
    public void logEvent(int level, String[] lines, boolean system) {
        if (lines == null) {
            return;
        }
        verifyLogLevel(level);

        int eventTagSetId = tagSetId(LogTag.JFR_EVENT);
        int systemEventTagSetId = tagSetId(LogTag.JFR_SYSTEM_EVENT);
        int tagSetId = system ? systemEventTagSetId : eventTagSetId;
        if (HasXlogSupport.get()) {
            JfrUnifiedLogging.logEvent(level, lines, system);
        } else if (legacyLevelEnables(tagSetId, level)) {
            for (String line : lines) {
                if (line != null) {
                    logLegacy(tagSetId, level, line);
                }
            }
        }
    }

    /// Publishes unified logging thresholds to JDK JFR.
    public void updateLogLevels() {
        for (int tagSetId = 0; tagSetId < logTags.length; tagSetId++) {
            LogTag logTag = logTags[tagSetId];
            if (logTag != null) {
                Target_jdk_jfr_internal_LogTag target = SubstrateUtil.cast(logTag, Target_jdk_jfr_internal_LogTag.class);
                int newLevel = unifiedLogging.levelFor(tagSetId);
                if (target.tagSetLevel != newLevel) {
                    target.tagSetLevel = newLevel;
                }
            }
        }
    }

    /// Records the legacy threshold selected for `logTag`.
    void setLegacyLevel(LogTag logTag, int level) {
        SubstrateUtil.cast(logTag, Target_jdk_jfr_internal_LogTag.class).tagSetLevel = level;
    }

    private void logLegacy(int tagSetId, int level, String message) {
        String levelDecoration = logLevels[level];
        String tagSetDecoration = logTagSets[tagSetId];

        if (levelDecoration.length() > levelDecorationFill) {
            levelDecorationFill = levelDecoration.length();
        }
        if (tagSetDecoration.length() > tagSetDecorationFill) {
            tagSetDecorationFill = tagSetDecoration.length();
        }

        Log log = Log.log();
        log.string("[");
        log.string(levelDecoration, levelDecorationFill, Log.LEFT_ALIGN);
        log.string("][");
        log.string(tagSetDecoration, tagSetDecorationFill, Log.LEFT_ALIGN);
        log.string("] ");
        log.string(message).newline();
    }

    private void logLegacyIfEnabled(int tagSetId, int level, String message) {
        if (message != null && legacyLevelEnables(tagSetId, level)) {
            logLegacy(tagSetId, level, message);
        }
    }

    private boolean legacyLevelEnables(int tagSetId, int level) {
        return level >= SubstrateUtil.cast(logTags[tagSetId], Target_jdk_jfr_internal_LogTag.class).tagSetLevel;
    }

    private void verifyLogLevel(int level) {
        if (level < 0 || level >= logLevels.length || logLevels[level] == null) {
            throw JfrUnifiedLogging.verifyLogLevelException;
        }
    }

    private void verifyLogTagSetId(int tagSetId) {
        if (tagSetId < 0 || tagSetId >= logTagSets.length || logTagSets[tagSetId] == null) {
            throw JfrUnifiedLogging.verifyLogTagSetIdException;
        }
    }

    /// Creates the legacy level decorations indexed by JDK numeric level.
    @Platforms(Platform.HOSTED_ONLY.class)
    private static String[] createLogLevels() {
        LogLevel[] values = LogLevel.values();
        String[] result = new String[getMaxLogLevel(values) + 1];
        for (LogLevel logLevel : values) {
            result[getLevel(logLevel)] = logLevel.toString().toLowerCase(Locale.ROOT);
        }
        return result;
    }

    /// Returns the largest numeric level in `values`.
    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getMaxLogLevel(LogLevel[] values) {
        int result = 0;
        for (LogLevel logLevel : values) {
            result = Math.max(result, getLevel(logLevel));
        }
        return result;
    }

    /// Creates the legacy tag decorations indexed by JDK tag set identifier.
    @Platforms(Platform.HOSTED_ONLY.class)
    private static String[] createLogTagSets() {
        LogTag[] values = LogTag.values();
        String[] result = new String[getMaxLogTagSetId(values) + 1];
        for (LogTag logTagSet : values) {
            StringBuilder builder = new StringBuilder();
            Set<JfrLogTag> set = JfrLogConfiguration.LOG_TAG_SETS.get(logTagSet);
            if (set != null) {
                for (JfrLogTag logTag : set) {
                    if (!builder.isEmpty()) {
                        builder.append(",");
                    }
                    builder.append(logTag.toString().toLowerCase(Locale.ROOT));
                }
                result[getId(logTagSet)] = builder.toString();
            }
        }
        return result;
    }

    /// Creates the JDK tag lookup indexed by tag set identifier.
    @Platforms(Platform.HOSTED_ONLY.class)
    private static LogTag[] createLogTags() {
        LogTag[] values = LogTag.values();
        LogTag[] result = new LogTag[getMaxLogTagSetId(values) + 1];
        for (LogTag logTag : values) {
            result[getId(logTag)] = logTag;
        }
        return result;
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

    /// Reads the numeric level assigned to `logLevel` by JDK JFR.
    @Platforms(Platform.HOSTED_ONLY.class)
    public static int getLevel(LogLevel logLevel) {
        return ReflectionUtil.readField(LogLevel.class, "level", logLevel);
    }

    /// Reads the tag set identifier assigned to `logTag` by JDK JFR.
    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getId(LogTag logTag) {
        return ReflectionUtil.readField(LogTag.class, "id", logTag);
    }

    private static int tagSetId(LogTag logTag) {
        return SubstrateUtil.cast(logTag, Target_jdk_jfr_internal_LogTag.class).id;
    }
}
