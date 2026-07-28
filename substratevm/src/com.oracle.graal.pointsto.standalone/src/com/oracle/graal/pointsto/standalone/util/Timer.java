/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.util;

import java.util.Objects;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.standalone.StandaloneHost;

/**
 * Simple scope timer that also prints standalone reachability counters when an analysis universe is
 * available.
 */
public class Timer implements AutoCloseable {
    private static final String HEAP_STATE = "STANDALONE";
    private final String name;
    private final String analysisName;
    private final AnalysisUniverse universe;
    private final StandaloneHost host;
    /**
     * Timer start time in nanoseconds.
     */
    private final long startTime;
    /**
     * Timer total time in nanoseconds.
     */
    private long totalTime;

    /**
     * Starts a new named timer for the current standalone analysis run.
     */
    public Timer(String name, String analysisName, AnalysisUniverse universe, StandaloneHost host) {
        this.name = name;
        this.analysisName = analysisName;
        this.universe = Objects.requireNonNull(universe);
        this.host = Objects.requireNonNull(host);
        startTime = System.nanoTime();
    }

    @Override
    public void close() {
        totalTime = System.nanoTime() - startTime;
        long totalMemory = Runtime.getRuntime().totalMemory();
        double totalMemoryGB = totalMemory / 1024.0 / 1024.0 / 1024.0;
        System.out.format("[%s]%25s: %,10.2f ms, %,5.2f GB%s%n", analysisName, name, totalTime / 1000000d, totalMemoryGB, formatAnalysisStats());
        if (host.shouldPrintClassInitializationFailures()) {
            printClassInitializationFailures();
        }
    }

    /**
     * Prints the end-of-analysis summary for build-time class-initialization failures that fell
     * back to runtime handling.
     */
    private void printClassInitializationFailures() {
        int failureCount = host.getClassInitializationFailureCount();
        int failureTypeCount = host.getClassInitializationFailureTypeCount();
        System.out.format("[%s]%25s: %,d failed attempt(s) across %,d class(es)%n", analysisName, "class init", failureCount, failureTypeCount);
        if (failureCount == 0) {
            return;
        }
        String prefix = "[" + analysisName + "]" + " ".repeat(25) + ": ";
        for (String line : host.formatClassInitializationFailures().split("\\R", -1)) {
            System.out.println(prefix + line);
        }
    }

    private String formatAnalysisStats() {
        int reachableClasses = universe.getReachableTypes();
        int totalClasses = universe.getTypes().size();
        long reachableMethods = universe.getMethods().stream().filter(AnalysisMethod::isReachable).count();
        int totalMethods = universe.getMethods().size();
        long reachableFields = universe.getFields().stream().filter(AnalysisField::isAccessed).count();
        int totalFields = universe.getFields().size();
        return String.format(", heap: %s, classes: %,d/%,d, methods: %,d/%,d, fields: %,d/%,d",
                        HEAP_STATE,
                        reachableClasses, totalClasses,
                        reachableMethods, totalMethods,
                        reachableFields, totalFields);
    }
}
