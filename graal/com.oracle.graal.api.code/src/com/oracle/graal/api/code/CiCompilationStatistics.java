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
package com.oracle.graal.api.code;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.meta.*;

@SuppressWarnings("unused")
public final class CiCompilationStatistics {

    private static final long RESOLUTION = 100000000;
    private static final boolean TIMELINE_ENABLED = System.getProperty("stats.timeline.file") != null;
    private static final boolean COMPILATIONSTATS_ENABLED = System.getProperty("stats.compilations.file") != null;
    private static final boolean ENABLED = TIMELINE_ENABLED || COMPILATIONSTATS_ENABLED;

    private static final CiCompilationStatistics DUMMY = new CiCompilationStatistics(null);

    private static ConcurrentLinkedDeque<CiCompilationStatistics> list = new ConcurrentLinkedDeque<>();

    private static final ThreadLocal<Deque<CiCompilationStatistics>> current = new ThreadLocal<Deque<CiCompilationStatistics>>() {

        @Override
        protected Deque<CiCompilationStatistics> initialValue() {
            return new ArrayDeque<>();
        }
    };

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private static @interface AbsoluteTimeValue {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private static @interface TimeValue {
    }

    private static long zeroTime = System.nanoTime();

    private final String holder;
    private final String name;
    private final String signature;
    @AbsoluteTimeValue
    private final long startTime;
    @TimeValue
    private long duration;
    private int startInvCount;
    private int endInvCount;
    private int bytecodeCount;
    private int codeSize;
    private int deoptCount;

    private CiCompilationStatistics(ResolvedJavaMethod method) {
        if (method != null) {
            holder = CiUtil.format("%H", method);
            name = method.name();
            signature = CiUtil.format("%p", method);
            startTime = System.nanoTime();
            startInvCount = method.invocationCount();
            bytecodeCount = method.codeSize();
        } else {
            holder = "";
            name = "";
            signature = "";
            startTime = 0;
        }
    }

    public void finish(ResolvedJavaMethod method) {
        if (ENABLED) {
            duration = System.nanoTime() - startTime;
            endInvCount = method.invocationCount();
            codeSize = method.compiledCodeSize();
            if (current.get().getLast() != this) {
                throw new RuntimeException("mismatch in finish()");
            }
            current.get().removeLast();
        }
    }

    public static CiCompilationStatistics current() {
        return current.get().isEmpty() ? null : current.get().getLast();
    }

    public static CiCompilationStatistics create(ResolvedJavaMethod method) {
        if (ENABLED) {
            CiCompilationStatistics stats = new CiCompilationStatistics(method);
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
            ConcurrentLinkedDeque<CiCompilationStatistics> snapshot = list;
            long snapshotZeroTime = zeroTime;

            list = new ConcurrentLinkedDeque<>();
            zeroTime = System.nanoTime();

            Date now = new Date();
            String dateString = (now.getYear() + 1900) + "_" + (now.getMonth() + 1) + "_" + now.getDate() + " " + now.getHours() + "_" + now.getMinutes() + "_" + now.getSeconds();
            try (PrintStream out = new PrintStream("compilations " + dateString + " " + dumpName + ".csv")) {
                // output the list of all compilations

                Field[] declaredFields = CiCompilationStatistics.class.getDeclaredFields();
                ArrayList<Field> fields = new ArrayList<>();
                for (Field field : declaredFields) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        fields.add(field);
                    }
                }
                for (Field field : fields) {
                    out.print(field.getName() + ";");
                }
                out.println();
                for (CiCompilationStatistics stats : snapshot) {
                    for (Field field : fields) {
                        if (field.isAnnotationPresent(AbsoluteTimeValue.class)) {
                            double value = (field.getLong(stats) - snapshotZeroTime) / 1000000d;
                            out.print(String.format(Locale.ENGLISH, "%.3f", value) + ";");
                        } else if (field.isAnnotationPresent(TimeValue.class)) {
                            double value = field.getLong(stats) / 1000000d;
                            out.print(String.format(Locale.ENGLISH, "%.3f", value) + ";");
                        } else {
                            out.print(field.get(stats) + ";");
                        }
                    }
                    out.println();
                }
            }

            String timelineFile = System.getProperty("stats.timeline.file");
            if (timelineFile == null || timelineFile.isEmpty()) {
                timelineFile = "timeline " + dateString;
            }
            try (FileOutputStream fos = new FileOutputStream(timelineFile + " " + dumpName + ".csv", true); PrintStream out = new PrintStream(fos)) {

                long[] timeSpent = new long[10000];
                int maxTick = 0;
                for (CiCompilationStatistics stats : snapshot) {
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
                    out.print(timelineName + ";");
                }
                for (int i = 0; i <= maxTick; i++) {
                    out.print((timeSpent[i] * 100 / RESOLUTION) + ";");
                }
                out.println();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setDeoptCount(int count) {
        this.deoptCount = count;
    }
}
