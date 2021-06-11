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
package com.oracle.svm.jfr.logging;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.UserError;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;

/**
 * Parses the flight recorder logging configuration and enables the logging according to that
 * configuration.
 */
class JfrLogConfiguration {
    private static final String EMPTY_STRING_DEFAULT_CONFIG = "all=info";
    static final Map<LogTag, Set<JfrLogTag>> LOG_TAG_SETS = createLogTagSets();

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrLogConfiguration() {
    }

    void parse(String str) {
        if (str.equalsIgnoreCase("disable")) {
            return;
        }

        String config;
        if (str.isEmpty()) {
            config = EMPTY_STRING_DEFAULT_CONFIG;
        } else {
            config = str;
        }

        String[] splitConfig = config.split(",");
        JfrLogSelection[] selections = new JfrLogSelection[splitConfig.length];

        int index = 0;
        for (String s : splitConfig) {
            selections[index++] = JfrLogSelection.parse(s);
        }
        setLogTagSetLevels(selections);
        verifySelections(selections);
    }

    private static void setLogTagSetLevels(JfrLogSelection[] selections) {
        LogTag[] values = LogTag.values();
        for (LogTag logTagSet : values) {
            JfrLogLevel logLevel = JfrLogLevel.WARNING;
            for (JfrLogSelection selection : selections) {
                if ((selection.wildcard && LOG_TAG_SETS.get(logTagSet).containsAll(selection.tags)) || (selection.tags.equals(LOG_TAG_SETS.get(logTagSet)))) {
                    logLevel = selection.level;
                    selection.matchesATagSet = true;
                }
            }
            SubstrateUtil.cast(logTagSet, Target_jdk_jfr_internal_LogTag.class).tagSetLevel = logLevel.level;
        }
    }

    private static void verifySelections(JfrLogSelection[] selections) {
        for (JfrLogSelection selection : selections) {
            if (!selection.matchesATagSet) {
                throw UserError.abort("No tag set matches tag combination %s for FlightRecorderLogging",
                                selection.tags.toString().toLowerCase() + (selection.wildcard ? "*" : ""));
            }
        }
    }

    private static Map<LogTag, Set<JfrLogTag>> createLogTagSets() {
        Map<LogTag, Set<JfrLogTag>> result = new EnumMap<>(LogTag.class);
        result.put(LogTag.JFR, EnumSet.of(JfrLogTag.JFR));
        result.put(LogTag.JFR_SYSTEM, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM));
        result.put(LogTag.JFR_SYSTEM_EVENT, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.EVENT));
        result.put(LogTag.JFR_SYSTEM_SETTING, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.SETTING));
        result.put(LogTag.JFR_SYSTEM_BYTECODE, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.BYTECODE));
        result.put(LogTag.JFR_SYSTEM_PARSER, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.PARSER));
        result.put(LogTag.JFR_SYSTEM_METADATA, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.METADATA));
        result.put(LogTag.JFR_METADATA, EnumSet.of(JfrLogTag.JFR, JfrLogTag.METADATA));
        result.put(LogTag.JFR_EVENT, EnumSet.of(JfrLogTag.JFR, JfrLogTag.EVENT));
        result.put(LogTag.JFR_SETTING, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SETTING));
        result.put(LogTag.JFR_DCMD, EnumSet.of(JfrLogTag.JFR, JfrLogTag.DCMD));
        return result;
    }

    private static class JfrLogSelection {
        private final Set<JfrLogTag> tags;
        private final JfrLogLevel level;
        private final boolean wildcard;
        private boolean matchesATagSet;

        JfrLogSelection(Set<JfrLogTag> tags, JfrLogLevel level, boolean wildcard) {
            this.tags = tags;
            this.level = level;
            this.wildcard = wildcard;
            this.matchesATagSet = false;
        }

        private static JfrLogSelection parse(String str) {
            Set<JfrLogTag> tags = EnumSet.noneOf(JfrLogTag.class);
            JfrLogLevel level = JfrLogLevel.INFO;
            boolean wildcard = false;

            String tagsStr;
            int equalsIndex;
            if ((equalsIndex = str.indexOf('=')) > 0) {
                String value = str.substring(equalsIndex + 1);
                try {
                    level = JfrLogLevel.valueOf(value.toUpperCase());
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw UserError.abort(e, "Invalid log level '%s' for FlightRecorderLogging.", value);
                }
                tagsStr = str.substring(0, equalsIndex);
            } else {
                tagsStr = str;
            }

            if (tagsStr.equalsIgnoreCase("all")) {
                return new JfrLogSelection(tags, level, true);
            }

            if (tagsStr.endsWith("*")) {
                wildcard = true;
                tagsStr = tagsStr.substring(0, tagsStr.length() - 1);
            }

            for (String s : tagsStr.split("\\+")) {
                try {
                    tags.add(JfrLogTag.valueOf(s.toUpperCase()));
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw UserError.abort(e, "Invalid log tag '%s' for FlightRecorderLogging.", s);
                }
            }
            return new JfrLogSelection(tags, level, wildcard);
        }
    }

    public enum JfrLogLevel {
        TRACE(JfrLogging.getLevel(LogLevel.TRACE)),
        DEBUG(JfrLogging.getLevel(LogLevel.DEBUG)),
        INFO(JfrLogging.getLevel(LogLevel.INFO)),
        WARNING(JfrLogging.getLevel(LogLevel.WARN)),
        ERROR(JfrLogging.getLevel(LogLevel.ERROR)),
        OFF(100);

        final int level;

        JfrLogLevel(int level) {
            this.level = level;
        }
    }
}
