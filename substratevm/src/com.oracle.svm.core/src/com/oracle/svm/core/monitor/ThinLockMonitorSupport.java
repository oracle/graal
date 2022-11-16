/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, BELLSOFT. All rights reserved.
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
package com.oracle.svm.core.monitor;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import jdk.internal.misc.Unsafe;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.word.Pointer;

/**
 * Implementation of synchronized-related operations for Thin Lock.
 * Thin lock mark word format (64 bits):
 * thread id: 55 bits. Id of thread owning the monitor
 * count: 8 bits. Enter counter. Starts with zero
 * shape: 1 bit. 0x1 - thin lock. 0x0 - fat lock
 */
public class ThinLockMonitorSupport extends MultiThreadedMonitorSupport {

    public static final int MONITOR_THREAD_MASK = 0xfffffe00;
    public static final int MONITOR_COUNT_MASK = 0x1fe;
    public static final int MONITOR_COUNT_MAX = 0x1fe;
    public static final int MONITOR_SHAPE_MASK = 0x1;
    public static final int MONITOR_THREAD_POSITION = 9;
    public static final int MONITOR_COUNT_POSITION = 1;
    public static final int MONITOR_SHAPE_THIN = 0x1;
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    protected static void thinLockMonitorEnter(Object obj) {
        int monitorOffset = getMonitorOffset(obj);
        if (monitorOffset == 0) {
            slowPathMonitorEnter(obj);
        } else {
            long currentThreadLog = Thread.currentThread().getId();
            long currentThread = currentThreadLog << MONITOR_THREAD_POSITION;
            if (!UNSAFE.compareAndSetLong(obj, monitorOffset, 0, currentThread + MONITOR_SHAPE_THIN)) {     // Try to capture monitor with thin lock
                long oldObjMark = BarrieredAccess.readLong(obj, monitorOffset);
                if (isMonitorInflated(oldObjMark)) {                                                        // Monitor has already been inflated
                    slowPathMonitorEnter(obj);
                    return;
                }

                long oldThread = oldObjMark & MONITOR_THREAD_MASK;
                if (oldThread == currentThread) {                                                           // Monitor captured by current thread - reenter
                    long oldCount = oldObjMark & MONITOR_COUNT_MASK;
                    if (oldCount == MONITOR_COUNT_MAX) {                                                    // Counter overflow - inflate monitor
                        inflateMonitor(obj);
                        slowPathMonitorEnter(obj);
                    } else {
                        long newObjMark = oldObjMark + (1 << MONITOR_COUNT_POSITION);
                        if (!UNSAFE.compareAndSetLong(obj, monitorOffset, oldObjMark, newObjMark)) {
                            slowPathMonitorEnter(obj);                                                      // Monitor has been inflated in another thread
                        }
                    }
                } else {                                                                                    // Monitor captured by another thread
                    inflateMonitor(obj);
                    slowPathMonitorEnter(obj);
                }
            }
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    protected static void thinLockMonitorExit(Object obj) {
        int monitorOffset = getMonitorOffset(obj);
        long oldObjMark = BarrieredAccess.readLong(obj, monitorOffset);
        if (isMonitorInflated(oldObjMark) || monitorOffset == 0) {                                          // The object does not have mark word
            slowPathMonitorExit(obj);                                                                       // Or already has been inflated
            return;
        }

        long oldCount = oldObjMark & MONITOR_COUNT_MASK;
        if (oldCount == 0) {                                                                                // Last monitor exit, clear mark word
            if (!UNSAFE.compareAndSetLong(obj, monitorOffset, oldObjMark, 0)) {
                slowPathMonitorExit(obj);
            }
        } else {                                                                                            // Decrement thin lock counter
            long newObjMark = oldObjMark - (1 << MONITOR_COUNT_POSITION);
            if (!UNSAFE.compareAndSetLong(obj, monitorOffset, oldObjMark, newObjMark)) {
                slowPathMonitorExit(obj);
            }
        }
    }

    @SuppressFBWarnings(value = {"WA_AWAIT_NOT_IN_LOOP"}, justification = "This method is a wait implementation.")
    @Override
    protected void doWait(Object obj, long timeoutMillis) throws InterruptedException {
        inflateMonitor(obj);
        super.doWait(obj, timeoutMillis);
    }

    @Override
    public void notify(Object obj, boolean notifyAll) {
        inflateMonitor(obj);
        super.notify(obj, notifyAll);
    }

    protected static void inflateMonitor(Object obj) {                         // Monitor must be captured by current thread
        int monitorOffset = getMonitorOffset(obj);
        JavaMonitor newLock = newMonitorLock();
        long newLockAddress = ReferenceAccess.singleton().getCompressedRepresentation(newLock).rawValue();
        long mark = BarrieredAccess.readLong(obj, monitorOffset);
        while (!isMonitorInflated(mark)) {
            long threadId = (mark & MONITOR_THREAD_MASK) >> 9;
            int markCount = (int) ((mark & MONITOR_COUNT_MASK) >> 1);
            int lockCount = (threadId == 0) ? 0 : markCount + 1;
            newLock.setOwnerThreadId(threadId);
            newLock.setCount(lockCount);
            mark = compareAndExchange(obj, monitorOffset, mark, newLockAddress, newLock);
        }
    }

    @Uninterruptible(reason = "Prevent GC intervening between compareAndExchangeLong and putObject")
    protected static long compareAndExchange(Object obj, int offset, long oldLong, long newLong, Object newObject) {
        long result = UNSAFE.compareAndExchangeLong(obj, offset, oldLong, newLong);
        if (result == oldLong) {
            UNSAFE.putObject(obj, offset, newObject);                           // Mark newObject as trackable object
            return newLong;
        }
        return result;
    }

    protected static boolean isMonitorInflated(long mark) {
        long shape = mark & MONITOR_SHAPE_MASK;
        if (shape == MONITOR_SHAPE_THIN) {
            return false;                                                      // Monitor is thin
        }
        return mark != 0;                                                      // Monitor is free
    }

    public static boolean isThinLockMonitor(Pointer p) {
        return SubstrateOptions.ThinLock.getValue() && p.and(MONITOR_SHAPE_MASK).equal(MONITOR_SHAPE_THIN);
    }
}


