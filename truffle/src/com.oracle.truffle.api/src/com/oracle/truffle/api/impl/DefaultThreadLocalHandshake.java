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

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;

final class DefaultThreadLocalHandshake extends ThreadLocalHandshake {

    static final DefaultThreadLocalHandshake SINGLETON = new DefaultThreadLocalHandshake();
    private static final ThreadLocal<TruffleSafepointImpl> STATE = new ThreadLocal<>();

    /*
     * Number of active pending threads. Allows to check the active threads more efficiently.
     */
    private static final AtomicInteger PENDING_COUNT = new AtomicInteger();

    private DefaultThreadLocalHandshake() {
    }

    @Override
    public void ensureThreadInitialized() {
        STATE.set(getThreadState(Thread.currentThread()));
    }

    @Override
    public void poll(Node enclosingNode) {
        int count = PENDING_COUNT.get();
        assert count >= 0 : "inconsistent pending state";
        if (count > 0) {
            SINGLETON.processHandshake(enclosingNode);
        }
    }

    @Override
    public TruffleSafepointImpl getCurrent() {
        TruffleSafepointImpl state = STATE.get();
        if (state == null) {
            throw CompilerDirectives.shouldNotReachHere("Thread local handshake is not initialized for this thread. " +
                            "Did you call getCurrent() outside while a polyglot context not entered?");
        }
        return state;
    }

    @Override
    protected void setFastPending(Thread t) {
        PENDING_COUNT.incrementAndGet();
    }

    @Override
    protected void clearFastPending() {
        PENDING_COUNT.decrementAndGet();
    }

}
