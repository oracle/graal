/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

/**
 * Provides a skeletal implementation of the abstract {@link Isolate} class, reducing the effort
 * required to implement custom subclasses. This implementation is intended for technologies in
 * which the thread attaches once and remains attached for its entire lifetime.
 */
abstract sealed class AbstractIsolate<T extends AbstractIsolateThread> extends Isolate<T> permits NativeIsolate, ProcessIsolate {

    static final int CLOSED = -1;

    private final ThreadLocal<T> attachedIsolateThread;
    private final Collection<T> threads;      // Guarded by this

    private volatile State state;  // Guarded by this

    AbstractIsolate(ThreadLocal<T> threadLocal) {
        this.attachedIsolateThread = Objects.requireNonNull(threadLocal, "ThreadLocal must be non-null.");
        this.threads = new ArrayList<>();
        this.state = State.ACTIVE;
    }

    abstract T attachCurrentThread();

    abstract void detachCurrentThread(T currentThread);

    abstract void callTearDownHook();

    abstract boolean doIsolateShutdown(T shutdownThread);

    @Override
    public final T enter() {
        T isolateThread = getOrCreateIsolateThread();
        if (isolateThread != null && isolateThread.enter()) {
            return isolateThread;
        } else {
            throw throwClosedException();
        }
    }

    @Override
    public final T tryEnter() {
        try {
            T isolateThread = getOrCreateIsolateThread();
            if (isolateThread != null && isolateThread.enter()) {
                return isolateThread;
            } else {
                return null;
            }
        } catch (IsolateDeathException ide) {
            return null;
        }
    }

    @Override
    public final boolean isActive() {
        T isolateThread = attachedIsolateThread.get();
        return isolateThread != null && (isolateThread.isForeignThread() || isolateThread.isActive());
    }

    @Override
    public final boolean shutdown() {
        T currentIsolateThread = attachedIsolateThread.get();
        if (currentIsolateThread != null && currentIsolateThread.isForeignThread()) {
            return false;
        }
        boolean deferredClose = false;
        synchronized (this) {
            if (state == State.DISPOSED) {
                return true;
            }
            state = State.DISPOSING;
            for (T isolateThread : threads) {
                deferredClose |= !isolateThread.invalidate();
            }
        }
        if (deferredClose) {
            return false;
        } else {
            return doIsolateShutdown();
        }
    }

    @Override
    public final boolean isDisposed() {
        return state == State.DISPOSED;
    }

    final boolean isAttached() {
        return attachedIsolateThread.get() != null;
    }

    final synchronized void registerForeignThread(T foreignThread) {
        if (!state.isValid()) {
            throw throwClosedException();
        }
        threads.add(foreignThread);
        attachedIsolateThread.set(foreignThread);
    }

    final void lastLeave() {
        synchronized (this) {
            for (T isolateThread : threads) {
                if (isolateThread.isActive()) {
                    return;
                }
            }
        }
        doIsolateShutdown();
    }

    RuntimeException throwClosedException() {
        throw new IllegalStateException("Isolate " + this + " is already closed.");
    }

    private boolean doIsolateShutdown() {
        synchronized (this) {
            if (state == State.DISPOSED) {
                return true;
            }
            state = State.DISPOSED;
        }
        cleaners.clear();
        boolean success;

        T isolateThread = attachedIsolateThread.get();
        if (isolateThread == null) {
            try {
                isolateThread = attachCurrentThread();
            } catch (IsolateDeathException ide) {
                /*
                 * The isolate has crashed. Invoke at least the tear down hook to allow the host
                 * code to perform cleanup.
                 */
                callTearDownHook();
                return false;
            }
            isolateThread.invalidate();
            attachedIsolateThread.set(isolateThread);
        }
        isolateThread.setShutDownRequest(true);
        try {
            callTearDownHook();
            success = doIsolateShutdown(isolateThread);
        } finally {
            isolateThread.setShutDownRequest(false);
        }
        return success;
    }

    private T getOrCreateIsolateThread() {
        T isolateThread = attachedIsolateThread.get();
        if (isolateThread == null) {
            synchronized (this) {
                if (!state.isValid()) {
                    return null;
                }
                isolateThread = attachCurrentThread();
                threads.add(isolateThread);
                attachedIsolateThread.set(isolateThread);
            }
        }
        return isolateThread;
    }

    @Override
    public final void detachCurrentThread() {
        synchronized (this) {
            T isolateThread = attachedIsolateThread.get();
            if (state.isValid() && isolateThread != null) {
                if (!isolateThread.isForeignThread()) {
                    detachCurrentThread(isolateThread);
                }
                attachedIsolateThread.set(null);
                threads.remove(isolateThread);
            }
        }
    }

    private enum State {

        ACTIVE,
        DISPOSING,
        DISPOSED;

        boolean isValid() {
            return this == ACTIVE;
        }
    }
}
