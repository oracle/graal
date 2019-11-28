/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jdwp.impl;

public final class JDWPLogger {

    public static LogLevel LEVEL;

    public enum LogLevel {
        ALL,
        THREAD,
        PACKET,
        IDS,
        STEPPING,
        NONE
    }

    public static void setupLogLevel(String level) {
        LEVEL = level != null ? LogLevel.valueOf(level) : LogLevel.NONE;
    }

    private static boolean shouldLog(LogLevel level) {
        switch (level) {
            case NONE: return false;
            case THREAD: return LEVEL == LogLevel.THREAD || LEVEL == LogLevel.ALL;
            case PACKET: return LEVEL == LogLevel.PACKET || LEVEL == LogLevel.ALL;
            case STEPPING: return LEVEL == LogLevel.STEPPING || LEVEL == LogLevel.ALL;
            case IDS: return LEVEL == LogLevel.IDS || LEVEL == LogLevel.ALL;
            case ALL:
                return  LEVEL == LogLevel.ALL ||
                        LEVEL == LogLevel.THREAD ||
                        LEVEL == LogLevel.PACKET ||
                        LEVEL == LogLevel.STEPPING ||
                        LEVEL == LogLevel.IDS;
            default: return false;
        }
    }

    public static void log(String msg, LogLevel level) {
        if (shouldLog(level)) {
            System.out.println(msg);
        }
    }
}
