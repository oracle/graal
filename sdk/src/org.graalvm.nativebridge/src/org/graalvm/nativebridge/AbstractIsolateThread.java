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

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.graalvm.nativebridge.NativeIsolate.CLOSED;

/**
 * Provides a skeletal implementation of the abstract {@link IsolateThread} class, reducing the
 * effort required to implement custom subclasses. This implementation is intended for technologies
 * in which the thread attaches once and remains attached for its entire lifetime.
 */
abstract sealed class AbstractIsolateThread extends IsolateThread permits NativeIsolateThread, ProcessIsolateThread {

    private static final int CLOSING_MASK = 0b1;

    private final WeakReference<Thread> thread;
    private final AtomicInteger enteredCount;
    private final boolean foreignThread;
    private boolean executesShutDown;

    AbstractIsolateThread(Thread thread, boolean foreignThread) {
        Objects.requireNonNull(thread, "Thread must be non-null");
        this.thread = new WeakReference<>(thread);
        this.foreignThread = foreignThread;
        this.enteredCount = new AtomicInteger();
    }

    @Override
    public abstract AbstractIsolate<?> getIsolate();

    @Override
    public final void leave() {
        assert verifyThread();
        decrementAttached();
    }

    final boolean enter() {
        assert verifyThread();
        return incrementAttached();
    }

    final boolean invalidate() {
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

    final boolean isActive() {
        if (executesShutDown) {
            return true;
        }
        int value = enteredCount.get();
        return value != CLOSED && (value >>> 1) > 0;
    }

    final boolean isForeignThread() {
        return foreignThread;
    }

    final void setShutDownRequest(boolean shutDown) {
        this.executesShutDown = shutDown;
    }

    final boolean verifyThread() {
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
                return executesShutDown;
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
                    throw new IllegalStateException("Isolate " + getIsolate() + " was closed while being active.");
                }
            }
            int closing = (value & CLOSING_MASK);
            int numberOfAttached = (value >>> 1) - 1;
            boolean lastLeaving = closing == CLOSING_MASK && numberOfAttached == 0;
            int newValue = lastLeaving ? CLOSED : ((numberOfAttached << 1) | closing);
            if (enteredCount.compareAndSet(value, newValue)) {
                if (lastLeaving) {
                    getIsolate().lastLeave();
                }
                break;
            }
        }
    }
}
