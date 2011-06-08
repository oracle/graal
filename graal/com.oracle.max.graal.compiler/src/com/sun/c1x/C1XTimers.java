/*
 * Copyright (c) 2009, 2009, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x;

import com.sun.c1x.debug.*;

/**
 * This class contains timers that record the amount of time spent in various
 * parts of the compiler.
 *
 * @author Christian Wimmer
 */
public enum C1XTimers {
    HIR_CREATE("Create HIR"),
    HIR_OPTIMIZE("Optimize HIR"),
    NCE("Nullcheck elimination"),
    LIR_CREATE("Create LIR"),
    LIFETIME_ANALYSIS("Lifetime Analysis"),
    LINEAR_SCAN("Linear Scan"),
    RESOLUTION("Resolution"),
    DEBUG_INFO("Create Debug Info"),
    CODE_CREATE("Create Code");

    private final String name;
    private long start;
    private long total;

    private C1XTimers(String name) {
        this.name = name;
    }

    public void start() {
        start = System.nanoTime();
    }

    public void stop() {
        total += System.nanoTime() - start;
    }

    public static void reset() {
        for (C1XTimers t : values()) {
            t.total = 0;
        }
    }

    public static void print() {
        long total = 0;
        for (C1XTimers timer : C1XTimers.values()) {
            total += timer.total;
        }
        if (total == 0) {
            return;
        }

        TTY.println();
        for (C1XTimers timer : C1XTimers.values()) {
            TTY.println("%-20s: %7.4f s (%5.2f%%)", timer.name, timer.total / 1000000000.0, timer.total * 100.0 / total);
            timer.total = 0;
        }
        TTY.println();
    }
}
