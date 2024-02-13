/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Red Hat Inc. All rights reserved.
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

import java.util.Locale;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.util.ReflectionUtil;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;

public class JfrLogging {
    private final String[] logLevels;
    private final String[] logTagSets;
    private int levelDecorationFill = 0;
    private int tagSetDecorationFill = 0;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrLogging() {
        logLevels = createLogLevels();
        logTagSets = createLogTagSets();
    }

    public void parseConfiguration(String config) {
        JfrLogConfiguration.parse(config);
    }

    public void warnInternal(String message) {
        int tagSetId = SubstrateUtil.cast(LogTag.JFR_SYSTEM, Target_jdk_jfr_internal_LogTag.class).id;
        log(tagSetId, JfrLogConfiguration.JfrLogLevel.WARNING.level, message);
    }

    public void log(int tagSetId, int level, String message) {
        if (message == null) {
            return;
        }
        verifyLogLevel(level);
        verifyLogTagSetId(tagSetId);

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

    public void logEvent(int level, String[] lines, boolean system) {
        if (lines == null) {
            return;
        }
        verifyLogLevel(level);

        LogTag logTag = system ? LogTag.JFR_SYSTEM_EVENT : LogTag.JFR_EVENT;
        int tagSetId = SubstrateUtil.cast(logTag, Target_jdk_jfr_internal_LogTag.class).id;
        for (String line : lines) {
            log(tagSetId, level, line);
        }
    }

    private void verifyLogLevel(int level) {
        if (level < 0 || level >= logLevels.length || logLevels[level] == null) {
            throw new IllegalArgumentException("LogLevel passed is outside valid range");
        }
    }

    private void verifyLogTagSetId(int tagSetId) {
        if (tagSetId < 0 || tagSetId >= logTagSets.length) {
            throw new IllegalArgumentException("LogTagSet id is outside valid range");
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String[] createLogLevels() {
        LogLevel[] values = LogLevel.values();
        String[] result = new String[getMaxLogLevel(values) + 1];
        for (LogLevel logLevel : values) {
            result[getLevel(logLevel)] = logLevel.toString().toLowerCase(Locale.ROOT);
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getMaxLogLevel(LogLevel[] values) {
        int result = 0;
        for (LogLevel logLevel : values) {
            result = Math.max(result, getLevel(logLevel));
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String[] createLogTagSets() {
        LogTag[] values = LogTag.values();
        String[] result = new String[getMaxLogTagSetId(values) + 1];
        for (LogTag logTagSet : values) {
            StringBuilder builder = new StringBuilder();
            Set<JfrLogTag> set = JfrLogConfiguration.LOG_TAG_SETS.get(logTagSet);
            if (set != null) {
                for (JfrLogTag logTag : set) {
                    if (builder.length() > 0) {
                        builder.append(",");
                    }
                    builder.append(logTag.toString().toLowerCase(Locale.ROOT));
                }
                result[getId(logTagSet)] = builder.toString();
            }
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getMaxLogTagSetId(LogTag[] values) {
        int result = 0;
        for (LogTag logTagSet : values) {
            result = Math.max(result, getId(logTagSet));
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int getLevel(LogLevel logLevel) {
        return ReflectionUtil.readField(LogLevel.class, "level", logLevel);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int getId(LogTag logTag) {
        return ReflectionUtil.readField(LogTag.class, "id", logTag);
    }
}
