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

import java.lang.management.LockInfo;
import java.lang.management.ThreadInfo;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.SubstrateOptionsParser;

public class DeadlockWatchdog extends Watchdog {

    DeadlockWatchdog() {
        /* Access to options must be done in main thread because it requires ImageSingletons. */
        super(Math.toIntExact(TimeUnit.MINUTES.toSeconds(SubstrateOptions.DeadlockWatchdogInterval.getValue())), SubstrateOptions.DeadlockWatchdogExitOnTimeout.getValue());
    }

    @Override
    protected void deadlineMissed() {
        System.err.println();
        System.err.println("=== Image generator watchdog detected no activity. This can be a sign of a deadlock during image building. Dumping all stack traces. Current time: " + new Date());
        super.deadlineMissed();
        Runtime runtime = Runtime.getRuntime();
        final long heapSizeUnit = 1024 * 1024;
        long usedHeapSize = runtime.totalMemory() / heapSizeUnit;
        long freeHeapSize = runtime.freeMemory() / heapSizeUnit;
        long maximumHeapSize = runtime.maxMemory() / heapSizeUnit;
        System.err.printf("=== Memory statistics (in MB):%n=== Used heap size: %d%n=== Free heap size: %d%n=== Maximum heap size: %d%n", usedHeapSize, freeHeapSize, maximumHeapSize);
        System.err.flush();
    }

    @Override
    protected void exitOnTimeout() {
        System.err.println("=== Image generator watchdog is aborting image generation. To configure the watchdog, use the options " +
                        SubstrateOptionsParser.commandArgument(SubstrateOptions.DeadlockWatchdogInterval, Integer.toString(SubstrateOptions.DeadlockWatchdogInterval.getValue()), null) + " and " +
                        SubstrateOptionsParser.commandArgument(SubstrateOptions.DeadlockWatchdogExitOnTimeout, "+", null));
        /*
         * Since there is a likely deadlock somewhere, there is no less intrusive way to abort other
         * than a hard exit of the image builder VM.
         */
        super.exitOnTimeout();
    }

    @Override
    protected void printThreadInfo(ThreadInfo ti) {
        super.printThreadInfo(ti);
        printLockInfo(ti.getLockedSynchronizers());
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
