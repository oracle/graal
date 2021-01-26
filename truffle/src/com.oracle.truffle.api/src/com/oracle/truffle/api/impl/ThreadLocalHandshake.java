/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public abstract class ThreadLocalHandshake {

    private static final ConcurrentHashMap<Thread, Handshake> PENDING = new ConcurrentHashMap<>();

    protected ThreadLocalHandshake() {
    }

    public abstract void poll();

    protected abstract void setPending(Thread t);

    protected abstract void clearPending();

    public abstract boolean setDisabled(boolean value);

    /**
     * If this method is invoked the thread must be guaranteed to be polled. If the thread dies and
     * {@link #poll()} was not invoked then an {@link IllegalStateException} is thrown;
     *
     * @param target
     * @param threads
     * @param run
     */
    @TruffleBoundary
    public final void runThreadLocal(CallTarget target, Thread[] threads, Runnable run) {
        for (int i = 0; i < threads.length; i++) {
            Thread t = threads[i];
            if (!t.isAlive()) {
                throw new IllegalStateException("Thread no longer alive with pending handshake.");
            }
            PENDING.compute(t, (thread, p) -> {
                return new Handshake(run, p);
            });
            setPending(t);
        }
    }

    @TruffleBoundary
    protected final void processHandshake() {
        Throwable ex = null;
        while (true) {
            clearPending();
            Handshake handshake = PENDING.remove(Thread.currentThread());
            if (handshake == null) {
                break;
            }
            ex = combineThrowable(ex, handshake.process());
        }

        if (ex != null) {
            throw sneakyThrow(ex);
        }
    }

    private static Throwable combineThrowable(Throwable current, Throwable t) {
        if (current == null) {
            return t;
        }
        t.addSuppressed(current);
        return t;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    static final class Handshake {

        final Runnable action;
        final Handshake next;

        private volatile Handshake prev;

        Handshake(Runnable action, Handshake next) {
            this.action = action;
            if (next != null) {
                next.prev = this;
            }
            this.next = next;
        }

        Throwable process() {
            /*
             * Retain schedule order and process next first. Schedule order is important for events
             * that perform synchronization between multiple threads to avoid deadlocks.
             *
             * We use a prev pointer to avoid the use of recursion to avoid arbitrary deep stacks in
             * safepoints.
             */
            Handshake current = this;
            while (current.next != null) {
                current = current.next;
            }

            assert current != null;
            Throwable ex = null;
            while (current != null) {
                try {
                    current.action.run();
                } catch (Throwable e) {
                    ex = combineThrowable(ex, e);
                } finally {
                    current = current.prev;
                }
            }
            return ex;
        }

    }

}
