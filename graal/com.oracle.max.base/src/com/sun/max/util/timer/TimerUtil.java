/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util.timer;

import com.sun.max.profile.*;

/**
 * This class offers various utilities for composing and using timers.
 */
public class TimerUtil {
    public static void time(Runnable runnable, Timer timer) {
        timer.start();
        runnable.run();
        timer.stop();
    }

    public static long timeElapsed(Runnable runnable, Timer timer) {
        timer.start();
        runnable.run();
        timer.stop();
        return timer.getLastElapsedTime();
    }

    public static long timeElapsed(Runnable runnable, Clock clock) {
        return timeElapsed(runnable, new SingleUseTimer(clock));
    }

    public static long getLastElapsedSeconds(Timer timer) {
        return (1 * timer.getLastElapsedTime()) / timer.getClock().getHZ();
    }

    public static long getLastElapsedMilliSeconds(Timer timer) {
        return (1000 * timer.getLastElapsedTime()) / timer.getClock().getHZ();
    }

    public static long getLastElapsedMicroSeconds(Timer timer) {
        return (1000000 * timer.getLastElapsedTime()) / timer.getClock().getHZ();
    }

    public static long getLastElapsedNanoSeconds(Timer timer) {
        return (1000000000 * timer.getLastElapsedTime()) / timer.getClock().getHZ();
    }

    public static String getHzSuffix(Clock clock) {
        if (clock.getHZ() == 1000) {
            return "ms";
        } else if (clock.getHZ() == 1000000) {
            return "us";
        } else if (clock.getHZ() == 1000000000) {
            return "ns";
        }
        return "(" + clock.getHZ() + "/sec)";
    }
}
