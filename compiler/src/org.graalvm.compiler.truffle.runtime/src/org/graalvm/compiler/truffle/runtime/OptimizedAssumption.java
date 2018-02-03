/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TraceTruffleAssumptions;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TraceTruffleStackTraceLimit;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.AbstractAssumption;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

/**
 * An assumption that when {@linkplain #invalidate() invalidated} will cause all
 * {@linkplain #registerDependency() registered} dependencies to be invalidated.
 */
public final class OptimizedAssumption extends AbstractAssumption {

    /**
     * A daemon thread used to clean up {@link OptimizedAssumptionDependency} objects that may
     * become invalid by some mechanism other than {@link OptimizedAssumption#invalidate()} and
     * whose reachability is
     * {@linkplain OptimizedAssumptionDependency#unreachabilityDeterminesValidity() not correlated}
     * with the validity of the referenced machine code.
     */
    static class Cleaner extends Thread {

        private static final int CLEANING_INTERVAL_MS = 1000;

        Cleaner() {
            super("OptimizedAssumptionDependencyCleaner");
            setDaemon(true);
            setPriority(MIN_PRIORITY);
            start();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(CLEANING_INTERVAL_MS);
                } catch (InterruptedException e) {
                }
                clean();
            }
        }

        final List<Entry> deps = new ArrayList<>();

        static synchronized void add(Entry e) {
            if (CLEANER == null) {
                CLEANER = new Cleaner();
            }
            CLEANER.deps.add(e);
            // Notify that there might be something to clean
            CLEANER.notify();
        }

        synchronized void clean() {
            while (deps.isEmpty()) {
                try {
                    // Wait until there might be something to clean
                    this.wait();
                } catch (InterruptedException e) {
                }
            }
            Iterator<Entry> iter = deps.iterator();
            while (iter.hasNext()) {
                Entry e = iter.next();
                OptimizedAssumptionDependency dep = e.dependency;
                if (dep != null && !dep.isValid()) {
                    e.dependency = INVALID_DEPENDENCY;
                    iter.remove();
                }
            }
        }

        private static Cleaner CLEANER;

        /**
         * Replaces a strong reference to machine code once the code becomes invalid.
         */
        static final OptimizedAssumptionDependency INVALID_DEPENDENCY = new OptimizedAssumptionDependency() {

            @Override
            public void invalidate() {
            }

            @Override
            public boolean isValid() {
                return false;
            }

            @Override
            public String toString() {
                return "INVALID_DEPENDENCY";
            }
        };
    }

    /**
     * Reference to machine code that is dependent on an assumption.
     */
    static class Entry implements Consumer<OptimizedAssumptionDependency> {
        /**
         * A machine code reference that must be kept reachable as long as the machine code itself
         * is valid. A {@link Cleaner} is used to clear such strong references to machine code when
         * the code is invalidated by some mechanism other than
         * {@link OptimizedAssumption#invalidate()}.
         */
        volatile OptimizedAssumptionDependency dependency;

        /**
         * Machine code that is guaranteed to be invalid once the
         * {@link OptimizedAssumptionDependency} object becomes unreachable.
         */
        WeakReference<OptimizedAssumptionDependency> weakDependency;

        Entry next;

        @Override
        public void accept(OptimizedAssumptionDependency dep) {
            synchronized (this) {
                if (dep.unreachabilityDeterminesValidity()) {
                    this.weakDependency = new WeakReference<>(dep);
                } else {
                    this.dependency = dep;
                    GraalTruffleRuntime.getRuntime();
                    Cleaner.add(this);
                }
                this.notifyAll();
            }
        }

        public OptimizedAssumptionDependency awaitDependency() {
            synchronized (this) {
                while (dependency == null && weakDependency == null) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (dependency != null) {
                    return dependency;
                }
                return weakDependency.get();
            }
        }
    }

    private Entry first;

    public OptimizedAssumption(String name) {
        super(name);
    }

    @Override
    public void check() throws InvalidAssumptionException {
        if (!this.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new InvalidAssumptionException();
        }
    }

    @Override
    public void invalidate() {
        if (isValid) {
            invalidateImpl();
        }
    }

    @TruffleBoundary
    private synchronized void invalidateImpl() {
        /*
         * Check again, now that we are holding the lock. Since isValid is defined volatile,
         * double-checked locking is allowed.
         */
        if (!isValid) {
            return;
        }

        boolean invalidatedADependency = false;
        Entry e = first;
        while (e != null) {
            OptimizedAssumptionDependency dependency = e.awaitDependency();
            if (dependency != null) {
                OptimizedCallTarget callTarget = invalidateWithReason(dependency, "assumption invalidated");
                invalidatedADependency = true;
                if (TruffleCompilerOptions.getValue(TraceTruffleAssumptions)) {
                    logInvalidatedDependency(dependency);
                }
                if (callTarget != null) {
                    callTarget.getCompilationProfile().reportInvalidated();
                }
            }
            e = e.next;
        }
        first = null;
        isValid = false;

        if (TruffleCompilerOptions.getValue(TraceTruffleAssumptions)) {
            if (invalidatedADependency) {
                logStackTrace();
            }
        }
    }

    /**
     * Registers some dependent code with this object.
     *
     * @return a consumer that will be supplied the dependent code once it is available
     */
    public synchronized Consumer<OptimizedAssumptionDependency> registerDependency() {
        if (isValid) {
            Entry e = new Entry();
            e.next = first;
            first = e;
            return e;
        } else {
            return new Consumer<OptimizedAssumptionDependency>() {
                @Override
                public void accept(OptimizedAssumptionDependency dependency) {
                    if (dependency != null) {
                        invalidateWithReason(dependency, "assumption already invalidated when installing code");
                        if (TruffleCompilerOptions.getValue(TraceTruffleAssumptions)) {
                            logInvalidatedDependency(dependency);
                            logStackTrace();
                        }
                    }
                }
            };
        }
    }

    private OptimizedCallTarget invalidateWithReason(OptimizedAssumptionDependency dependency, String reason) {
        if (dependency.getCompilable() != null) {
            OptimizedCallTarget callTarget = (OptimizedCallTarget) dependency.getCompilable();
            callTarget.invalidate(this, reason);
            return callTarget;
        } else {
            dependency.invalidate();
            return null;
        }
    }

    @Override
    public boolean isValid() {
        return isValid;
    }

    private void logInvalidatedDependency(OptimizedAssumptionDependency dependency) {
        TTY.out().out().printf("assumption '%s' invalidated installed code '%s'\n", name, dependency);
    }

    private static void logStackTrace() {
        final int skip = 1;
        final int limit = TruffleCompilerOptions.getValue(TraceTruffleStackTraceLimit);
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        StringBuilder strb = new StringBuilder();
        String sep = "";
        for (int i = skip; i < stackTrace.length && i < skip + limit; i++) {
            strb.append(sep).append("  ").append(stackTrace[i].toString());
            sep = "\n";
        }
        if (stackTrace.length > skip + limit) {
            strb.append("\n    ...");
        }

        TTY.out().out().println(strb);
    }
}
