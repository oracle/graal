/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.VMOperationInfo;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.events.ExecuteVMOperationEvent;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperationControl.OpInProgress;
import com.oracle.svm.core.util.VMError;

/**
 * Only one thread at a time can execute {@linkplain VMOperation}s (see
 * {@linkplain VMOperationControl}). While executing a VM operation, it is guaranteed that the
 * yellow zone is enabled and that recurring callbacks are paused. This is necessary to avoid
 * unexpected exceptions while executing critical code.
 *
 * No Java synchronization is allowed within a VMOperation. See
 * {@link VMOperationControl#guaranteeOkayToBlock} for examples of how using synchronization within
 * a VMOperation can cause a deadlock.
 */
public abstract class VMOperation {
    private final VMOperationInfo info;

    protected VMOperation(VMOperationInfo info) {
        assert info.getVMOperationClass() == this.getClass();
        this.info = info;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final int getId() {
        return info.getId();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final String getName() {
        return info.getName();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isGC() {
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final boolean getCausesSafepoint() {
        return info.getCausesSafepoint();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final boolean isBlocking() {
        return info.isBlocking();
    }

    protected final void execute(NativeVMOperationData data) {
        assert VMOperationControl.mayExecuteVmOperations();
        assert !isFinished(data);

        final Log trace = SubstrateOptions.TraceVMOperations.getValue() ? Log.log() : Log.noopLog();
        if (!hasWork(data)) {
            /*
             * The caller already does some filtering but it can still happen that we reach this
             * code even though no work needs to be done.
             */
            trace.string("[Skipping operation ").string(getName()).string("]");
            return;
        }

        VMOperationControl control = ImageSingletons.lookup(VMOperationControl.class);
        VMOperation prevOperation = control.getInProgress().getOperation();
        IsolateThread prevQueuingThread = control.getInProgress().getQueuingThread();
        IsolateThread prevExecutingThread = control.getInProgress().getExecutingThread();
        IsolateThread requestingThread = getQueuingThread(data);

        control.setInProgress(this, requestingThread, CurrentIsolate.getCurrentThread(), true);
        long startTicks = JfrTicks.elapsedTicks();
        try {
            trace.string("[Executing operation ").string(getName());
            operate(data);
            trace.string("]");
        } catch (Throwable t) {
            trace.string("[VMOperation.execute caught: ").string(t.getClass().getName()).string("]").newline();
            throw VMError.shouldNotReachHere(t);
        } finally {
            ExecuteVMOperationEvent.emit(this, getQueuingThreadId(data), startTicks);
            control.setInProgress(prevOperation, prevQueuingThread, prevExecutingThread, false);
        }
    }

    /**
     * Returns true if the current thread is in the middle of executing a VM operation. Note that
     * this includes VM operations that do not need a safepoint.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInProgress() {
        OpInProgress inProgress = VMOperationControl.get().getInProgress();
        return isInProgress(inProgress);
    }

    /**
     * Returns true if the current thread is in the middle of executing a VM operation that needs a
     * safepoint.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInProgressAtSafepoint() {
        OpInProgress inProgress = VMOperationControl.get().getInProgress();
        return isInProgress(inProgress) && inProgress.operation.getCausesSafepoint();
    }

    /**
     * Returns true if the current thread is in the middle of executing a VM operation. Note that
     * this includes VM operations that do not need a safepoint.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static boolean isInProgress(OpInProgress inProgress) {
        return inProgress.getExecutingThread() == CurrentIsolate.getCurrentThread();
    }

    /** Returns true if the current thread is in the middle of performing a garbage collection. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isGCInProgress() {
        VMOperation op = VMOperationControl.get().getInProgress().getOperation();
        return op != null && op.isGC();
    }

    /**
     * Throws a fatal error if the current thread is in the middle of executing a VM operation. Note
     * that this includes VM operations that do not need a safepoint.
     */
    public static void guaranteeNotInProgress(String message) {
        if (isInProgress()) {
            throw VMError.shouldNotReachHere(message);
        }
    }

    /**
     * Verifies that the current thread is in the middle of executing a VM operation that needs a
     * safepoint.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void guaranteeInProgressAtSafepoint(String message) {
        if (!isInProgressAtSafepoint()) {
            throw VMError.shouldNotReachHere(message);
        }
    }

    /** Verifies that the current thread is in the middle of performing a garbage collection. */
    public static void guaranteeGCInProgress(String message) {
        if (!isGCInProgress()) {
            throw VMError.shouldNotReachHere(message);
        }
    }

    /**
     * Used to determine if a VM operation must be executed or if it can be skipped. Regardless of
     * the {@linkplain SystemEffect} that was specified for the VM operation, this method might be
     * called before or after a safepoint was initiated.
     */
    protected boolean hasWork(@SuppressWarnings("unused") NativeVMOperationData data) {
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void markAsQueued(NativeVMOperationData data);

    protected abstract void markAsFinished(NativeVMOperationData data);

    protected abstract IsolateThread getQueuingThread(NativeVMOperationData data);

    protected abstract long getQueuingThreadId(NativeVMOperationData data);

    protected abstract boolean isFinished(NativeVMOperationData data);

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Whitelisted because some operations may allocate.")
    protected abstract void operate(NativeVMOperationData data);

    public enum SystemEffect {
        NONE,
        SAFEPOINT;

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean getCausesSafepoint(SystemEffect value) {
            return value == SAFEPOINT;
        }
    }
}
