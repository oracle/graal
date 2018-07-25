/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil.Thunk;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.Safepoint.SafepointException;
import com.oracle.svm.core.util.VMError;

/** The abstract base class of all VM operations. */
public abstract class VMOperation extends VMOperationControl.AllocationFreeStack.Element<VMOperation> {

    /** An identifier for the VMOperation. */
    private final String name;

    /** A VMOperation either blocks the caller or it does not. */
    public enum CallerEffect {
        DOES_NOT_BLOCK_CALLER,
        BLOCKS_CALLER
    }

    private final CallerEffect callerEffect;

    /** A VMOperation either causes a safepoint or it does not. */
    public enum SystemEffect {
        DOES_NOT_CAUSE_SAFEPOINT,
        CAUSES_SAFEPOINT
    }

    private final SystemEffect systemEffect;

    /**
     * The VMThread of the thread that queued this VMOperation. Useful if the operation needs to
     * update thread-local state in the queuing thread.
     */
    private IsolateThread queuingVMThread;

    /**
     * The thread that is currently executing this VMOperation, or NULL if the operation is
     * currently not being executed.
     */
    private IsolateThread executingVMThread;

    /** Constructor for sub-classes. */
    protected VMOperation(String name, CallerEffect callerEffect, SystemEffect systemEffect) {
        super();
        this.name = name;
        this.callerEffect = callerEffect;
        this.systemEffect = systemEffect;
        /*
         * TODO: Currently I am running VMOperations on the thread of the caller, so all
         * VMOperations block the caller.
         */
        assert callerEffect == CallerEffect.BLOCKS_CALLER : "Only blocking calls are implemented";
    }

    /** Public interface: Queue the operation for execution. */
    public final void enqueue() {
        try {
            if (!SubstrateOptions.MultiThreaded.getValue()) {
                // If I am single-threaded, I can just execute the operation.
                execute();
            } else {
                // If I am multi-threaded, then I have to bring the system to a safepoint, etc.
                setQueuingVMThread(CEntryPointContext.getCurrentIsolateThread());
                VMOperationControl.enqueue(this);
                setQueuingVMThread(WordFactory.nullPointer());
            }
        } catch (SafepointException se) {
            /* This exception is intended to be thrown from safepoint checks, at one's own risk */
            throw rethrow(se.inner);
        }
    }

    @SuppressWarnings({"unchecked"})
    static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    /** Convenience method for thunks that can be run by allocating a VMOperation. */
    public static void enqueueBlockingSafepoint(String name, Thunk thunk) {
        ThunkOperation vmOperation = new ThunkOperation(name, CallerEffect.BLOCKS_CALLER, SystemEffect.CAUSES_SAFEPOINT, thunk);
        vmOperation.enqueue();
    }

    /** Convenience method for thunks that can be run by allocating a VMOperation. */
    public static void enqueueBlockingNoSafepoint(String name, Thunk thunk) {
        ThunkOperation vmOperation = new ThunkOperation(name, CallerEffect.BLOCKS_CALLER, SystemEffect.DOES_NOT_CAUSE_SAFEPOINT, thunk);
        vmOperation.enqueue();
    }

    /** What it means to execute an operation. */
    protected final void execute() {
        try {
            operateUnderIndicator();
        } catch (Throwable t) {
            Log.log().string("[VMOperation.execute caught: ").string(t.getClass().getName()).string("]").newline();
            throw VMError.shouldNotReachHere(t);
        }
    }

    /*
     * TODO: This method should be annotated with {@link MustNotSynchronize}, but too many methods
     * would have to be white-listed to make that practical.
     */
    private void operateUnderIndicator() {
        final VMOperationControl control = ImageSingletons.lookup(VMOperationControl.class);
        final VMOperation previousInProgress = control.getInProgress();
        try {
            executingVMThread = CEntryPointContext.getCurrentIsolateThread();
            control.setInProgress(this);
            operate();
        } finally {
            control.setInProgress(previousInProgress);
            executingVMThread = WordFactory.nullPointer();
        }
    }

    public static boolean isInProgress() {
        VMOperation cur = ImageSingletons.lookup(VMOperationControl.class).getInProgress();
        return cur != null && cur.executingVMThread == CEntryPointContext.getCurrentIsolateThread();
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

    /*
     * Methods for sub-classes to override
     */

    /** Do whatever it is that this VM operation does. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, reason = "Whitelisted because some operations may allocate.")
    protected abstract void operate();

    /*
     * Field access methods.
     */

    protected final String getName() {
        return name;
    }

    final boolean getBlocksCaller() {
        return callerEffect == CallerEffect.BLOCKS_CALLER;
    }

    final boolean getCausesSafepoint() {
        return systemEffect == SystemEffect.CAUSES_SAFEPOINT;
    }

    protected final IsolateThread getQueuingVMThread() {
        return queuingVMThread;
    }

    private void setQueuingVMThread(IsolateThread vmThread) {
        queuingVMThread = vmThread;
    }

    final IsolateThread getExecutingVMThread() {
        return executingVMThread;
    }

    /** A VMOperation that executes a thunk. */
    public static class ThunkOperation extends VMOperation {

        private Thunk thunk;

        ThunkOperation(String name, CallerEffect callerEffect, SystemEffect systemEffect, Thunk thunk) {
            super(name, callerEffect, systemEffect);
            this.thunk = thunk;
        }

        @Override
        public void operate() {
            thunk.invoke();
        }
    }
}
