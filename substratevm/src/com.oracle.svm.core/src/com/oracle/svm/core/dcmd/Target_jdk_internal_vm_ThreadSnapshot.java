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

import java.lang.Thread.State;
import java.util.concurrent.locks.LockSupport;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.monitor.JavaMonitor;
import com.oracle.svm.core.monitor.JavaMonitorQueuedSynchronizer;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.Target_java_lang_VirtualThread;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.BasedOnJDKFile;

@TargetClass(className = "jdk.internal.vm.ThreadSnapshot")
final class Target_jdk_internal_vm_ThreadSnapshot {
    @Alias //
    String name;
    /** Replaces {@link #threadStatus}, to avoid unnecessary conversions. */
    @Inject //
    State state;
    @Delete //
    private int threadStatus;
    @Alias //
    Thread carrierThread;
    @Alias //
    StackTraceElement[] stackTrace;
    @Alias //
    int blockerTypeOrdinal;
    @Alias //
    Object blockerObject;

    @Alias //
    Target_jdk_internal_vm_ThreadSnapshot() {
    }

    @Substitute
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+26/src/java.base/share/native/libjava/ThreadSnapshot.c#L32-L36")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+36/src/hotspot/share/prims/jvm.cpp#L2964-L2971")
    private static Target_jdk_internal_vm_ThreadSnapshot create(Thread thread) {
        return ThreadSnapshotUtil.create(thread);
    }

    @Substitute
    State threadState() {
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

final class ThreadSnapshotUtil {
    /**
     * At the moment, this method only computes the most relevant data, i.e., the blocker
     * information is incomplete and owned monitors are not supported. It is also slow because it
     * often needs a VM operation.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+36/src/hotspot/share/services/threadService.cpp#L1437-L1554")
    public static Target_jdk_internal_vm_ThreadSnapshot create(Thread thread) {
        if (thread == Thread.currentThread()) {
            /* No VM operation needed. */
            return createSnapshot(thread);
        }

        /*
         * Enqueue a VM operation to get a consistent state. Could use a thread-local handshake in
         * the future (see GR-60270).
         */
        CreateThreadSnapshotOperation op = new CreateThreadSnapshotOperation(thread);
        op.enqueue();
        return op.result;
    }

    private static Target_jdk_internal_vm_ThreadSnapshot createSnapshot(Thread thread) {
        assert thread == Thread.currentThread() || VMOperation.isInProgressAtSafepoint();

        Target_jdk_internal_vm_ThreadSnapshot snapshot = new Target_jdk_internal_vm_ThreadSnapshot();
        snapshot.stackTrace = thread.getStackTrace();
        snapshot.name = thread.getName();
        snapshot.state = thread.getState();

        if (JavaThreads.isVirtual(thread)) {
            Target_java_lang_VirtualThread vthread = SubstrateUtil.cast(thread, Target_java_lang_VirtualThread.class);
            snapshot.carrierThread = JavaThreads.getVirtualThreadCarrier(vthread);
        }

        /* Setting the blocker info in cases other than PARK_BLOCKER is non-trivial. */
        Object blocker = LockSupport.getBlocker(thread);
        if (blocker != null && !(blocker instanceof JavaMonitor) && !(blocker instanceof JavaMonitorQueuedSynchronizer.JavaMonitorConditionObject)) {
            snapshot.blockerTypeOrdinal = SubstrateUtil.cast(Target_jdk_internal_vm_ThreadSnapshot_BlockerLockType.PARK_BLOCKER, Enum.class).ordinal();
            snapshot.blockerObject = blocker;
        }

        return snapshot;
    }

    private static class CreateThreadSnapshotOperation extends JavaVMOperation {
        private final Thread thread;
        Target_jdk_internal_vm_ThreadSnapshot result;

        CreateThreadSnapshotOperation(Thread thread) {
            super(VMOperationInfos.get(CreateThreadSnapshotOperation.class, "Create ThreadSnapshot", SystemEffect.SAFEPOINT));
            this.thread = thread;
        }

        @Override
        protected void operate() {
            result = createSnapshot(thread);
        }
    }
}
