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
import java.util.concurrent.atomic.AtomicInteger;

final class DefaultThreadLocalHandshake extends ThreadLocalHandshake {

    static final DefaultThreadLocalHandshake INSTANCE = new DefaultThreadLocalHandshake();
    private static final ThreadLocal<Boolean> DISABLED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ConcurrentHashMap<Thread, Boolean> PENDING = new ConcurrentHashMap<>();

    private static final AtomicInteger PENDING_COUNT = new AtomicInteger();
    private static final AtomicInteger DISABLED_COUNT = new AtomicInteger();

    private DefaultThreadLocalHandshake() {
    }

    @Override
    public void poll() {
        int count = PENDING_COUNT.get();
        assert count >= 0 : "inconsistent pending state " + count;
        if (count > 0) {
            pollSlowPath(count);
        }
    }

    private void pollSlowPath(int count) {
        int disabledCount = DISABLED_COUNT.get();
        assert disabledCount >= 0 : "inconsistent disabled state " + count;
        if (disabledCount > 0 && DISABLED.get()) {
            return;
        }
        if (PENDING.get(Thread.currentThread()) != null) {
            processHandshake();
        }
    }

    @Override
    protected void setPending(Thread t) {
        PENDING.compute(t, (k, p) -> {
            if (p == null) {
                PENDING_COUNT.incrementAndGet();
            }
            return Boolean.TRUE;
        });
    }

    @Override
    protected void clearPending() {
        PENDING.compute(Thread.currentThread(), (k, p) -> {
            if (p != null) {
                PENDING_COUNT.decrementAndGet();
            }
            return null;
        });
    }

    @Override
    public boolean setDisabled(boolean value) {
        boolean b = DISABLED.get();
        if (b != value) {
            DISABLED_COUNT.addAndGet(value ? 1 : -1);
            DISABLED.set(value);
        }
        return b;
    }

}
