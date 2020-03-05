/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.logging;

import java.util.Arrays;

import com.oracle.svm.core.jdk.jfr.JfrOptions;
import com.oracle.svm.core.thread.VMOperation;

public class JfrLogger {

    public enum Level {
        // Numbers assigned to be the same as jdk.jfr.internal.LogLevel
        TRACE("trace", 1),
        DEBUG("debug", 2),
        INFO("info", 3),
        WARNING("warning", 4),
        ERROR("error", 5);

        public String lv;
        public int id;

        Level(String lv, int id) {
            this.lv = lv;
            this.id = id;
        }
    }

    public static boolean shouldLog(int tagSetId, int level) {
        return !VMOperation.isInProgressAtSafepoint() && level >= JfrOptions.getLogLevel();
    }

    public static void log(int tagSetId, JfrLogger.Level level, String message) {
        if (shouldLog(tagSetId, level.id)) {
            System.err.println("[" + level.lv + "] " + "[" + tagSetId + "]  " + message);
        }
    }

    public static void log(int tagSetId, int level, String message) {
        if (shouldLog(tagSetId, level)) {
            if (1 <= level && level <= 5) {
                // Subtract one when indexing to align to the correct Level in the array
                System.err.println("[" + Level.values()[level - 1].lv + "] " + "[" + tagSetId + "]  " + message);
            }
        }
    }

    public static void logError(Object... obj) {
        log(0, JfrLogger.Level.ERROR, buildLogString(obj));
    }

    public static void logWarning(Object... obj) {
        log(0, Level.WARNING, buildLogString(obj));
    }

    public static void logInfo(Object... obj) {
        log(0, Level.INFO, buildLogString(obj));
    }

    public static void logDebug(Object... obj) {
        log(0, Level.DEBUG, buildLogString(obj));
    }

    public static void logTrace(Object... obj) {
        log(0, Level.TRACE, buildLogString(obj));
    }

    private static String buildLogString(Object... obj) {
        StringBuilder b = new StringBuilder(obj[0] != null ? obj[0].toString() : "null");
        for (int i = 1; i < obj.length; i++) {
            b.append(" ");
            b.append(obj[i] != null ? obj[i].toString() : "null");
        }
        b.append(System.lineSeparator());

        return b.toString();
    }

    public static void logStackTrace(Exception e) {
        JfrLogger.logError(Arrays.toString(e.getStackTrace()));
    }
}
