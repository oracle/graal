/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;

final class ContextStoreProfile {

    private static final ContextStore UNINTIALIZED_STORE = new ContextStore(0);

    private final Assumption dynamicStoreAssumption = Truffle.getRuntime().createAssumption("constant context store");
    private final Assumption constantStoreAssumption = Truffle.getRuntime().createAssumption("dynamic context store");

    @CompilationFinal private ContextStore constantStore;

    private volatile ContextStore dynamicStore = UNINTIALIZED_STORE;
    private volatile Thread dynamicStoreThread;

    private final ThreadLocal<ContextStore> threadStore = new ThreadLocal<>();

    ContextStoreProfile(ContextStore initialStore) {
        this.constantStore = initialStore;
    }

    ContextStore get() {
        // can be used on the fast path
        ContextStore store;
        if (constantStoreAssumption.isValid()) {
            // single context per engine
            store = constantStore;
        } else if (dynamicStoreAssumption.isValid()) {
            // multiple context single thread
            store = dynamicStore;
        } else {
            // multiple context multiple threads
            store = getThreadLocalStore();
        }
        return store;
    }

    void enter(ContextStore store) {
        assert store != null;
        // fast path
        if (constantStore == store) {
            return;
        }

        // fast path single thread
        if (Thread.currentThread() == dynamicStoreThread) {
            dynamicStore = store;
            return;
        }

        // fast path multiple threads
        ContextStore tlstore = threadStore.get();
        if (tlstore != null) {
            if (tlstore != store) {
                threadStore.set(tlstore);
            }
            return;
        }

        // everything else
        slowPathProfile(store);
    }

    @TruffleBoundary
    private synchronized void slowPathProfile(ContextStore store) {
        if (constantStoreAssumption.isValid()) {
            if (constantStore == UNINTIALIZED_STORE) {
                constantStore = store;
                return;
            } else {
                assert constantStore != store;
                constantStore = null;
                constantStoreAssumption.invalidate();
            }
        }
        if (dynamicStoreAssumption.isValid()) {
            Thread currentThread = Thread.currentThread();
            assert currentThread != dynamicStoreThread;
            if (dynamicStore == UNINTIALIZED_STORE) {
                dynamicStoreThread = currentThread;
                dynamicStore = store;
                return;
            } else {
                dynamicStore = null;
                dynamicStoreThread = null;
                dynamicStoreAssumption.invalidate();
            }
        }

        assert !constantStoreAssumption.isValid();
        assert !dynamicStoreAssumption.isValid();

        // ensure cleaned up speculation
        assert dynamicStoreThread == null;
        assert constantStore == null;
        assert dynamicStore == null;

        threadStore.set(store);
    }

    @TruffleBoundary
    private ContextStore getThreadLocalStore() {
        return threadStore.get();
    }

}
