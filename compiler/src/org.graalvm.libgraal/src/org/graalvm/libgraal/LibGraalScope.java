/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.libgraal;

import java.lang.reflect.Method;
import java.util.Arrays;
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
     * @returns a value that can be used for the IsolateThreadContext argument of a {@code native}
     *          method {@link LibGraal#registerNativeMethods linked} to a CEntryPoint function in
     *          libgraal
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
     * current thread, the current thread is attached to libgraal. When the outer most scope is
     * closed, the current thread is detached from libgraal.
     *
     * This must be used in a try-with-resources statement.
     *
     * This cannot be called from {@linkplain LibGraal#inLibGraal() within} libgraal.
     *
     * @throws IllegalStateException if libgraal is {@linkplain LibGraal#isAvailable() unavailable}
     *             or {@link LibGraal#inLibGraal()} returns true
     */
    public LibGraalScope(DetachAction detachAction) {
        if (LibGraal.inLibGraal() || !LibGraal.isAvailable()) {
            throw new IllegalStateException();
        }
        id = nextId.getAndIncrement();
        parent = currentScope.get();
        if (parent == null) {
            long[] isolateBox = {0};
            boolean firstAttach = LibGraal.attachCurrentThread(false, isolateBox);
            long isolateAddress = isolateBox[0];
            long isolateThread = getIsolateThreadIn(isolateAddress);
            long isolateId = getIsolateId(isolateThread);
            LibGraalIsolate isolate = LibGraalIsolate.forIsolateId(isolateId, isolateAddress);
            shared = new Shared(firstAttach ? detachAction : null, isolate, isolateThread);
        } else {
            shared = parent.shared;
        }
        currentScope.set(this);
    }

    @Override
    public String toString() {
        return String.format("LibGraalScope@%d[%s, parent=%s]", id, shared, parent);
    }

    /**
     * Enters a scope for making calls into an existing libgraal isolate. If there is no existing
     * libgraal scope for the current thread, the current thread is attached to libgraal. When the
     * outer most scope is closed, the current thread is detached from libgraal.
     *
     * This must be used in a try-with-resources statement.
     *
     * This cannot be called from {@linkplain LibGraal#inLibGraal() within} libgraal.
     *
     * @throws IllegalStateException if libgraal is {@linkplain LibGraal#isAvailable() unavailable}
     *             or {@link LibGraal#inLibGraal()} returns true
     */
    public LibGraalScope(long isolateAddress) {
        if (LibGraal.inLibGraal() || !LibGraal.isAvailable()) {
            throw new IllegalStateException();
        }
        id = nextId.getAndIncrement();
        parent = currentScope.get();
        if (parent == null) {
            long isolateThread = getIsolateThreadIn(isolateAddress);
            boolean alreadyAttached;
            if (isolateThread == 0L) {
                alreadyAttached = false;
                isolateThread = attachThreadTo(isolateAddress);
            } else {
                alreadyAttached = true;
            }
            long isolateId = getIsolateId(isolateThread);
            LibGraalIsolate isolate = LibGraalIsolate.forIsolateId(isolateId, isolateAddress);
            shared = new Shared(alreadyAttached ? null : DetachAction.DETACH, isolate, isolateThread);
        } else {
            shared = parent.shared;
        }
        currentScope.set(this);
    }

    /**
     * Attaches the current thread to the isolate at {@code isolateAddress}.
     *
     * @return the address of the attached IsolateThread
     */
    // Implementation:
    // com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.attachThreadTo
    static native long attachThreadTo(long isolateAddress);

    /**
     * Detaches the current thread from the isolate at {@code isolateAddress}.
     */
    // Implementation:
    // com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.detachThreadFrom
    static native void detachThreadFrom(long isolateThreadAddress);

    /**
     * Gets the isolate thread for the current thread in the isolate at {@code isolateAddress}.
     *
     * @return 0L if the current thread is not attached to the isolate at {@code isolateAddress}
     */
    // Implementation:
    // com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.getIsolateThreadIn
    @SuppressWarnings("unused")
    static native long getIsolateThreadIn(long isolateAddress);

    /**
     * Gets an unique identifier for the current thread's isolate. The returned value is guaranteed
     * to be unique for the first {@code 2^64 - 1} isolates in the process.
     */
    // Implementation:
    // com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.getIsolateId
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

    // Shared support for the LibGraal overlays

    /**
     * Convenience function for wrapping varargs into an array for use in calls to
     * {@link #method(Class, String, Class[][])}.
     */
    static Class<?>[] sig(Class<?>... types) {
        return types;
    }

    /**
     * Returns the nesting depth of this {@code LibGraalScope} object.
     */
    public int getDepth() {
        int depth = 0;
        LibGraalScope ancestor = parent;
        while (ancestor != null) {
            depth++;
            ancestor = ancestor.parent;
        }
        return depth;
    }

    /**
     * Gets the method in {@code declaringClass} with the unique name {@code name}.
     *
     * @param sigs the signatures the method may have
     */
    static Method method(Class<?> declaringClass, String name, Class<?>[]... sigs) {
        if (sigs.length == 1 || sigs.length == 0) {
            try {
                Class<?>[] sig = sigs.length == 1 ? sigs[0] : new Class<?>[0];
                return declaringClass.getDeclaredMethod(name, sig);
            } catch (NoSuchMethodException | SecurityException e) {
                throw (NoSuchMethodError) new NoSuchMethodError(name).initCause(e);
            }
        }
        Method match = null;
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                if (match != null) {
                    throw new InternalError(String.format("Expected single method named %s, found %s and %s",
                                    name, match, m));
                }
                match = m;
            }
        }
        if (match == null) {
            throw new NoSuchMethodError("Cannot find method " + name + " in " + declaringClass.getName());
        }
        Class<?>[] parameterTypes = match.getParameterTypes();
        for (Class<?>[] sig : sigs) {
            if (Arrays.equals(parameterTypes, sig)) {
                return match;
            }
        }
        throw new NoSuchMethodError(String.format("Unexpected signature for %s: %s", name, Arrays.toString(parameterTypes)));
    }

    /**
     * Gets the method in {@code declaringClass} with the unique name {@code name} or {@code null}
     * if not found.
     *
     * @param sigs the signatures the method may have
     */
    static Method methodOrNull(Class<?> declaringClass, String name, Class<?>[]... sigs) {
        try {
            return method(declaringClass, name, sigs);
        } catch (NoSuchMethodError e) {
            return null;
        }
    }

    /**
     * Gets the method in {@code declaringClass} with the unique name {@code name} or {@code null}
     * if {@code guard == null}.
     *
     * @param sigs the signatures the method may have
     */
    static Method methodIf(Object guard, Class<?> declaringClass, String name, Class<?>[]... sigs) {
        if (guard == null) {
            return null;
        }
        return method(declaringClass, name, sigs);
    }
}
