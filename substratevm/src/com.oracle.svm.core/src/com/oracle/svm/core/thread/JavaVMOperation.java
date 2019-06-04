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

import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.SubstrateUtil.Thunk;
import com.oracle.svm.core.annotate.RestrictHeapAccess;

/**
 * The abstract base class of all VM operations that are allocated on the Java heap. Allocating the
 * VM operation on the Java heap is preferable but not always possible.
 */
public abstract class JavaVMOperation extends VMOperation implements VMOperationControl.JavaAllocationFreeQueue.Element<JavaVMOperation> {
    protected IsolateThread queuingThread;
    private JavaVMOperation next;
    private volatile boolean finished;

    protected JavaVMOperation(String name, SystemEffect systemEffect) {
        super(name, systemEffect);
    }

    @Override
    public JavaVMOperation getNext() {
        return next;
    }

    @Override
    public void setNext(JavaVMOperation value) {
        assert next == null || value == null : "Must not change next abruptly.";
        next = value;
    }

    public void enqueue() {
        VMOperationControl.get().enqueue(this);
    }

    @Override
    protected IsolateThread getQueuingThread(NativeVMOperationData data) {
        return queuingThread;
    }

    @Override
    protected void setQueuingThread(NativeVMOperationData data, IsolateThread thread) {
        queuingThread = thread;
    }

    @Override
    protected boolean isFinished(NativeVMOperationData data) {
        return finished;
    }

    @Override
    protected void setFinished(NativeVMOperationData data, boolean value) {
        finished = value;
    }

    /** Convenience method for thunks that can be run by allocating a VMOperation. */
    public static void enqueueBlockingSafepoint(String name, Thunk thunk) {
        ThunkOperation vmOperation = new ThunkOperation(name, SystemEffect.CAUSES_SAFEPOINT, thunk);
        vmOperation.enqueue();
    }

    /** Convenience method for thunks that can be run by allocating a VMOperation. */
    public static void enqueueBlockingNoSafepoint(String name, Thunk thunk) {
        ThunkOperation vmOperation = new ThunkOperation(name, SystemEffect.DOES_NOT_CAUSE_SAFEPOINT, thunk);
        vmOperation.enqueue();
    }

    @Override
    public final void operate(NativeVMOperationData data) {
        operate();
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, reason = "Whitelisted because some operations may allocate.")
    protected abstract void operate();

    /** A VMOperation that executes a thunk. */
    public static class ThunkOperation extends JavaVMOperation {
        private Thunk thunk;

        ThunkOperation(String name, SystemEffect systemEffect, Thunk thunk) {
            super(name, systemEffect);
            this.thunk = thunk;
        }

        @Override
        public void operate() {
            thunk.invoke();
        }
    }
}
