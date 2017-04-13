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

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;

final class PolyglotEngineProfile {

    // single context per engines
    private final Assumption constantStoreAssumption = Truffle.getRuntime().createAssumption("dynamic context store");
    private final Assumption dynamicStoreAssumption = Truffle.getRuntime().createAssumption("constant context store");

    @CompilationFinal private WeakReference<PolyglotEngine> constantStore;
    @CompilationFinal private volatile boolean constantEntered;

    private volatile PolyglotEngine dynamicStore;
    @CompilationFinal private volatile Thread singleThread;

    private volatile ThreadLocal<PolyglotEngine> threadStore;

    PolyglotEngineProfile(PolyglotEngine initialStore) {
        this.constantStore = new WeakReference<>(initialStore);
    }

    Assumption getConstantStoreAssumption() {
        return constantStoreAssumption;
    }

    PolyglotEngine get() {
        // can be used on the fast path
        PolyglotEngine store;
        if (constantStoreAssumption.isValid()) {
            // we can skip the constantEntered check in compiled code, because we are assume we are
            // always entered in such cases.
            // TODO logic is temporarily disabled due to behavior that depends on it
            // store = (CompilerDirectives.inCompiledCode() || constantEntered) ?
            // constantStore.get() : null;
            store = constantStore.get();
        } else if (dynamicStoreAssumption.isValid()) {
            // multiple context single thread
            store = dynamicStore;
        } else {
            // multiple context multiple threads
            store = getThreadLocalStore(threadStore);
        }
        return store;
    }

    void leave(PolyglotEngine prev) {
        // only constant stores should not be cleared as they use a compilation final weak
        // reference.
        if (constantStoreAssumption.isValid()) {
            assert prev == null;
            constantEntered = false;
        } else if (dynamicStoreAssumption.isValid()) {
            dynamicStore = prev;
            assert singleThread == Thread.currentThread();
        } else {
            ThreadLocal<PolyglotEngine> tlstore = threadStore;
            assert tlstore != null;
            setThreadLocalStore(tlstore, prev);
        }
    }

    PolyglotEngine enter(PolyglotEngine store) {
        assert store != null;
        if (constantStoreAssumption.isValid()) {
            if (constantStore.get() == store) {
                constantEntered = true;
                return null;
            }
        } else if (dynamicStoreAssumption.isValid()) {
            PolyglotEngine prevStore = dynamicStore;
            if (Thread.currentThread() == singleThread) {
                dynamicStore = store;
                return prevStore;
            }
        } else {
            // fast path multiple threads
            ThreadLocal<PolyglotEngine> tlstore = threadStore;
            assert tlstore != null;
            PolyglotEngine currentstore = getThreadLocalStore(tlstore);
            if (currentstore != store) {
                setThreadLocalStore(tlstore, store);
            }
            return currentstore;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return slowPathProfile(store);
    }

    @TruffleBoundary
    private static void setThreadLocalStore(ThreadLocal<PolyglotEngine> tlstore, PolyglotEngine store) {
        tlstore.set(store);
    }

    @TruffleBoundary
    private synchronized PolyglotEngine slowPathProfile(PolyglotEngine engine) {
        PolyglotEngine prev = null;
        if (constantStoreAssumption.isValid()) {
            if (constantStore.get() == null) {
                constantStore = new WeakReference<>(engine);
                singleThread = Thread.currentThread();
                constantEntered = true;
                return null;
            } else {
                constantStoreAssumption.invalidate();
                prev = constantStore.get();
                constantStore.clear();
            }
        }
        if (dynamicStoreAssumption.isValid()) {
            Thread currentThread = Thread.currentThread();
            if (dynamicStore == null && singleThread == currentThread) {
                dynamicStore = engine;
                return prev;
            } else {
                final PolyglotEngine initialEngine = dynamicStore == null ? prev : dynamicStore;
                threadStore = new ThreadLocal<PolyglotEngine>() {
                    @Override
                    protected PolyglotEngine initialValue() {
                        return initialEngine;
                    }
                };
                threadStore.set(engine);
                dynamicStoreAssumption.invalidate();
                prev = initialEngine;
            }
        }
        dynamicStore = null;

        assert !constantStoreAssumption.isValid();
        assert !dynamicStoreAssumption.isValid();

        // ensure cleaned up speculation
        assert threadStore != null;
        return prev;
    }

    @TruffleBoundary
    private static PolyglotEngine getThreadLocalStore(ThreadLocal<PolyglotEngine> tls) {
        return tls.get();
    }

}
