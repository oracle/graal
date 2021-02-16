/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.io.Closeable;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.SubstrateOptionsParser;

public class DeadlockWatchdog implements Closeable {

    private final int watchdogInterval;
    private final boolean watchdogExitOnTimeout;
    private final Thread thread;

    private volatile long nextDeadline;
    private volatile boolean stopped;

    DeadlockWatchdog() {
        /* Access to options must be done in main thread because it requires ImageSingletons. */
        watchdogInterval = SubstrateOptions.DeadlockWatchdogInterval.getValue();
        watchdogExitOnTimeout = SubstrateOptions.DeadlockWatchdogExitOnTimeout.getValue();

        if (watchdogInterval > 0) {
            thread = new Thread(this::watchdogThread);
            thread.start();
        } else {
            thread = null;
        }
    }

    public void recordActivity() {
        nextDeadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(watchdogInterval);
    }

    @Override
    public void close() {
        stopped = true;
        if (thread != null) {
            thread.interrupt();
        }
    }

    void watchdogThread() {
        recordActivity();

        while (!stopped) {
            long now = System.currentTimeMillis();
            if (now >= nextDeadline) {
                System.err.println();
                System.err.println("=== Image generator watchdog detected no activity. This can be a sign of a deadlock during image building. Dumping all stack traces. Current time: " + new Date());
                threadDump();
                Runtime runtime = Runtime.getRuntime();
                final long heapSizeUnit = 1024 * 1024;
                long usedHeapSize = runtime.totalMemory() / heapSizeUnit;
                long freeHeapSize = runtime.freeMemory() / heapSizeUnit;
                long maximumHeapSize = runtime.maxMemory() / heapSizeUnit;
                System.err.printf("=== Memory statistics (in MB):%n=== Used heap size: %d%n=== Free heap size: %d%n=== Maximum heap size: %d%n", usedHeapSize, freeHeapSize, maximumHeapSize);
                System.err.flush();

                if (watchdogExitOnTimeout) {
                    System.err.println("=== Image generator watchdog is aborting image generation. To configure the watchdog, use the options " +
                                    SubstrateOptionsParser.commandArgument(SubstrateOptions.DeadlockWatchdogInterval, Integer.toString(watchdogInterval), null) + " and " +
                                    SubstrateOptionsParser.commandArgument(SubstrateOptions.DeadlockWatchdogExitOnTimeout, "+", null));
                    /*
                     * Since there is a likely deadlock somewhere, there is no less intrusive way to
                     * abort other than a hard exit of the image builder VM.
                     */
                    System.exit(1);

                } else {
                    recordActivity();
                }
            }

            try {
                Thread.sleep(Math.min(nextDeadline - now, TimeUnit.SECONDS.toMillis(1)));
            } catch (InterruptedException e) {
                /* Nothing to do, when close() was called then we will exit the loop. */
            }
        }
    }

    private static void threadDump() {
        for (ThreadInfo ti : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            printThreadInfo(ti);
            printLockInfo(ti.getLockedSynchronizers());
        }
        System.err.println();
    }

    private static void printThreadInfo(ThreadInfo ti) {
        StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"" + " Id=" + ti.getThreadId() + " in " + ti.getThreadState());
        if (ti.getLockName() != null) {
            sb.append(" on lock=" + ti.getLockName());
        }
        if (ti.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (ti.isInNative()) {
            sb.append(" (running in native)");
        }
        System.err.println(sb.toString());

        if (ti.getLockOwnerName() != null) {
            System.err.println("      owned by " + ti.getLockOwnerName() + " Id=" + ti.getLockOwnerId());
        }

        StackTraceElement[] stacktrace = ti.getStackTrace();
        MonitorInfo[] monitors = ti.getLockedMonitors();
        for (int i = 0; i < stacktrace.length; i++) {
            StackTraceElement ste = stacktrace[i];
            System.err.println("    at " + ste.toString());
            for (MonitorInfo mi : monitors) {
                if (mi.getLockedStackDepth() == i) {
                    System.err.println("      - locked " + mi);
                }
            }
        }
        System.err.println();
    }

    private static void printLockInfo(LockInfo[] locks) {
        if (locks.length > 0) {
            System.err.println("    Locked synchronizers: count = " + locks.length);
            for (LockInfo li : locks) {
                System.err.println("      - " + li);
            }
            System.err.println();
        }
    }
}
