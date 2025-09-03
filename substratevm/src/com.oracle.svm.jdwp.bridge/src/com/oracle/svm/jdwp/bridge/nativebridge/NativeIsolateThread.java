/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge.nativebridge;

import static com.oracle.svm.jdwp.bridge.nativebridge.NativeIsolate.CLOSED;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an entered isolate thread.
 *
 * @see NativeIsolate#enter()
 */
public final class NativeIsolateThread {

    private static final int CLOSING_MASK = 0b1;

    private final NativeIsolate isolate;
    private final WeakReference<Thread> thread;
    private final AtomicInteger enteredCount;
    private final boolean nativeThread;
    final long isolateThread;
    private boolean executesShutDown;

    NativeIsolateThread(Thread thread, NativeIsolate isolate, boolean nativeThread, long isolateThreadId) {
        this.thread = new WeakReference<>(thread);
        this.isolate = isolate;
        this.nativeThread = nativeThread;
        this.isolateThread = isolateThreadId;
        this.enteredCount = new AtomicInteger();
    }

    /**
     * Returns the isolate thread address.
     *
     * @throws IllegalStateException when the {@link NativeIsolateThread} is no more entered.
     */
    public long getIsolateThreadId() {
        assert verifyThread();
        if (!isActive()) {
            throw new IllegalStateException("Isolate 0x" + Long.toHexString(isolate.getIsolateId()) + " is not entered.");
        }
        return isolateThread;
    }

    /**
     * Returns the isolate for this thread.
     */
    public NativeIsolate getIsolate() {
        return isolate;
    }

    /**
     * Leaves the {@link NativeIsolate} on the current thread.
     */
    public void leave() {
        assert verifyThread();
        decrementAttached();
    }

    boolean enter() {
        assert verifyThread();
        return incrementAttached();
    }

    boolean invalidate() {
        while (true) { // TERMINATION ARGUMENT: busy loop
            int value = enteredCount.get();
            if (value == CLOSED) {
                return true;
            }
            int numberOfAttached = (value >>> 1);
            boolean inactive = numberOfAttached == 0;
            int newValue = inactive ? CLOSED : (value | CLOSING_MASK);
            if (enteredCount.compareAndSet(value, newValue)) {
                return inactive;
            }
        }
    }

    boolean isActive() {
        if (executesShutDown) {
            return true;
        }
        int value = enteredCount.get();
        return value != CLOSED && (value >>> 1) > 0;
    }

    boolean isNativeThread() {
        return nativeThread;
    }

    void setShutDownRequest(boolean shutDown) {
        this.executesShutDown = shutDown;
    }

    private boolean verifyThread() {
        assert thread.get() == Thread.currentThread() : String.format(
                        "NativeIsolateThread used by other thread. Expected thread %s, actual thread %s.",
                        thread.get(),
                        Thread.currentThread());
        return true;
    }

    private boolean incrementAttached() {
        while (true) { // TERMINATION ARGUMENT: busy loop
            int value = enteredCount.get();
            if (value == CLOSED) {
                if (executesShutDown) {
                    return true;
                } else {
                    return false;
                }
            }
            int closing = (value & CLOSING_MASK);
            int newValue = (((value >>> 1) + 1) << 1) | closing;
            if (enteredCount.compareAndSet(value, newValue)) {
                break;
            }
        }
        return true;
    }

    private void decrementAttached() {
        while (true) { // TERMINATION ARGUMENT: busy loop
            int value = enteredCount.get();
            if (value == CLOSED) {
                if (executesShutDown) {
                    return;
                } else {
                    throw new IllegalStateException("Isolate 0x" + Long.toHexString(isolate.getIsolateId()) + " was closed while being active.");
                }
            }
            int closing = (value & CLOSING_MASK);
            int numberOfAttached = (value >>> 1) - 1;
            boolean lastLeaving = closing == CLOSING_MASK && numberOfAttached == 0;
            int newValue = lastLeaving ? CLOSED : ((numberOfAttached << 1) | closing);
            if (enteredCount.compareAndSet(value, newValue)) {
                if (lastLeaving) {
                    isolate.lastLeave();
                }
                break;
            }
        }
    }
}
