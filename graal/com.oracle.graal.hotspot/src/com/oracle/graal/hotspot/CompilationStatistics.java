/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static java.lang.Thread.*;

import java.io.*;
import java.lang.annotation.*;
import java.lang.management.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.hotspot.meta.*;
import com.sun.management.ThreadMXBean;

@SuppressWarnings("unused")
public final class CompilationStatistics {

    private static final long RESOLUTION = 100000000;
    private static final boolean ENABLED = Boolean.getBoolean("graal.comp.stats");

    private static final CompilationStatistics DUMMY = new CompilationStatistics(null, false);

    private static ConcurrentLinkedDeque<CompilationStatistics> list = new ConcurrentLinkedDeque<>();

    private static final ThreadLocal<Deque<CompilationStatistics>> current = new ThreadLocal<Deque<CompilationStatistics>>() {

        @Override
        protected Deque<CompilationStatistics> initialValue() {
            return new ArrayDeque<>();
        }
    };

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private static @interface NotReported {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private static @interface TimeValue {
    }

    private static long zeroTime = System.nanoTime();

    private static long getThreadAllocatedBytes() {
        ThreadMXBean thread = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        return thread.getThreadAllocatedBytes(currentThread().getId());
    }

    @NotReported private final long startTime;
    @NotReported private long threadAllocatedBytesStart;

    private int bytecodeCount;
    private int codeSize;
    @TimeValue private long duration;
    private long memoryUsed;
    private final boolean osr;
    private final String holder;
    private final String name;
    private final String signature;

    private CompilationStatistics(HotSpotResolvedJavaMethod method, boolean osr) {
        this.osr = osr;
        if (method != null) {
            holder = method.getDeclaringClass().getName();
            name = method.getName();
            signature = method.getSignature().getMethodDescriptor();
            startTime = System.nanoTime();
            bytecodeCount = method.getCodeSize();
            threadAllocatedBytesStart = getThreadAllocatedBytes();
        } else {
            holder = "";
            name = "";
            signature = "";
            startTime = 0;
        }
    }

    public void finish(HotSpotResolvedJavaMethod method, HotSpotInstalledCode code) {
        if (ENABLED) {
            duration = System.nanoTime() - startTime;
            codeSize = (int) code.getCodeSize();
            memoryUsed = getThreadAllocatedBytes() - threadAllocatedBytesStart;
            if (current.get().getLast() != this) {
                throw new RuntimeException("mismatch in finish()");
            }
            current.get().removeLast();
        }
    }

    public static CompilationStatistics current() {
        return current.get().isEmpty() ? null : current.get().getLast();
    }

    public static CompilationStatistics create(HotSpotResolvedJavaMethod method, boolean isOSR) {
        if (ENABLED) {
            CompilationStatistics stats = new CompilationStatistics(method, isOSR);
            list.add(stats);
            current.get().addLast(stats);
            return stats;
        } else {
            return DUMMY;
        }
    }

    @SuppressWarnings("deprecation")
    public static void clear(String dumpName) {
        if (!ENABLED) {
            return;
        }
        try {
            ConcurrentLinkedDeque<CompilationStatistics> snapshot = list;
            long snapshotZeroTime = zeroTime;

            list = new ConcurrentLinkedDeque<>();
            zeroTime = System.nanoTime();

            Date now = new Date();
            String dateString = (now.getYear() + 1900) + "-" + (now.getMonth() + 1) + "-" + now.getDate() + "-" + now.getHours() + "" + now.getMinutes();

            dumpCompilations(snapshot, dumpName, dateString);

            try (FileOutputStream fos = new FileOutputStream("timeline_" + dateString + "_" + dumpName + ".csv", true); PrintStream out = new PrintStream(fos)) {

                long[] timeSpent = new long[10000];
                int maxTick = 0;
                for (CompilationStatistics stats : snapshot) {
                    long start = stats.startTime - snapshotZeroTime;
                    long duration = stats.duration;
                    if (start < 0) {
                        duration -= -start;
                        start = 0;
                    }

                    int tick = (int) (start / RESOLUTION);
                    long timeLeft = RESOLUTION - (start % RESOLUTION);

                    while (tick < timeSpent.length && duration > 0) {
                        if (tick > maxTick) {
                            maxTick = tick;
                        }
                        timeSpent[tick] += Math.min(timeLeft, duration);
                        duration -= timeLeft;
                        tick++;
                        timeLeft = RESOLUTION;
                    }
                }
                String timelineName = System.getProperty("stats.timeline.name");
                if (timelineName != null && !timelineName.isEmpty()) {
                    out.print(timelineName + "\t");
                }
                for (int i = 0; i <= maxTick; i++) {
                    out.print((timeSpent[i] * 100 / RESOLUTION) + "\t");
                }
                out.println();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static void dumpCompilations(ConcurrentLinkedDeque<CompilationStatistics> snapshot, String dumpName, String dateString) throws IllegalAccessException, FileNotFoundException {
        String fileName = "compilations_" + dateString + "_" + dumpName + ".csv";
        try (PrintStream out = new PrintStream(fileName)) {
            // output the list of all compilations

            Field[] declaredFields = CompilationStatistics.class.getDeclaredFields();
            ArrayList<Field> fields = new ArrayList<>();
            for (Field field : declaredFields) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isAnnotationPresent(NotReported.class)) {
                    fields.add(field);
                }
            }
            for (Field field : fields) {
                out.print(field.getName() + "\t");
            }
            out.println();
            for (CompilationStatistics stats : snapshot) {
                for (Field field : fields) {
                    if (field.isAnnotationPresent(TimeValue.class)) {
                        double value = field.getLong(stats) / 1000000d;
                        out.print(String.format(Locale.ENGLISH, "%.3f", value) + "\t");
                    } else {
                        out.print(field.get(stats) + "\t");
                    }
                }
                out.println();
            }
        }
    }
}
