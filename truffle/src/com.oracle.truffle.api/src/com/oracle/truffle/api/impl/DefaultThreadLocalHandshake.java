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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives.Interruptable;

final class DefaultThreadLocalHandshake extends ThreadLocalHandshake {

    static final DefaultThreadLocalHandshake INSTANCE = new DefaultThreadLocalHandshake();

    /*
     * This map contains all state objects for all threads accessible for other threads. Since the
     * thread needs to be weak and synchronized is less efficient to access and is only used when
     * accessing the state of other threads.
     */
    private static final Map<Thread, ThreadLocalState> STATE_OBJECTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<ThreadLocalState> STATE = ThreadLocal.withInitial(() -> getThreadState(Thread.currentThread()));

    /*
     * Number of active pending threads. Allows to check the active threads more efficiently.
     */
    private static final AtomicInteger PENDING_COUNT = new AtomicInteger();

    private DefaultThreadLocalHandshake() {
    }

    @Override
    public void poll() {
        int count = PENDING_COUNT.get();
        assert count >= 0 : "inconsistent pending state " + count;
        if (count > 0) {
            pollSlowPath();
        }
    }

    private static void pollSlowPath() {
        ThreadLocalState s = STATE.get();
        if (s.pending && s.disabledCount == 0) {
            INSTANCE.processHandshake();
        }
    }

    private static ThreadLocalState getThreadState(Thread thread) {
        return STATE_OBJECTS.computeIfAbsent(thread, (t) -> new ThreadLocalState());
    }

    @Override
    protected void setPending(Thread t) {
        ThreadLocalState s = getThreadState(t);
        if (!s.pending) {
            Interruptable action = null;
            synchronized (s) {
                if (!s.pending) {
                    s.pending = true;
                    PENDING_COUNT.incrementAndGet();
                    action = s.interruptableAction;
                }
            }
            if (action != null) {
                action.interrupt(t);
            }
        }

    }

    @Override
    protected void clearPending() {
        ThreadLocalState r = STATE.get();
        if (r.pending) {
            Interruptable action = null;
            synchronized (r) {
                if (r.pending) {
                    r.pending = false;
                    PENDING_COUNT.decrementAndGet();
                    action = r.interruptableAction;
                }
            }
            if (action != null) {
                action.interrupted();
            }
        }
    }

    @Override
    public void setBlocked(Interruptable interruptable) {
        ThreadLocalState r = STATE.get();
        poll();
        assert r.interruptableAction == null : "Thread is already blocked. Call CompilerDirectives.clearSafepointsBlocked() first.";
        r.interruptableAction = interruptable;
    }

    @Override
    public Interruptable clearBlocked() {
        ThreadLocalState r = STATE.get();
        Interruptable interruptable = r.interruptableAction;
        assert interruptable != null : "Thread is not blocked. Call CompilerDirectives.setSafepointsBlocked first.";
        r.interruptableAction = null;
        poll();
        return interruptable;
    }

    @Override
    public void disable() {
        ThreadLocalState s = STATE.get();
        s.disabledCount++;
    }

    @Override
    public void enable() {
        ThreadLocalState s = STATE.get();
        s.disabledCount--;
        assert s.disabledCount >= 0 : "cannot enable if not disabled";
    }

    static final class ThreadLocalState {

        volatile boolean pending;
        int disabledCount;
        volatile Interruptable interruptableAction;

    }

}
