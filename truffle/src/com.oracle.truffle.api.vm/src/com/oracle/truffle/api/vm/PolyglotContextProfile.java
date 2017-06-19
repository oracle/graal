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
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;

final class PolyglotContextProfile {

    // single context per engines
    private final Assumption constantStoreAssumption = Truffle.getRuntime().createAssumption("dynamic context store");
    private final Assumption dynamicStoreAssumption = Truffle.getRuntime().createAssumption("constant context store");

    @CompilationFinal private WeakReference<PolyglotContextImpl> constantStore;
    @CompilationFinal private final AtomicInteger constantEntered = new AtomicInteger(0);

    private volatile PolyglotContextImpl dynamicStore;
    @CompilationFinal private volatile Thread singleThread;

    private volatile ThreadLocal<PolyglotContextImpl> threadStore;

    PolyglotContextProfile() {
        this.constantStore = new WeakReference<>(null);
    }

    Assumption getConstantStoreAssumption() {
        return constantStoreAssumption;
    }

    PolyglotContextImpl get() {
        // can be used on the fast path
        PolyglotContextImpl store;
        if (constantStoreAssumption.isValid()) {
            // we can skip the constantEntered check in compiled code, because we are assume we are
            // always entered in such cases.
            store = (CompilerDirectives.inCompiledCode() || constantEntered.get() > 0) ? constantStore.get() : null;
        } else if (dynamicStoreAssumption.isValid()) {
            // multiple context single thread
            store = dynamicStore;
        } else {
            // multiple context multiple threads
            store = getThreadLocalStore(threadStore);
        }
        return store;
    }

    void leave(PolyglotContextImpl prev) {
        // only constant stores should not be cleared as they use a compilation final weak
        // reference.
        if (constantStoreAssumption.isValid()) {
            constantEntered.incrementAndGet();
        } else if (dynamicStoreAssumption.isValid()) {
            dynamicStore = prev;
            assert singleThread == Thread.currentThread();
        } else {
            ThreadLocal<PolyglotContextImpl> tlstore = threadStore;
            assert tlstore != null;
            setThreadLocalStore(tlstore, prev);
        }
    }

    PolyglotContextImpl enter(PolyglotContextImpl store) {
        assert store != null;
        if (constantStoreAssumption.isValid()) {
            if (constantStore.get() == store) {
                constantEntered.incrementAndGet();
                return null;
            }
        } else if (dynamicStoreAssumption.isValid()) {
            PolyglotContextImpl prevStore = dynamicStore;
            if (Thread.currentThread() == singleThread) {
                dynamicStore = store;
                return prevStore;
            }
        } else {
            // fast path multiple threads
            ThreadLocal<PolyglotContextImpl> tlstore = threadStore;
            assert tlstore != null;
            PolyglotContextImpl currentstore = getThreadLocalStore(tlstore);
            if (currentstore != store) {
                setThreadLocalStore(tlstore, store);
            }
            return currentstore;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return slowPathProfile(store);
    }

    @TruffleBoundary
    private static void setThreadLocalStore(ThreadLocal<PolyglotContextImpl> tlstore, PolyglotContextImpl store) {
        tlstore.set(store);
    }

    @TruffleBoundary
    private synchronized PolyglotContextImpl slowPathProfile(PolyglotContextImpl engine) {
        PolyglotContextImpl prev = null;
        if (constantStoreAssumption.isValid()) {
            if (constantStore.get() == null) {
                constantStore = new WeakReference<>(engine);
                singleThread = Thread.currentThread();
                constantEntered.incrementAndGet();
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
                final PolyglotContextImpl initialEngine = dynamicStore == null ? prev : dynamicStore;
                threadStore = new ThreadLocal<PolyglotContextImpl>() {
                    @Override
                    protected PolyglotContextImpl initialValue() {
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
    private static PolyglotContextImpl getThreadLocalStore(ThreadLocal<PolyglotContextImpl> tls) {
        return tls.get();
    }

}
