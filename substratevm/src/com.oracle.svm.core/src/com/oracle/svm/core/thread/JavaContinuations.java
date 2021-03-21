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

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.heap.StoredContinuationImpl;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.util.VMError;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

public class JavaContinuations {
    public static final int YIELDING = -2;
    public static final int YIELD_SUCCESS = 0;
    public static final int PINNED_CRITICAL_SECTION = 1;
    public static final int PINNED_NATIVE = 2;
    public static final int PINNED_MONITOR = 3;

    @Fold
    public static boolean useLoom() {
        return SubstrateOptions.UseLoom.getValue();
    }

    @NeverInline("access stack pointer")
    public static Integer yield(Target_java_lang_Continuation cont) {
        Pointer leafSP = KnownIntrinsics.readCallerStackPointer();
        Pointer rootSP = cont.sp;
        CodePointer leafIP = KnownIntrinsics.readReturnAddress();
        CodePointer rootIP = cont.ip;

        int preemptStatus = StoredContinuationImpl.allocateFromCurrentStack(cont, rootSP, leafSP, leafIP);
        if (preemptStatus != 0) {
            return preemptStatus;
        }

        cont.sp = leafSP;
        cont.ip = leafIP;

        KnownIntrinsics.farReturn(0, rootSP, rootIP, false);
        throw VMError.shouldNotReachHere("value should be returned by `farReturn`");
    }

    public static int tryPreempt(Target_java_lang_Continuation cont, Thread thread) {
        TryPreemptThunk thunk = new TryPreemptThunk(cont, thread);
        JavaVMOperation.enqueueBlockingSafepoint("tryForceYield0", thunk);
        return thunk.preemptStatus;
    }

    private static class TryPreemptThunk implements SubstrateUtil.Thunk {
        int preemptStatus = YIELD_SUCCESS;

        final Target_java_lang_Continuation cont;
        final Thread thread;

        TryPreemptThunk(Target_java_lang_Continuation cont, Thread thread) {
            this.cont = cont;
            this.thread = thread;
        }

        @Override
        public void invoke() {
            IsolateThread vmThread = JavaThreads.getIsolateThread(thread);
            Pointer rootSP = cont.sp;
            CodePointer rootIP = cont.ip;
            preemptStatus = StoredContinuationImpl.allocateFromForeignStack(cont, rootSP, vmThread);
            if (preemptStatus == 0) {
                VMThreads.ActionOnExitSafepointSupport.setSwitchStack(vmThread);
                VMThreads.ActionOnExitSafepointSupport.setSwitchStackTarget(vmThread, rootSP, rootIP);
            }
        }
    }

    public static int isPinned(Target_java_lang_Thread thread, Target_java_lang_ContinuationScope scope, boolean isCurrentThread) {
        Target_java_lang_Continuation cont = thread.getContinuation();

        IsolateThread vmThread = isCurrentThread ? CurrentIsolate.getCurrentThread() : JavaThreads.getIsolateThread(SubstrateUtil.cast(thread, Thread.class));

        if (cont != null) {
            int threadMonitorCount = MonitorSupport.singleton().countThreadLock(vmThread);

            while (true) {
                if (cont.cs > 0) {
                    return PINNED_CRITICAL_SECTION;
                } else if (threadMonitorCount > cont.monitorBefore) {
                    return PINNED_MONITOR;
                }

                if (cont.getParent() != null && cont.getScope() != scope) {
                    cont = cont.getParent();
                } else {
                    break;
                }
            }

            JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(vmThread);
            if (anchor.isNonNull() && cont.sp.aboveThan(anchor.getLastJavaSP())) {
                return PINNED_NATIVE;
            }
        }
        return YIELD_SUCCESS;
    }

    public static boolean isStarted(Target_java_lang_Continuation cont) {
        return cont.isStarted();
    }

    public static Pointer getSP(Target_java_lang_Continuation cont) {
        return cont.sp;
    }

    public static CodePointer getIP(Target_java_lang_Continuation cont) {
        return cont.ip;
    }

    public static void setIP(Target_java_lang_Continuation cont, CodePointer ip) {
        cont.ip = ip;
    }

    /**
     * Note this is different than `Thread.getContinuation`. `Thread.getContinuation` is orthogonal
     * with `VirtualThread.cont`.
     */
    public static Target_java_lang_Continuation getContinuation(Target_java_lang_Thread thread) {
        if (thread.isVirtual()) {
            Target_java_lang_VirtualThread vthread = SubstrateUtil.cast(thread, Target_java_lang_VirtualThread.class);
            return vthread.cont;
        }
        return thread.cont;
    }

    public static class LoomCompatibilityUtil {
        static long getStackSize(Target_java_lang_Thread tjlt) {
            return useLoom() ? tjlt.holder.stackSize : tjlt.stackSize;
        }

        static int getThreadStatus(Target_java_lang_Thread tjlt) {
            return useLoom() ? tjlt.holder.threadStatus : tjlt.threadStatus;
        }

        static void setThreadStatus(Target_java_lang_Thread tjlt, int threadStatus) {
            if (useLoom()) {
                tjlt.holder.threadStatus = threadStatus;
            } else {
                tjlt.threadStatus = threadStatus;
            }
        }

        static int getPriority(Target_java_lang_Thread tjlt) {
            if (useLoom()) {
                return tjlt.holder.priority;
            } else {
                return tjlt.priority;
            }
        }

        static void setPriority(Target_java_lang_Thread tjlt, int priority) {
            if (useLoom()) {
                tjlt.holder.priority = priority;
            } else {
                tjlt.priority = priority;
            }
        }

        static void setStackSize(Target_java_lang_Thread tjlt, long stackSize) {
            if (useLoom()) {
                tjlt.holder.stackSize = stackSize;
            } else {
                tjlt.stackSize = stackSize;
            }
        }

        static void setDaemon(Target_java_lang_Thread tjlt, boolean isDaemon) {
            if (useLoom()) {
                tjlt.holder.daemon = isDaemon;
            } else {
                tjlt.daemon = isDaemon;
            }
        }

        static void setGroup(Target_java_lang_Thread tjlt, ThreadGroup group) {
            if (useLoom()) {
                tjlt.holder.group = group;
            } else {
                tjlt.group = group;
            }
        }

        static void setTarget(Target_java_lang_Thread tjlt, Runnable target) {
            if (useLoom()) {
                tjlt.holder.task = target;
            } else {
                tjlt.target = target;
            }
        }

        static void initThreadFields(Target_java_lang_Thread tjlt, ThreadGroup group, Runnable target, long stackSize, int priority, boolean daemon, int threadStatus) {
            if (useLoom()) {
                tjlt.holder = new Target_java_lang_Thread_FieldHolder(null, null, 0, 0, false);
            }
            setGroup(tjlt, group);

            setPriority(tjlt, priority);
            setDaemon(tjlt, daemon);

            JavaContinuations.LoomCompatibilityUtil.setTarget(tjlt, target);
            tjlt.setPriority(priority);

            /* Stash the specified stack size in case the VM cares */
            JavaContinuations.LoomCompatibilityUtil.setStackSize(tjlt, stackSize);

            JavaContinuations.LoomCompatibilityUtil.setThreadStatus(tjlt, threadStatus);
        }
    }
}
