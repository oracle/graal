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

import com.oracle.svm.core.util.UserError;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.jfr.internal.LogTag;

import java.util.Map;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Handles configuration for flight recorder logging.
 */
class JfrLogConfiguration {
    private static final String EMPTY_STRING_DEFAULT_CONFIG = "all=info";
    private static final Map<LogTag, Set<JfrLogTag>> tagSetTags = new EnumMap<LogTag, Set<JfrLogTag>>(LogTag.class);

    private JfrLogSelection[] selections;

    static {
        tagSetTags.put(LogTag.JFR, EnumSet.of(JfrLogTag.JFR));
        tagSetTags.put(LogTag.JFR_SYSTEM, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM));
        tagSetTags.put(LogTag.JFR_SYSTEM_EVENT, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.EVENT));
        tagSetTags.put(LogTag.JFR_SYSTEM_SETTING, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.SETTING));
        tagSetTags.put(LogTag.JFR_SYSTEM_BYTECODE, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.BYTECODE));
        tagSetTags.put(LogTag.JFR_SYSTEM_PARSER, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.PARSER));
        tagSetTags.put(LogTag.JFR_SYSTEM_METADATA, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SYSTEM, JfrLogTag.METADATA));
        tagSetTags.put(LogTag.JFR_METADATA, EnumSet.of(JfrLogTag.JFR, JfrLogTag.METADATA));
        tagSetTags.put(LogTag.JFR_EVENT, EnumSet.of(JfrLogTag.JFR, JfrLogTag.EVENT));
        tagSetTags.put(LogTag.JFR_SETTING, EnumSet.of(JfrLogTag.JFR, JfrLogTag.SETTING));
        tagSetTags.put(LogTag.JFR_DCMD, EnumSet.of(JfrLogTag.JFR, JfrLogTag.DCMD));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrLogConfiguration() {
    }

    static Map<LogTag, Set<JfrLogTag>> getTagSetTags() {
        return tagSetTags;
    }

    void parse(String str) {
        // See LogTag#tagSetLevel.
        // Should initially be set to 100, but is automatically set to 4 during image build time.
        for (Target_jdk_jfr_internal_LogTag tagSet : Target_jdk_jfr_internal_LogTag.values()) {
            tagSet.tagSetLevel = 100;
        }

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
        selections = new JfrLogSelection[splitConfig.length];

        int index = 0;
        for (String s : splitConfig) {
            selections[index++] = JfrLogSelection.parse(s);
        }
        setLogTagSetLevels();
        verifySelections();
    }

    private void setLogTagSetLevels() {
        for (LogTag tagSet : LogTag.values()) {
            JfrLogLevel jfrLogLevel = JfrLogLevel.WARNING;
            for (JfrLogSelection selection : selections) {
                if ((selection.wildcard && tagSetTags.get(tagSet).containsAll(selection.tags))
                        || (selection.tags.equals(tagSetTags.get(tagSet)))) {
                    jfrLogLevel = selection.level;
                    selection.matchesATagSet = true;
                        }
            }
            Target_jdk_jfr_internal_LogTag.values()[tagSet.ordinal()].tagSetLevel = jfrLogLevel.level;
        }
    }

    private void verifySelections() {
        for (JfrLogSelection selection : selections) {
            if (!selection.matchesATagSet) {
                throw UserError.abort("No tag set matches tag combination %s for FlightRecorderLogging",
                        selection.tags.toString().toLowerCase() + (selection.wildcard ? "*" : ""));
            }
        }
    }

    private static class JfrLogSelection {
        private final Set<JfrLogTag> tags;
        private final JfrLogLevel level;
        private final boolean wildcard;
        private boolean matchesATagSet = false;

        JfrLogSelection(Set<JfrLogTag> tags, JfrLogLevel level, boolean wildcard) {
            this.tags = tags;
            this.level = level;
            this.wildcard = wildcard;
        }

        private static JfrLogSelection parse(String str) {
            Set<JfrLogTag> tags = EnumSet.noneOf(JfrLogTag.class);
            JfrLogLevel level = JfrLogLevel.INFO;
            boolean wildcard = false;

            String tagsStr;
            int equalsIndex;
            if ((equalsIndex = str.indexOf('=')) > 0) {
                try {
                    level = JfrLogLevel.valueOf(str.substring(equalsIndex + 1).toUpperCase());
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw UserError.abort(e, "Invalid log level '%s' for FlightRecorderLogging.",
                            str.substring(equalsIndex + 1));
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
}
