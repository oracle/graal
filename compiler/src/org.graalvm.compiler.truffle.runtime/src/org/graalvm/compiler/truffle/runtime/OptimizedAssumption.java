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

    private static class Entry implements Consumer<OptimizedAssumptionDependency> {
        WeakReference<OptimizedAssumptionDependency> dependencyRef;
        Entry next;

        @Override
        public void accept(OptimizedAssumptionDependency dependency) {
            synchronized (this) {
                this.dependencyRef = new WeakReference<>(dependency);
                this.notifyAll();
            }
        }

        public OptimizedAssumptionDependency awaitDependency() {
            synchronized (this) {
                while (dependencyRef == null) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return dependencyRef.get();
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
