/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.CurrentIsolate;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.JavaMonitorEnterEvent;
import com.oracle.svm.core.jfr.JfrTicks;

public class JavaMonitor extends ReentrantLock {
    private long ownerTid;
    private int waitingThreads = 0;
    private LinkedList<Long> notifiers = new LinkedList<Long>();

    public long getOwnerTid() {
        return ownerTid;
    }

    public long getNotifierTid() {
        waitingThreads--;
        return notifiers.removeFirst();
    }

    public void addWaiter() {
        waitingThreads++;
    }

    public void setNotifier(boolean notifyAll) {
        long curr = SubstrateJVM.get().getThreadId(CurrentIsolate.getCurrentThread());
        assert isLocked() && getOwnerTid() == curr; //make sure we hold the lock
        int notifications = 1;
        if (notifyAll) {
            notifications = waitingThreads - notifiers.size();
        }
        for (int i = 0; i < notifications; i++) {
            notifiers.addLast(curr);
        }
    }

    public JavaMonitor() {
        super();
        ownerTid = SubstrateJVM.get().getThreadId(CurrentIsolate.getCurrentThread());
    }

    public void monitorEnter(Object obj) {
        if (!tryLock()) {
            long startTicks = JfrTicks.elapsedTicks();
            lock();
            JavaMonitorEnterEvent.emit(obj, getOwnerTid(), startTicks);
        }
        ownerTid = SubstrateJVM.get().getThreadId(CurrentIsolate.getCurrentThread());
    }


}
