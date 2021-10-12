/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.compiler.serviceprovider.GraalServices;

public class Timer {

    private static final int UNKNOWN = -1;
    private String prefix;

    private final String name;
    private final boolean autoPrint;
    /** Timer start time in nanoseconds. */
    private long startTime;
    /** Timer total time in nanoseconds. */
    private long totalTime;
    /** Total VM memory in bytes recorded when the timer is printed. */
    private long totalHeapUsage;

    public Timer(String name) {
        this(null, name, true);
    }

    public Timer(String prefix, String name) {
        this(prefix, name, true);
    }

    public Timer(String name, boolean autoPrint) {
        this(null, name, autoPrint);
    }

    public Timer(String prefix, String name, boolean autoPrint) {
        this.prefix = prefix;
        this.name = name;
        this.autoPrint = autoPrint;
    }

    /**
     * Registers the prefix to be used when {@linkplain Timer#print(long) printing} a timer. This
     * allows the output of interlaced native image executions to be disambiguated.
     */
    public void setPrefix(String value) {
        this.prefix = value;
    }

    public StopTimer start() {
        startTime = System.nanoTime();
        return new StopTimer();
    }

    public void stop() {
        long addTime = System.nanoTime() - startTime;
        totalTime += addTime;
        if (autoPrint) {
            print(addTime);
        }
    }

    private void print(long time) {
        final String concurrentPrefix;
        String pid = GraalServices.getExecutionID();
        if (prefix != null) {
            // Add the PID to further disambiguate concurrent builds of images with the same name
            concurrentPrefix = String.format("[%s:%s] ", prefix, pid);
        } else {
            concurrentPrefix = "";
        }
        totalHeapUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double heapUsageGB = totalHeapUsage / 1024.0 / 1024.0 / 1024.0;
        String memoryInfo = String.format("%,5.2f GB (heap)", heapUsageGB);
        long totalRssMemoryKB = getRss(pid);
        if (totalRssMemoryKB != UNKNOWN) {
            double totalRssMemoryGB = totalRssMemoryKB / 1024.0 / 1024.0;
            memoryInfo = String.format("%s %,5.2f GB (rss)", memoryInfo, totalRssMemoryGB);
        }
        System.out.format("%s%12s: %,10.2f ms, %s%n", concurrentPrefix, name, time / 1000000d, memoryInfo);
    }

    public void print() {
        print(totalTime);
    }

    /** Get timer total time in milliseconds. */
    public double getTotalTime() {
        return totalTime / 1000000d;
    }

    /** Get total VM memory in bytes. */
    public long getTotalMemory() {
        return totalHeapUsage;
    }

    private static long getRss(String pid) {
        if (OS.isLinux()) {
            try {
                // Consider using /proc/$pid/statm, but we'd need to know page size for it to be
                // useful as those numbers are in in virtual memory pages
                Path procStatus = Paths.get(String.format("/proc/%s/status", pid));
                String vmRSS = Files.lines(procStatus).filter(line -> line.startsWith("VmRSS:")).findFirst().orElse("");
                String[] vmRSSFields = vmRSS.split("\\s+");
                return Long.parseLong(vmRSSFields[1]); // VmRSS: <val> KB => <val>
            } catch (Exception e) {
                // ignore
            }
        }
        return UNKNOWN;
    }

    private static class OS {

        private static final String OS_PROP = System.getProperty("os.name", "Unknown").toLowerCase();

        // no instances
        private OS() {
        }

        static boolean isLinux() {
            return OS_PROP.contains("linux");
        }
    }

    public class StopTimer implements AutoCloseable {

        @Override
        public void close() {
            stop();
        }
    }
}
