/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shared;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.thread.VMOperationControl.isDedicatedVMOperationThread;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.heap.AbstractPinnedObjectSupport;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess.Access;
import com.oracle.svm.core.heap.VMOperationInfo;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.core.thread.ThreadStatusTransition;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.DuplicatedInNativeCode;

/**
 * GC-related VM operations can be scheduled by any isolate thread (including the VM operation
 * thread), or any unattached GC-internal thread.
 *
 * <p>
 * Executing a GC-related VM operation involves a rather complex mechanism. So, avoid changes if
 * possible, or be prepared to redesign the whole mechanism (changes are prone to race conditions
 * and deadlocks).
 *
 * <p>
 * If an isolate thread wants to queue a VM operation while it is in C++ code (and therefore in
 * {@link StatusSupport#STATUS_IN_VM}), it needs to execute the following steps:
 * <ol>
 * <li>The queuing thread allocates all necessary data structures (e.g.,
 * {@link NativeGCVMOperationData}) on the C++-side in native memory.</li>
 * <li>The queuing thread does a status transition to {@link StatusSupport#STATUS_IN_NATIVE}.</li>
 * <li>The queuing thread calls {@link #enqueue} (via one of its callers that is annotated with
 * {@link CEntryPoint}, see usages) to queue a non-blocking VM operation that wraps the GC VM
 * operation (see {@link NativeGCVMOperationWrapper}). For queuing this VM operation, the thread
 * needs to acquire the VM operation mutex and might get blocked. This is fine as we already did a
 * transition to {@link StatusSupport#STATUS_IN_NATIVE} before.</li>
 * <li>After queuing the VM operation, the queuing thread immediately returns to C++ code. No thread
 * transition is necessary as the thread has still {@link StatusSupport#STATUS_IN_NATIVE}.</li>
 * <li>Back in C++ code, the queuing thread waits until the VM operation thread starts executing the
 * VM operation wrapper.</li>
 * <li>The queuing thread executes the VM operation prologue which may acquire locks on the C++
 * side. Because we explicitly blocked the VM operation thread, it is guaranteed that locks (such as
 * the {@code Heap_lock}) can be acquired eventually.</li>
 * <li>The VM operation thread requests a safepoint and executes the main part of the GC-related VM
 * operation while all Java threads are frozen. After the main part was executed, the safepoint is
 * released.</li>
 * <li>The queuing thread changes its thread status to {@link StatusSupport#STATUS_IN_VM} to prevent
 * unexpected safepoints. This is necessary because it must be possible to transport uninitialized
 * memory from the VM operation back to the queuing thread while ensuring that the GC does not see
 * the uninitialized memory.</li>
 * <li>After that, the VM operation thread may finish executing the wrapper VM operation at any
 * time.</li>
 * <li>The queuing thread executes the VM operation epilogue.</li>
 * <li>The queuing thread waits until the execution of the VM operation wrapper finished.</li>
 * <li>The queuing thread frees the native data structures that were allocated as it is now
 * guaranteed that the VM operation thread won't access them anymore.</li>
 * </ol>
 *
 * <p>
 * For unattached GC threads, the mechanism is slightly simpler as the thread state transitions are
 * not necessary. Besides that, pretty much the whole mechanism is the same.
 *
 * <p>
 * In Native Image, the VM operation thread is a normal Java thread. So, it can happen that for
 * example a GC is needed while some non-GC-related VM operation is already in progress. Below is a
 * detailed list of steps that the VM operation thread executes in that case:
 * <ol>
 * <li>The VM operation thread is in C++ code and has {@link StatusSupport#STATUS_IN_VM} (e.g.,
 * currently in the allocation slowpath).</li>
 * <li>The VM operation thread allocates all necessary data structures (e.g.,
 * {@link NativeGCVMOperationData}) on the C++ side in native memory.</li>
 * <li>The VM operation thread calls one of the VM operation {@link CEntryPoint} methods (see usages
 * of {@link #enqueue}).</li>
 * <li>The VM operation thread does a transition to {@link StatusSupport#STATUS_IN_JAVA}.</li>
 * <li>The VM operation thread executes the VM operation prologue.</li>
 * <li>The VM operation thread initiates a safepoint if the surrounding VM operation didn't already
 * start one.</li>
 * <li>The VM operation thread executes the main part of the VM operation.</li>
 * <li>The VM operation thread releases the safepoint if one was initiated before.</li>
 * <li>The VM operation thread executes the VM operation epilogue.</li>
 * <li>The VM operation thread does a transition to {@link StatusSupport#STATUS_IN_VM}.</li>
 * <li>The VM operation thread returns to the C++ code.</li>
 * <li>The VM operation thread frees the native data structures that were allocated before.</li>
 * </ol>
 */
public class NativeGCVMOperationSupport {
    private static final NativeGCVMOperationWrapper VM_OPERATION_WRAPPER = new NativeGCVMOperationWrapper();

    public final CEntryPointLiteral<CFunctionPointer> funcUpdateVMOperationExecutionStatus;
    public final CEntryPointLiteral<CFunctionPointer> funcWaitForVMOperationExecutionStatus;
    public final CEntryPointLiteral<CFunctionPointer> funcIsVMOperationFinished;

    @Platforms(Platform.HOSTED_ONLY.class)
    public NativeGCVMOperationSupport() {
        funcUpdateVMOperationExecutionStatus = CEntryPointLiteral.create(NativeGCVMOperationSupport.class, "updateVMOperationExecutionStatus",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationWrapperData.class, int.class);
        funcWaitForVMOperationExecutionStatus = CEntryPointLiteral.create(NativeGCVMOperationSupport.class, "waitForVMOperationExecutionStatus",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationWrapperData.class, int.class);
        funcIsVMOperationFinished = CEntryPointLiteral.create(NativeGCVMOperationSupport.class, "isVMOperationFinished",
                        Isolate.class, IsolateThread.class, NativeGCVMOperationWrapperData.class);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void updateVMOperationExecutionStatus(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationWrapperData data,
                    int status) {
        NativeGCVMOperationWrapper.updateExecutionStatus(data, status);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static void waitForVMOperationExecutionStatus(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationWrapperData data,
                    int minStatus) {
        NativeGCVMOperationWrapper.waitForStatusUpdate(data, minStatus);
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = NoEpilogue.class)
    public static boolean isVMOperationFinished(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread isolateThread, NativeGCVMOperationWrapperData data) {
        return data.getFinished();
    }

    @Uninterruptible(reason = "Can be called from an unattached thread.")
    public static void enqueue(NativeGCVMOperation op, NativeGCVMOperationData data, NativeGCVMOperationWrapperData wrapperData) {
        data.setNativeVMOperation(op);
        if (VMOperationControl.isDedicatedVMOperationThread()) {
            /* The VM operation thread needs to execute the VM operation directly. */
            ThreadStatusTransition.fromVMToJava(false);
            executeDirectly(op, data);
            ThreadStatusTransition.fromJavaToVM();
        } else {
            wrapperData.setNativeVMOperation(VM_OPERATION_WRAPPER);
            wrapperData.setVMOperationData(data);
            VM_OPERATION_WRAPPER.enqueueFromNonJavaThread(wrapperData);
        }
    }

    @Uninterruptible(reason = "Bridge between uninterruptible and interruptible code.", calleeMustBe = false)
    private static void executeDirectly(NativeGCVMOperation op, NativeGCVMOperationData data) {
        assert StatusSupport.isStatusJava() : "Thread must be in Java state to execute interruptible code";
        assert VMOperationControl.isDedicatedVMOperationThread();

        /* Execute the prologue in the non-blocking part of the VM operation thread. */
        if (!op.executePrologue(data)) {
            return;
        }

        /* Queue the blocking VM operation. */
        assert op.getCausesSafepoint();
        op.enqueue(data);

        /* Execute the epilogue in the non-blocking part. */
        op.executeEpilogue(data);
    }

    /**
     * A non-blocking VM operation that wraps a blocking GC-related VM operation and deals with the
     * necessary synchronization between SVM and C++.
     *
     * This has to be a {@link NativeVMOperation} as every queued instance needs a separate state
     * (see {@link NativeGCVMOperationWrapperData}).
     */
    @DuplicatedInNativeCode
    private static class NativeGCVMOperationWrapper extends NativeVMOperation {
        private static final int BLOCK_VM_THREAD = 0;
        private static final int EXECUTE_PROLOGUE = BLOCK_VM_THREAD + 1;
        private static final int EXECUTE_VM_OPERATION = EXECUTE_PROLOGUE + 1;
        private static final int CANCELLED = EXECUTE_VM_OPERATION + 1;
        private static final int ADJUST_THREAD_STATUS = CANCELLED + 1;
        private static final int FINISHED = ADJUST_THREAD_STATUS + 1;

        private static final VMMutex EXECUTION_STATUS_MUTEX = new VMMutex("vmOpExecutionStatus");
        private static final VMCondition EXECUTION_STATUS_CONDITION = new VMCondition(EXECUTION_STATUS_MUTEX);

        protected NativeGCVMOperationWrapper() {
            super(VMOperationInfos.get(NativeGCVMOperationWrapper.class, "Native GC VM operation wrapper", SystemEffect.NONE));
        }

        @Override
        @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Used to implement GC functionality.")
        protected void operate(NativeVMOperationData data) {
            assert RecurringCallbackSupport.isCallbackUnsupportedOrTimerSuspended();
            operate0((NativeGCVMOperationWrapperData) data);
        }

        private static void operate0(NativeGCVMOperationWrapperData wrapperData) {
            assert wrapperData.getExecutionStatus() == BLOCK_VM_THREAD;
            assert !isDedicatedVMOperationThread(wrapperData.getQueuingThread());
            assert !VMOperation.isInProgressAtSafepoint() : "GC-related VM operation that need this kind of synchronization must start their own safepoint";

            /* Notify C++ code that the VM operation thread is in place. */
            updateExecutionStatus(wrapperData, EXECUTE_PROLOGUE);

            /* Wait until C++ code executed the prologue. */
            waitForStatusUpdate(wrapperData, EXECUTE_VM_OPERATION);

            if (wrapperData.getExecutionStatus() != CANCELLED) {
                assert wrapperData.getExecutionStatus() == EXECUTE_VM_OPERATION;

                NativeGCVMOperationData opData = wrapperData.getVMOperationData();
                NativeGCVMOperation op = (NativeGCVMOperation) opData.getNativeVMOperation();

                /* Execute the blocking part of the VM operation. */
                assert op.getCausesSafepoint();
                op.enqueue(opData);

                /* Notify C++ code that the VM operation was executed. */
                updateExecutionStatus(wrapperData, ADJUST_THREAD_STATUS);

                /* Wait until the C++ code changed the thread status to VM. */
                waitForStatusUpdate(wrapperData, FINISHED);
            }
        }

        @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
        static void updateExecutionStatus(NativeGCVMOperationWrapperData data, int status) {
            EXECUTION_STATUS_MUTEX.lockNoTransitionUnspecifiedOwner();
            try {
                data.setExecutionStatus(status);
                EXECUTION_STATUS_CONDITION.broadcast();
            } finally {
                EXECUTION_STATUS_MUTEX.unlockNoTransitionUnspecifiedOwner();
            }
        }

        @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
        static void waitForStatusUpdate(NativeGCVMOperationWrapperData data, int minStatus) {
            EXECUTION_STATUS_MUTEX.lockNoTransitionUnspecifiedOwner();
            try {
                while (data.getExecutionStatus() < minStatus) {
                    EXECUTION_STATUS_CONDITION.blockNoTransitionUnspecifiedOwner();
                }
            } finally {
                EXECUTION_STATUS_MUTEX.unlockNoTransitionUnspecifiedOwner();
            }
        }
    }

    /**
     * Stack allocated on the C++ side but only accessed by Native Image. We can't stack allocate
     * the structure on the Native Image side because the stack is destroyed when we return to C++
     * after queuing the VM operation.
     */
    @RawStructure
    public interface NativeGCVMOperationWrapperData extends NativeVMOperationData {
        @RawField
        int getExecutionStatus();

        @RawField
        void setExecutionStatus(int value);

        @RawField
        NativeGCVMOperationData getVMOperationData();

        @RawField
        void setVMOperationData(NativeGCVMOperationData value);
    }

    public abstract static class NativeGCVMOperation extends NativeVMOperation {
        private final boolean isGC;

        @Platforms(Platform.HOSTED_ONLY.class)
        protected NativeGCVMOperation(VMOperationInfo info, boolean isGC) {
            super(info);
            this.isGC = isGC;
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public boolean isGC() {
            return isGC;
        }

        public abstract boolean executePrologue(NativeGCVMOperationData data);

        @Override
        protected void operate(NativeVMOperationData data) {
            /* Cleanup obsolete pinned objects before calling into C++. */
            AbstractPinnedObjectSupport.singleton().removeClosedObjectsAndGetFirstOpenObject();
            operate0((NativeGCVMOperationData) data);
        }

        protected abstract void operate0(NativeGCVMOperationData data);

        public abstract void executeEpilogue(NativeGCVMOperationData data);
    }

    /**
     * {@link NativeGCVMOperationData} structs are always allocated on the C++ side. Note that the
     * C++ code uses pointer arithmetics and cast to convert this into a C++ {@code VM_Operation}.
     */
    @RawStructure
    public interface NativeGCVMOperationData extends NativeVMOperationData {
    }
}
