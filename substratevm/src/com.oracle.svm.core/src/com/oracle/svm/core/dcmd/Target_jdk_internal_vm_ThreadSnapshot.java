/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, IBM Inc. All rights reserved.
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

package com.oracle.svm.core.dcmd;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import com.oracle.svm.core.monitor.JavaMonitor;
import com.oracle.svm.core.monitor.JavaMonitorQueuedSynchronizer;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Target_java_lang_VirtualThread;

import java.util.concurrent.locks.LockSupport;

@TargetClass(className = "jdk.internal.vm.ThreadSnapshot")
final class Target_jdk_internal_vm_ThreadSnapshot {
    @Alias //
    private String name;

    /**
     * This is replacing an int that would be set by Hotspot and later converted to a Thread.State
     * anyway.
     */
    @Inject //
    private Thread.State state;
    @Alias //
    private Thread carrierThread;
    @Alias //
    private StackTraceElement[] stackTrace;

    @Alias //
    private int blockerTypeOrdinal;
    @Alias //
    private Object blockerObject;

    @Alias //
    private Target_jdk_internal_vm_ThreadSnapshot() {
    }

    @Substitute
    private static Target_jdk_internal_vm_ThreadSnapshot create(Thread thread) {
        Target_jdk_internal_vm_ThreadSnapshot snapshot = new Target_jdk_internal_vm_ThreadSnapshot();
        snapshot.stackTrace = thread.getStackTrace();
        snapshot.name = thread.getName();
        snapshot.state = thread.getState();

        if (JavaThreads.isVirtual(thread)) {
            Target_java_lang_VirtualThread vthread = SubstrateUtil.cast(thread, Target_java_lang_VirtualThread.class);
            snapshot.carrierThread = vthread.carrierThread;
        }

        // Setting the blocker info in cases other than PARK_BLOCKER is non-trivial.
        Object blocker = LockSupport.getBlocker(thread);
        if (blocker != null && !(blocker instanceof JavaMonitor) && !(blocker instanceof JavaMonitorQueuedSynchronizer.JavaMonitorConditionObject)) {
            snapshot.blockerTypeOrdinal = SubstrateUtil.cast(Target_jdk_internal_vm_ThreadSnapshot_BlockerLockType.PARK_BLOCKER, Enum.class).ordinal();
            snapshot.blockerObject = blocker;
        }

        return snapshot;
    }

    @Substitute
    Thread.State threadState() {
        return state;
    }
}

@TargetClass(className = "jdk.internal.vm.ThreadSnapshot", innerClass = "BlockerLockType")
final class Target_jdk_internal_vm_ThreadSnapshot_BlockerLockType {
    // Checkstyle: stop
    @Alias //
    static Target_jdk_internal_vm_ThreadSnapshot_BlockerLockType PARK_BLOCKER;
    // Checkstyle: resume
}
