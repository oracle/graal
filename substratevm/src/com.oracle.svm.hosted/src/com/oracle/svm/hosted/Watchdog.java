/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.util.concurrent.TimeUnit;

public class Watchdog implements Closeable {

    private final int watchdogInterval;
    private final boolean watchdogExitOnTimeout;
    private final Thread thread;

    private volatile long nextDeadline;
    private volatile boolean stopped;

    Watchdog(int watchdogInterval, boolean watchdogExitOnTimeout) {
        this.watchdogInterval = watchdogInterval;
        this.watchdogExitOnTimeout = watchdogExitOnTimeout;

        if (this.watchdogInterval > 0) {
            thread = new Thread(this::watchdogThread);
            thread.start();
        } else {
            thread = null;
        }
    }

    final void recordActivity() {
        nextDeadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(watchdogInterval);
    }

    @Override
    public void close() {
        stopped = true;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void watchdogThread() {
        recordActivity();

        while (!stopped) {
            long now = System.currentTimeMillis();
            if (now >= nextDeadline) {
                deadlineMissed();
                if (watchdogExitOnTimeout) {
                    exitOnTimeout();
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

    protected void deadlineMissed() {
        for (ThreadInfo ti : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            printThreadInfo(ti);
        }
        System.err.println();
    }

    protected void exitOnTimeout() {
        System.exit(1);
    }

    protected void printThreadInfo(ThreadInfo ti) {
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
}
