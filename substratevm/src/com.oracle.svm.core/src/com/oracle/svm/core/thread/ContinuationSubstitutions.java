/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import java.util.concurrent.locks.LockSupport;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.ContinuationsSupported;
import com.oracle.svm.core.jdk.NotLoomJDK;

@TargetClass(value = LockSupport.class, onlyWith = {ContinuationsSupported.class, NotLoomJDK.class})
final class Target_java_util_concurrent_locks_LockSupport {

    @Alias static Target_jdk_internal_misc_Unsafe_JavaThreads U;

    @Alias
    static native void setBlocker(Thread thread, Object blocker);

    @Substitute
    static void unpark(Thread thread) {
        if (thread != null) {
            if (VirtualThreads.singleton().isVirtual(thread)) {
                VirtualThreads.singleton().unpark(thread);
            } else {
                U.unpark(thread);
            }
        }
    }

    @Substitute
    static void park(Object blocker) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        long startTicks = com.oracle.svm.core.jfr.JfrTicks.elapsedTicks();
        try {
            if (VirtualThreads.singleton().isVirtual(t)) {
                VirtualThreads.singleton().park();
            } else {
                U.park(false, 0L);
            }
        } finally {
            com.oracle.svm.core.jfr.events.ThreadParkEvent.emit(startTicks, blocker,Long.MIN_VALUE, Long.MIN_VALUE);
            setBlocker(t, null);
        }
    }

    @Substitute
    static void parkNanos(Object blocker, long nanos) {
        System.out.println("park nanos");
        if (nanos > 0) {
            long startTicks = com.oracle.svm.core.jfr.JfrTicks.elapsedTicks();
            Thread t = Thread.currentThread();
            setBlocker(t, blocker);
            try {
                if (VirtualThreads.singleton().isVirtual(t)) {
                    VirtualThreads.singleton().parkNanos(nanos);
                } else {
                    U.park(false, nanos);
                }
            } finally {
                com.oracle.svm.core.jfr.events.ThreadParkEvent.emit(startTicks, blocker, nanos, Long.MIN_VALUE);
                setBlocker(t, null);
            }
        }
    }

    @Substitute
    static void parkUntil(Object blocker, long deadline) {
        long startTicks = com.oracle.svm.core.jfr.JfrTicks.elapsedTicks();
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        try {
            if (VirtualThreads.singleton().isVirtual(t)) {
                VirtualThreads.singleton().parkUntil(deadline);
            } else {
                U.park(true, deadline);
            }
        } finally {
            com.oracle.svm.core.jfr.events.ThreadParkEvent.emit(startTicks, blocker, Long.MIN_VALUE, deadline);
            setBlocker(t, null);
        }
    }

    @Substitute
    static void park() {
        long startTicks = com.oracle.svm.core.jfr.JfrTicks.elapsedTicks();
        if (VirtualThreads.singleton().isVirtual(Thread.currentThread())) {
            VirtualThreads.singleton().park();
        } else {
            U.park(false, 0L);
        }
        com.oracle.svm.core.jfr.events.ThreadParkEvent.emit(startTicks, null, Long.MIN_VALUE, Long.MIN_VALUE);
    }

    @Substitute
    public static void parkNanos(long nanos) {
        if (nanos > 0) {
            long startTicks = com.oracle.svm.core.jfr.JfrTicks.elapsedTicks();
            if (VirtualThreads.singleton().isVirtual(Thread.currentThread())) {
                VirtualThreads.singleton().parkNanos(nanos);
                ((SubstrateVirtualThread) Thread.currentThread()).parkNanos(nanos);
            } else {
                U.park(false, nanos);
            }
            com.oracle.svm.core.jfr.events.ThreadParkEvent.emit(startTicks, null, nanos, Long.MIN_VALUE);
        }
    }

    @Substitute
    public static void parkUntil(long deadline) {
        long startTicks = com.oracle.svm.core.jfr.JfrTicks.elapsedTicks();
        if (VirtualThreads.singleton().isVirtual(Thread.currentThread())) {
            VirtualThreads.singleton().parkUntil(deadline);
        } else {
            U.park(true, deadline);
        }
        com.oracle.svm.core.jfr.events.ThreadParkEvent.emit(startTicks, null, Long.MIN_VALUE, deadline);
    }
}

@TargetClass(className = "sun.nio.ch.NativeThreadSet", onlyWith = {ContinuationsSupported.class, NotLoomJDK.class})
final class Target_sun_nio_ch_NativeThreadSet {
    @Alias @InjectAccessors(NativeThreadSetUsedAccessors.class) //
    int used;

    @Inject //
    int injectedUsed;
}

final class NativeThreadSetUsedAccessors {
    static int get(Target_sun_nio_ch_NativeThreadSet that) {
        return that.injectedUsed;
    }

    static void set(Target_sun_nio_ch_NativeThreadSet that, int value) {
        // Note that the accessing method holds a lock that prevents concurrent updates
        if (VirtualThreads.singleton().isVirtual(Thread.currentThread())) {
            int diff = value - that.injectedUsed;
            if (diff == 1) {
                VirtualThreads.singleton().pinCurrent();
            } else if (diff == -1) {
                VirtualThreads.singleton().unpinCurrent();
            } else {
                assert value == 0 : "must only be incremented or decremented by 1 (or initialized to 0)";
            }
        }
        that.injectedUsed = value;
    }

    private NativeThreadSetUsedAccessors() {
    }
}

final class ContinuationSubstitutions {
    private ContinuationSubstitutions() {
    }
}
