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
package com.oracle.svm.core.monitor;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * Without support for threads, there is no need for any monitor operations.
 */
public class SingleThreadedMonitorSupport extends MonitorSupport {

    @Override
    public void monitorEnter(Object obj) {
        /* Synchronization is a no-op in single threaded mode. */
    }

    @Override
    public void monitorExit(Object obj) {
        /* Synchronization is a no-op in single threaded mode. */
    }

    @Override
    public Object prepareRelockObject(Object obj) {
        return null;
    }

    @Uninterruptible(reason = "called during deoptimization")
    @Override
    public void doRelockObject(Object obj, Object lockData) {
    }

    @Override
    public boolean isLockedByCurrentThread(Object obj) {
        /*
         * Since monitorenter and monitorexit are no-ops, we do not know the real answer. But since
         * the current thread has exclusive access to the object, true is a correct answer. Callers
         * of isLockedByCurrentThread usually want to ensure that synchronization has occurred,
         * i.e., assert that the returned value is true.
         */
        return true;
    }

    @Override
    public boolean isLockedByAnyThread(Object obj) {
        return isLockedByCurrentThread(obj);
    }

    @Override
    protected void doWait(Object obj, long timeoutMillis) throws InterruptedException {
        /*
         * There is no other thread that can interrupt waiting, so it is just sleeping. It is
         * questionable whether this implementation is useful, especially waiting without a timeout.
         * But it is the best thing we can do.
         */
        Thread.sleep(timeoutMillis == 0 ? Long.MAX_VALUE : timeoutMillis);
    }

    @Override
    public void notify(Object obj, boolean notifyAll) {
        /* No other thread can be waiting, so notify is a no-op. */
        return;
    }

    @Override
    public int maybeAdjustNewParkStatus(int status) {
        return status;
    }
}
