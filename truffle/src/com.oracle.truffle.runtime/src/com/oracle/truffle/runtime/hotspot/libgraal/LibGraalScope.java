/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.hotspot.libgraal;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scope for calling CEntryPoints in libgraal. {@linkplain #LibGraalScope() Opening} a scope ensures
 * the current thread is attached to libgraal and {@linkplain #close() closing} the outer most scope
 * detaches the current thread.
 */
public final class LibGraalScope implements AutoCloseable {

    static final ThreadLocal<LibGraalScope> currentScope = new ThreadLocal<>();

    /**
     * Shared state between a thread's nested scopes.
     */
    static class Shared {
        final DetachAction detachAction;
        final LibGraalIsolate isolate;
        private long isolateThread;

        Shared(DetachAction detachAction, LibGraalIsolate isolate, long isolateThread) {
            this.detachAction = detachAction;
            this.isolate = isolate;
            this.isolateThread = isolateThread;
        }

        public long getIsolateThread() {
            if (isolateThread == 0L) {
                throw new IllegalStateException(Thread.currentThread() + " is no longer attached to " + isolate);
            }
            return isolateThread;
        }

        public long detach() {
            long res = getIsolateThread();
            isolateThread = 0L;
            return res;
        }

        @Override
        public String toString() {
            return String.format("isolate=%s, isolateThread=0x%x, detachAction=%s", isolate, isolateThread, detachAction);
        }
    }

    private final LibGraalScope parent;
    private final Shared shared;

    private static final AtomicInteger nextId = new AtomicInteger(1);
    private final int id;

    /**
     * Gets the current scope.
     *
     * @throws IllegalStateException if the current thread is not in an {@linkplain #LibGraalScope()
     *             opened} scope
     */
    public static LibGraalScope current() {
        LibGraalScope scope = currentScope.get();
        if (scope == null) {
            throw new IllegalStateException("Not in an " + LibGraalScope.class.getSimpleName());
        }
        return scope;
    }

    /**
     * Gets the isolate thread associated with the current thread. The current thread must be in an
     * {@linkplain #LibGraalScope() opened} scope.
     *
     * @return a value that can be used for the IsolateThreadContext argument of a {@code native}
     *         method {@link LibGraal#registerNativeMethods linked} to a CEntryPoint function in
     *         libgraal
     * @throws IllegalStateException if the current thread is not attached to libgraal
     */
    public static long getIsolateThread() {
        return current().shared.getIsolateThread();
    }

    /**
     * Denotes the detach action to perform when closing a {@link LibGraalScope}.
     */
    public enum DetachAction {
        /**
         * Detach the thread from its libgraal isolate.
         */
        DETACH,

        /**
         * Detach the thread from its libgraal isolate and the associated {@code JVMCIRuntime}.
         */
        DETACH_RUNTIME,

        /**
         * Detach the thread from its libgraal isolate and the associated {@code JVMCIRuntime}. If
         * the VM supports releasing the {@code JavaVM} associated with {@code JVMCIRuntime}s and
         * this is the last thread attached to its {@code JVMCIRuntime}, then the
         * {@code JVMCIRuntime} destroys its {@code JavaVM} instance.
         */
        DETACH_RUNTIME_AND_RELEASE
    }

    /**
     * Shortcut for calling {@link #LibGraalScope(DetachAction)} with an argument of
     * {@link DetachAction#DETACH_RUNTIME}.
     */
    public LibGraalScope() {
        this(DetachAction.DETACH_RUNTIME);
    }

    /**
     * Enters a scope for making calls into libgraal. If there is no existing libgraal scope for the
     * current thread, the current thread is attached to libgraal. When the outermost scope is
     * closed, the current thread is detached from libgraal.
     *
     * This must be used in a try-with-resources statement.
     *
     * @throws IllegalStateException if libgraal is {@linkplain LibGraal#isAvailable() unavailable}
     */
    public LibGraalScope(DetachAction detachAction) {
        if (!LibGraal.isAvailable()) {
            throw new IllegalStateException();
        }
        id = nextId.getAndIncrement();
        parent = currentScope.get();
        boolean initializeIsolate;
        if (parent == null) {
            long[] isolateBox = {0};
            boolean firstAttach = LibGraal.attachCurrentThread(false, isolateBox);
            long isolateAddress = isolateBox[0];
            long isolateThread = getIsolateThreadIn(isolateAddress);
            long isolateId = getIsolateId(isolateThread);
            initializeIsolate = firstAttach;
            LibGraalIsolate isolate = LibGraalIsolate.forIsolateId(isolateId, isolateAddress);
            shared = new Shared(firstAttach ? detachAction : null, isolate, isolateThread);
        } else {
            shared = parent.shared;
            initializeIsolate = false;
        }
        currentScope.set(this);
        if (initializeIsolate) {
            LibGraalTruffleCompilationSupport.initializeIsolate(shared.getIsolateThread());
        }
    }

    @Override
    public String toString() {
        return String.format("LibGraalScope@%d[%s, parent=%s]", id, shared, parent);
    }

    /**
     * Attaches the current thread to the isolate at {@code isolateAddress}.
     *
     * @return the address of the attached IsolateThread
     */
    // Implementation:
    // com.oracle.svm.graal.hotspot.libgraal.LibGraalLibGraalScope.attachThreadTo
    static native long attachThreadTo(long isolateAddress);

    /**
     * Detaches the current thread from the isolate at {@code isolateAddress}.
     */
    // Implementation:
    // com.oracle.svm.graal.hotspot.libgraal.LibGraalLibGraalScope.detachThreadFrom
    static native void detachThreadFrom(long isolateThreadAddress);

    /**
     * Gets the isolate thread for the current thread in the isolate at {@code isolateAddress}.
     *
     * @return 0L if the current thread is not attached to the isolate at {@code isolateAddress}
     */
    // Implementation:
    // com.oracle.svm.graal.hotspot.libgraal.LibGraalLibGraalScope.getIsolateThreadIn
    @SuppressWarnings("unused")
    static native long getIsolateThreadIn(long isolateAddress);

    /**
     * Gets an unique identifier for the current thread's isolate. The returned value is guaranteed
     * to be unique for the first {@code 2^64 - 1} isolates in the process.
     */
    // Implementation:
    // com.oracle.svm.graal.hotspot.libgraal.LibGraalLibGraalScope.getIsolateId
    private static native long getIsolateId(long isolateThreadAddress);

    /**
     * Gets the isolate associated with this scope.
     */
    public LibGraalIsolate getIsolate() {
        return shared.isolate;
    }

    /**
     * Gets the address of the isolate thread associated with this scope.
     */
    public long getIsolateThreadAddress() {
        return shared.getIsolateThread();
    }

    @Override
    public void close() {
        // Reset the currentScope thread local before detaching. Detaching may trigger HotSpot to
        // shutdown the libgraal isolate. That involves re-attaching the current thread to the
        // libgraal isolate with a *new* isolate thread for calling
        // HotSpotJVMCIRuntime.shutdown(). In the scope of the latter call, if a new LibGraalScope
        // is opened, it must not see this LibGraalScope as its parent otherwise it will use the
        // closed and discarded isolate thread (i.e. this.shared.isolateThread).
        currentScope.set(parent);
        if (parent == null && shared.detachAction != null) {
            long isolateThread = shared.detach();
            if (shared.detachAction == DetachAction.DETACH) {
                detachThreadFrom(isolateThread);
            } else {
                LibGraal.detachCurrentThread(shared.detachAction == DetachAction.DETACH_RUNTIME_AND_RELEASE);
            }
        }
    }
}
