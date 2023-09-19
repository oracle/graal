/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.hotspot;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.nodes.Node;

final class HotSpotThreadLocalHandshake extends ThreadLocalHandshake {

    private static final sun.misc.Unsafe UNSAFE = HotSpotTruffleRuntime.UNSAFE;
    static final HotSpotThreadLocalHandshake SINGLETON = new HotSpotThreadLocalHandshake();
    private static final ThreadLocal<TruffleSafepointImpl> STATE = ThreadLocal.withInitial(() -> SINGLETON.getThreadState(Thread.currentThread()));

    private static final int PENDING_OFFSET = HotSpotTruffleRuntime.getRuntime().getJVMCIReservedLongOffset0();
    private static final long THREAD_EETOP_OFFSET;
    static {
        try {
            THREAD_EETOP_OFFSET = HotSpotTruffleRuntime.getObjectFieldOffset(Thread.class.getDeclaredField("eetop"));
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    @Override
    protected boolean isSupported() {
        return handshakeSupported();
    }

    static boolean handshakeSupported() {
        return PENDING_OFFSET != -1;
    }

    @Override
    public void poll(Node enclosingNode) {
        if (handshakeSupported()) {
            long eetop = UNSAFE.getLong(Thread.currentThread(), THREAD_EETOP_OFFSET);
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY,
                            UNSAFE.getInt(null, eetop + PENDING_OFFSET) != 0)) {
                processHandshake(enclosingNode);
            }
        }
    }

    static void doHandshake(Object node) {
        assert handshakeSupported() : "must not call doHandshake if handshake is not supported.";
        SINGLETON.processHandshake((Node) node);
    }

    @Override
    protected void setFastPending(Thread t) {
        assert handshakeSupported() : "must not call setFastPending if handshake is not supported.";
        if (handshakeSupported()) {
            setVolatile(t, PENDING_OFFSET, 1);
        }
    }

    @Override
    @TruffleBoundary
    public TruffleSafepointImpl getCurrent() {
        return STATE.get();
    }

    @Override
    protected void clearFastPending() {
        assert handshakeSupported() : "must not call clearFastPending if handshake is not supported.";
        if (handshakeSupported()) {
            setVolatile(Thread.currentThread(), PENDING_OFFSET, 0);
        }
    }

    private static void setVolatile(Thread t, int offset, int value) {
        /*
         * The thread will not go away here because the Truffle implementation ensures that this
         * method is no longer used if the thread is no longer active. It only sets this state for
         * contexts that are currently entered on a thread. Being entered implies that the thread is
         * active.
         */
        assert t.isAlive() : "thread must remain alive while setting fast pending";
        long eetop = UNSAFE.getLong(t, THREAD_EETOP_OFFSET);
        UNSAFE.putIntVolatile(null, eetop + offset, value);
    }

}
