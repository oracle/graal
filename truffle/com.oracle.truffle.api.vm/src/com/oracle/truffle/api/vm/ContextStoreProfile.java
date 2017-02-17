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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;

final class ContextStoreProfile {

    private static final ContextStore UNINTIALIZED_STORE = new ContextStore(null, 0);

    private final Assumption dynamicStoreAssumption = Truffle.getRuntime().createAssumption("constant context store");
    private final Assumption constantStoreAssumption = Truffle.getRuntime().createAssumption("dynamic context store");
    private final Assumption seenDynamicStore = Truffle.getRuntime().createAssumption("constant context store");
    private final Assumption seenThreadLocalStore = Truffle.getRuntime().createAssumption("constant context store");

    @CompilationFinal private ContextStore constantStore;

    private volatile ContextStore dynamicStore = UNINTIALIZED_STORE;
    private volatile Thread singleThread;

    private volatile ThreadLocal<ContextStore> threadStore;

    ContextStoreProfile(ContextStore initialStore) {
        this.constantStore = initialStore == null ? UNINTIALIZED_STORE : initialStore;
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
            store = getThreadLocalStore(threadStore);
        }
        return store;
    }

    void enter(ContextStore store) {
        assert store != null;
        // fast path
        if (constantStoreAssumption.isValid() && constantStore == store) {
            return;
        }

        if (seenDynamicStore.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            seenDynamicStore.invalidate();
        }

        // fast path single thread
        if (dynamicStoreAssumption.isValid() && Thread.currentThread() == singleThread && dynamicStore != UNINTIALIZED_STORE) {
            dynamicStore = store;
            return;
        }

        if (seenThreadLocalStore.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            seenThreadLocalStore.invalidate();
        }

        // fast path multiple threads
        ThreadLocal<ContextStore> tlstore = threadStore;
        if (tlstore != null) {
            ContextStore currentstore = getThreadLocalStore(tlstore);
            if (currentstore != store) {
                setThreadLocalStore(tlstore, store);
            }
            return;
        }

        // everything else
        slowPathProfile(store);
    }

    @TruffleBoundary
    private static void setThreadLocalStore(ThreadLocal<ContextStore> tlstore, ContextStore store) {
        tlstore.set(store);
    }

    @TruffleBoundary
    private synchronized void slowPathProfile(ContextStore store) {

        if (constantStoreAssumption.isValid()) {
            if (constantStore == UNINTIALIZED_STORE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constantStore = store;
                singleThread = Thread.currentThread();
                return;
            } else {
                constantStoreAssumption.invalidate();
            }
        }
        if (dynamicStoreAssumption.isValid()) {
            Thread currentThread = Thread.currentThread();
            if (dynamicStore == UNINTIALIZED_STORE && singleThread == currentThread) {
                dynamicStore = store;
                return;
            } else {
                final ContextStore initialStore = dynamicStore == UNINTIALIZED_STORE ? constantStore : dynamicStore;
                threadStore = new ThreadLocal<ContextStore>() {
                    @Override
                    protected ContextStore initialValue() {
                        return initialStore;
                    }
                };
                threadStore.set(store);
                dynamicStoreAssumption.invalidate();
            }
        }
        constantStore = null;
        dynamicStore = null;
        singleThread = null;

        assert !constantStoreAssumption.isValid();
        assert !dynamicStoreAssumption.isValid();

        // ensure cleaned up speculation
        assert threadStore != null;
    }

    @TruffleBoundary
    private static ContextStore getThreadLocalStore(ThreadLocal<ContextStore> tls) {
        return tls.get();
    }

}
