/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.nodes.Node;

final class HotSpotThreadLocalHandshake extends ThreadLocalHandshake {

    private static final sun.misc.Unsafe UNSAFE = AbstractHotSpotTruffleRuntime.UNSAFE;
    static final HotSpotThreadLocalHandshake INSTANCE = new HotSpotThreadLocalHandshake();
    private static final ThreadLocal<TruffleSafepointImpl> STATE = ThreadLocal.withInitial(() -> INSTANCE.getThreadState(Thread.currentThread()));

    private static final int PENDING_OFFSET = AbstractHotSpotTruffleRuntime.getRuntime().getThreadLocalPendingHandshakeOffset();
    private static final long THREAD_EETOP_OFFSET;
    static {
        try {
            THREAD_EETOP_OFFSET = UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("eetop"));
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    @Override
    public void poll(Node enclosingNode) {
        long eetop = UNSAFE.getLong(Thread.currentThread(), THREAD_EETOP_OFFSET);
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY,
                        UNSAFE.getInt(null, eetop + PENDING_OFFSET) != 0)) {
            processHandshake(enclosingNode);
        }
    }

    static void doHandshake(Node node) {
        INSTANCE.processHandshake(node);
    }

    @Override
    protected void setPending(Thread t) {
        setVolatile(t, PENDING_OFFSET, 1);
    }

    @Override
    public TruffleSafepointImpl getCurrent() {
        return STATE.get();
    }

    @Override
    protected void clearPending() {
        setVolatile(Thread.currentThread(), PENDING_OFFSET, 0);
    }

    private static int setVolatile(Thread t, int offset, int value) {
        long eetop = UNSAFE.getLong(t, THREAD_EETOP_OFFSET);
        int prev;
        do {
            prev = UNSAFE.getIntVolatile(null, eetop + offset);
        } while (!UNSAFE.compareAndSwapInt(null, eetop + offset, prev, value));
        return prev;
    }

}
