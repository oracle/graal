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
package com.oracle.max.graal.compiler;

import java.util.*;
import java.util.Map.*;

import com.oracle.max.graal.compiler.debug.*;

/**
 * This class contains timers that record the amount of time spent in various
 * parts of the compiler.
 */
public final class GraalTimers {
    private static LinkedHashMap<String, GraalTimers> map = new LinkedHashMap<String, GraalTimers>();

    public static final GraalTimers COMPUTE_LINEAR_SCAN_ORDER = get("Compute Linear Scan Order");
    public static final GraalTimers LIR_CREATE = get("Create LIR");
    public static final GraalTimers LIFETIME_ANALYSIS = get("Lifetime Analysis");
    public static final GraalTimers LINEAR_SCAN = get("Linear Scan");
    public static final GraalTimers RESOLUTION = get("Resolution");
    public static final GraalTimers DEBUG_INFO = get("Create Debug Info");
    public static final GraalTimers CODE_CREATE = get("Create Code");

    private final String name;
    private long start;
    private long total;

    private GraalTimers(String name) {
        this.name = name;
    }


    public static GraalTimers get(String name) {
        if (!map.containsKey(name)) {
            map.put(name, new GraalTimers(name));
        }
        return map.get(name);
    }

    public void start() {
        start = System.nanoTime();
    }

    public void stop() {
        total += System.nanoTime() - start;
    }

    public static void reset() {
        for (Entry<String, GraalTimers> e : map.entrySet()) {
            e.getValue().total = 0;
        }
    }

    public static void print() {
        long total = 0;
        for (Entry<String, GraalTimers> e : map.entrySet()) {
            total += e.getValue().total;
        }
        if (total == 0) {
            return;
        }

        TTY.println();
        for (Entry<String, GraalTimers> e : map.entrySet()) {
            GraalTimers timer = e.getValue();
            TTY.println("%-30s: %7.4f s (%5.2f%%)", timer.name, timer.total / 1000000000.0, timer.total * 100.0 / total);
            timer.total = 0;
        }
        TTY.println();
    }
}
