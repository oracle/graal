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
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperationControl.OpInProgress;
import com.oracle.svm.core.util.VMError;

/**
 * Only one thread at a time can execute {@linkplain VMOperation}s (see
 * {@linkplain VMOperationControl}). While executing a VM operation, it is guaranteed that the
 * yellow zone is enabled and that recurring callbacks are paused. This is necessary to avoid
 * unexpected exceptions while executing critical code.
 */
public abstract class VMOperation {
    private final String name;
    private final SystemEffect systemEffect;

    protected VMOperation(String name, SystemEffect systemEffect) {
        this.name = name;
        this.systemEffect = systemEffect;
    }

    public final String getName() {
        return name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected boolean isGC() {
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final boolean getCausesSafepoint() {
        return systemEffect == SystemEffect.SAFEPOINT;
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
            trace.string("[Skipping operation ").string(name).string("]");
            return;
        }

        VMOperationControl control = ImageSingletons.lookup(VMOperationControl.class);
        VMOperation prevOperation = control.getInProgress().getOperation();
        IsolateThread prevQueuingThread = control.getInProgress().getQueuingThread();
        IsolateThread prevExecutingThread = control.getInProgress().getExecutingThread();

        control.setInProgress(this, getQueuingThread(data), CurrentIsolate.getCurrentThread());
        try {
            trace.string("[Executing operation ").string(name);
            operate(data);
            trace.string("]");
        } catch (Throwable t) {
            trace.string("[VMOperation.execute caught: ").string(t.getClass().getName()).string("]").newline();
            throw VMError.shouldNotReachHere(t);
        } finally {
            control.setInProgress(prevOperation, prevQueuingThread, prevExecutingThread);
        }
    }

    /**
     * Returns true if the current thread is currently executing a VM operation.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInProgress() {
        OpInProgress inProgress = VMOperationControl.get().getInProgress();
        return isInProgress(inProgress);
    }

    /**
     * Returns true if the current thread is currently executing a VM operation that causes a
     * safepoint.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInProgressAtSafepoint() {
        OpInProgress inProgress = VMOperationControl.get().getInProgress();
        return isInProgress(inProgress) && inProgress.operation.getCausesSafepoint();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isInProgress(OpInProgress inProgress) {
        return inProgress.getExecutingThread() == CurrentIsolate.getCurrentThread();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isGCInProgress() {
        VMOperation op = VMOperationControl.get().getInProgress().getOperation();
        return op != null && op.isGC();
    }

    /** Check that there is a VMOperation in progress. */
    public static void guaranteeInProgress(String message) {
        if (!isInProgress()) {
            throw VMError.shouldNotReachHere(message);
        }
    }

    /** Check that there is not a VMOperation in progress. */
    public static void guaranteeNotInProgress(String message) {
        if (isInProgress()) {
            throw VMError.shouldNotReachHere(message);
        }
    }

    public static void guaranteeInProgressAtSafepoint(String message) {
        if (!isInProgressAtSafepoint()) {
            throw VMError.shouldNotReachHere(message);
        }
    }

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

    protected abstract IsolateThread getQueuingThread(NativeVMOperationData data);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void setQueuingThread(NativeVMOperationData data, IsolateThread value);

    protected abstract boolean isFinished(NativeVMOperationData data);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void setFinished(NativeVMOperationData data, boolean value);

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, reason = "Whitelisted because some operations may allocate.")
    protected abstract void operate(NativeVMOperationData data);

    public enum SystemEffect {
        NONE,
        SAFEPOINT
    }
}
