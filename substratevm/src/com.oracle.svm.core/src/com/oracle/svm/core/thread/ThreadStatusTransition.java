/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.thread.SafepointSlowpath.ENTER_SLOW_PATH_SAFEPOINT_CHECK_REGULAR_CCONV;
import static com.oracle.svm.core.thread.SafepointSlowpath.ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS;
import static com.oracle.svm.core.thread.SafepointSlowpath.SLOW_PATH_RUN_PENDING_ACTIONS;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.replacements.ReplacementsUtil;

/**
 * This class contains the logic for all relevant thread status transitions.
 *
 * Code that does a thread status transition from {@link StatusSupport#STATUS_IN_NATIVE} to some
 * other status must be written in a way so that it is safe to execute even if another thread is
 * currently in the middle of executing a VM operation at a safepoint. This is necessary because
 * threads with {@link StatusSupport#STATUS_IN_NATIVE} do not prevent safepoints and can therefore
 * try to do thread status transitions while a safepoint is in progress.
 *
 * Note that there are a few cases where the thread status is directly in the compiler backend, see
 * usages of {@link KnownOffsets#getVMThreadStatusOffset}.
 */
public class ThreadStatusTransition {
    /**
     * Does a transition from Java to native. This code does not perform any safepoint checks. Can
     * only be called from snippets.
     */
    public static void fromJavaToNative() {
        ReplacementsUtil.dynamicAssert(StatusSupport.isStatusJava(), "Thread status must be 'Java'.");
        VMThreads.StatusSupport.setStatusNative();
    }

    /**
     * Does a transition from native to Java. Does a safepoint slowpath call if a safepoint is
     * currently in effect. Can only be called from snippets.
     */
    public static void fromNativeToJava(boolean popFrameAnchor) {
        StatusSupport.assertStatusNativeOrSafepoint();
        int newStatus = StatusSupport.STATUS_IN_JAVA;

        if (probability(VERY_FAST_PATH_PROBABILITY, StatusSupport.compareAndSetNativeToNewStatus(newStatus))) {
            /* Prevent reads from floating before the status transition. */
            MembarNode.memoryBarrier(MembarNode.FenceKind.NONE, LocationIdentity.ANY_LOCATION);

            if (popFrameAnchor) {
                JavaFrameAnchors.popFrameAnchor();
            }

            /*
             * Another thread may have executed a VM operation in the meanwhile that modified the
             * thread locals of this thread. It is guaranteed that this thread sees the latest
             * values for its thread-locals because the atomic status transition above emits the
             * necessary memory barriers.
             */
            if (probability(VERY_SLOW_PATH_PROBABILITY, VMThreads.ActionOnTransitionToJavaSupport.isActionPending()) ||
                            probability(VERY_SLOW_PATH_PROBABILITY, RecurringCallbackSupport.needsNativeToJavaSlowpath())) {
                call(ENTER_SLOW_PATH_SAFEPOINT_CHECK_REGULAR_CCONV);
            }
        } else {
            call(ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS, newStatus, popFrameAnchor);

            /* Prevent reads from floating before the status transition. */
            MembarNode.memoryBarrier(MembarNode.FenceKind.NONE, LocationIdentity.ANY_LOCATION);
        }
    }

    /**
     * Tries a transition from native to VM and returns true if the transition was successful. This
     * code does not perform any safepoint checks.
     */
    @Uninterruptible(reason = "Must not contain safepoint checks")
    public static boolean tryFromNativeToVM() {
        StatusSupport.assertStatusNativeOrSafepoint();
        return StatusSupport.compareAndSetNativeToNewStatus(StatusSupport.STATUS_IN_VM);
    }

    /**
     * Does a transition from native to VM. Does a safepoint slowpath call if a safepoint is
     * currently in effect.
     */
    @Uninterruptible(reason = "Must not contain safepoint checks")
    public static void fromNativeToVM() {
        StatusSupport.assertStatusNativeOrSafepoint();
        int newStatus = StatusSupport.STATUS_IN_VM;
        boolean needSlowPath = !StatusSupport.compareAndSetNativeToNewStatus(newStatus);
        if (probability(VERY_SLOW_PATH_PROBABILITY, needSlowPath)) {
            call(ENTER_SLOW_PATH_TRANSITION_FROM_NATIVE_TO_NEW_STATUS, newStatus, false);
        }
    }

    /** Does a transition from VM to Java. This code does not perform any safepoint checks. */
    @Uninterruptible(reason = "Must not contain safepoint checks")
    public static void fromVMToJava(boolean popFrameAnchor) {
        /*
         * Change the thread status directly. Other threads won't touch the status field if it is
         * STATUS_IN_VM.
         */
        StatusSupport.assertStatusVM();
        StatusSupport.setStatusJavaUnguarded();
        if (popFrameAnchor) {
            JavaFrameAnchors.popFrameAnchor();
        }

        /* Only execute pending actions but don't do a safepoint slowpath call. */
        boolean needSlowPath = VMThreads.ActionOnTransitionToJavaSupport.isActionPending();
        if (probability(VERY_SLOW_PATH_PROBABILITY, needSlowPath)) {
            call(SLOW_PATH_RUN_PENDING_ACTIONS);
        }
    }

    /** Does a transition from Java to VM. This code does not perform any safepoint checks. */
    @Uninterruptible(reason = "Must not contain safepoint checks")
    public static void fromJavaToVM() {
        /*
         * Change the thread status directly. Other threads won't touch the status field if it is
         * STATUS_IN_VM.
         */
        StatusSupport.assertStatusJava();
        StatusSupport.setStatusVM();
    }

    /** Does a transition from VM to Native. This code does not perform any safepoint checks. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void fromVMToNative() {
        /*
         * Change the thread status directly. Other threads won't touch the status field if it is
         * STATUS_IN_VM.
         */
        StatusSupport.assertStatusVM();
        StatusSupport.setStatusNative();
    }

    @Node.NodeIntrinsic(value = ForeignCallNode.class)
    private static native void call(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor);

    @Node.NodeIntrinsic(value = ForeignCallNode.class)
    private static native void call(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, int newThreadStatus, boolean popFrameAnchor);
}
