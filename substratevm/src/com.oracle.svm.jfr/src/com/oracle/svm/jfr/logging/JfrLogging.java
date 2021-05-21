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

import com.oracle.svm.core.log.Log;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.NoSuchElementException;

public class JfrLogging {
    private final JfrLogConfiguration configuration;
    private int levelDecorationFill = 0;
    private int tagSetDecorationFill = 0;


    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrLogging() {
        configuration = new JfrLogConfiguration();
    }

    public void parseConfiguration(String config) {
        configuration.parse(config);
    }

    public void log(int tagSetId, int level, String message) {
        Log log = Log.log();
        logDecorations(log, tagSetId, level);
        log.spaces(1).string(message).newline();
    }

    private void logDecorations(Log log, int tagSetId, int level) {
        String levelDecoration = getLogLevel(level).toString().toLowerCase();
        String tagSetDecoration = JfrLogConfiguration.getTagSetTags().get(getLogTagSet(tagSetId))
            .toString().toLowerCase().replaceAll("\\s", "");
        tagSetDecoration = tagSetDecoration.substring(1, tagSetDecoration.length() - 1);

        if (levelDecoration.length() > levelDecorationFill) {
            levelDecorationFill = levelDecoration.length();
        }
        if (tagSetDecoration.length() > tagSetDecorationFill) {
            tagSetDecorationFill = tagSetDecoration.length();
        }

        log.character('[');
        log.string(levelDecoration, levelDecorationFill, Log.LEFT_ALIGN);
        log.string("][");
        log.string(tagSetDecoration, tagSetDecorationFill, Log.LEFT_ALIGN);
        log.character(']');
    }

    private static JfrLogLevel getLogLevel(int level) {
        for (JfrLogLevel jfrLogLevel : JfrLogLevel.values()) {
            if (jfrLogLevel.level == level) {
                return jfrLogLevel;
            }
        }
        throw new NoSuchElementException();
    }

    private static Target_jdk_jfr_internal_LogTag getLogTagSet(int tagSetId) {
        for (Target_jdk_jfr_internal_LogTag tagSet : Target_jdk_jfr_internal_LogTag.values()) {
            if (tagSet.id == tagSetId) {
                return tagSet;
            }
        }
        throw new NoSuchElementException();
    }

}
