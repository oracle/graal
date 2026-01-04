/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.test.GraalTest;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.util.Date;
import java.util.Formatter;
import java.util.concurrent.TimeUnit;

/**
 * A try-with-resources scope for raising an error if the time spent executing in the scope exceeds
 * some limit.
 *
 * {@snippet lang = java :
 * try (var scope = TimeLimit.create(1000, "MyTask")) {
 *     // MyTask must complete in under 1000 milliseconds to
 *     // avoid AssertionError being thrown when scope is closed.
 *     work();
 * }
 * }
 */
public final class TimeLimit implements AutoCloseable {

    private volatile boolean closed;
    private final Thread watchdog;

    /**
     * Describes error to thrown when {@link #close()} is called.
     */
    private String error;

    /**
     * Enters a scope for timing execution within the scope.
     *
     * @param limit a time limit in milliseconds. If the execution exceeds this limit, an
     *            {@link AssertionError} is thrown when the scope is {@linkplain #close() closed}.
     *            The error will include a dump of all threads and other info that should provide
     *            insight as to why the execution ran too long.
     * @param label a descriptive name of the execution
     * @return a TimeLimit scope object or null if JMX is not available
     */
    public static TimeLimit create(long limit, String label) {
        if (GraalTest.isManagementLibraryIsLoadable() != null) {
            return null;
        }
        return new TimeLimit(limit, label);
    }

    private TimeLimit(long limit, String label) {
        watchdog = new Thread("TimeLimitWatchDog") {
            @Override
            public void run() {
                long elapsed = 0;
                while (elapsed < limit && !closed) {
                    long start = System.nanoTime();
                    try {
                        Thread.sleep(limit - elapsed);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    elapsed += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                }
                if (!closed) {
                    setError(limit, label);
                }
            }
        };
        watchdog.setDaemon(true);
        watchdog.start();

    }

    @Override
    public void close() {
        closed = true;
        watchdog.interrupt();
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    private void setError(long limit, String label) {
        Formatter buf = new Formatter();
        buf.format("Task '%s' failed to complete within %d milleseconds%n", label, limit);
        buf.format("Dumping all stack traces. Current time: %s%n", new Date());
        threadDump(buf);
        Runtime runtime = Runtime.getRuntime();
        final long heapSizeUnit = 1024 * 1024;
        long usedHeapSize = runtime.totalMemory() / heapSizeUnit;
        long freeHeapSize = runtime.freeMemory() / heapSizeUnit;
        long maximumHeapSize = runtime.maxMemory() / heapSizeUnit;
        buf.format("=== Memory statistics (in MB):%n=== Used heap size: %d%n=== Free heap size: %d%n=== Maximum heap size: %d%n", usedHeapSize, freeHeapSize, maximumHeapSize);
        this.error = buf.toString();
    }

    private static void threadDump(Formatter buf) {
        for (ThreadInfo ti : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            printThreadInfo(buf, ti);
            printLockInfo(buf, ti.getLockedSynchronizers());
        }
        buf.format("%n");
    }

    private static void printThreadInfo(Formatter buf, ThreadInfo ti) {
        buf.format("\"%s\" Id=%d in %s", ti.getThreadName(), ti.getThreadId(), ti.getThreadState());
        if (ti.getLockName() != null) {
            buf.format(" on lock=%s", ti.getLockName());
        }
        if (ti.isSuspended()) {
            buf.format(" (suspended)");
        }
        if (ti.isInNative()) {
            buf.format(" (running in native)");
        }
        buf.format("%n");

        if (ti.getLockOwnerName() != null) {
            buf.format("      owned by %s Id=%d%n", ti.getLockOwnerName(), ti.getLockOwnerId());
        }

        StackTraceElement[] stacktrace = ti.getStackTrace();
        MonitorInfo[] monitors = ti.getLockedMonitors();
        for (int i = 0; i < stacktrace.length; i++) {
            StackTraceElement ste = stacktrace[i];
            buf.format("    at %s%n", ste);
            for (MonitorInfo mi : monitors) {
                if (mi.getLockedStackDepth() == i) {
                    buf.format("      - locked %s%n", mi);
                }
            }
        }
        buf.format("%n");
    }

    private static void printLockInfo(Formatter buf, LockInfo[] locks) {
        if (locks.length > 0) {
            buf.format("    Locked synchronizers: count = %d%n", locks.length);
            for (LockInfo li : locks) {
                buf.format("      - %s%n", li);
            }
            buf.format("%n");
        }
    }
}
