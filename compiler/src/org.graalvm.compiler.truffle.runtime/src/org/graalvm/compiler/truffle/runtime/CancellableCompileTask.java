/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.compiler.truffle.common.TruffleCompilationTask;

public final class CancellableCompileTask implements TruffleCompilationTask {
    private final WeakReference<OptimizedCallTarget> targetRef;
    private final boolean lastTierCompilation;
    private volatile Future<?> future;
    private volatile boolean cancelled;
    private volatile boolean started;

    public CancellableCompileTask(WeakReference<OptimizedCallTarget> targetRef, boolean lastTierCompilation) {
        this.targetRef = targetRef;
        this.lastTierCompilation = lastTierCompilation;
    }

    // This cannot be done in the constructor because the CancellableCompileTask needs to be
    // passed down to the compiler through a Runnable inner class.
    // This means it must be final and initialized before the future can be set.
    void setFuture(Future<?> future) {
        synchronized (this) {
            if (this.future == null) {
                this.future = future;
            } else {
                throw new IllegalStateException("The future should not be re-set.");
            }
        }
    }

    public void awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        future.get(timeout, unit);
    }

    public void awaitCompletion() throws ExecutionException, InterruptedException {
        future.get();
    }

    public synchronized boolean cancel() {
        if (!cancelled) {
            cancelled = true;
            if (!started) {
                finished();
            }
            return true;
        }
        return false;
    }

    public void finished() {
        final OptimizedCallTarget target = targetRef.get();
        if (target != null) {
            target.resetCompilationTask();
        }
    }

    public synchronized boolean start() {
        assert !started : "Should not start a stared task";
        if (cancelled) {
            return false;
        }
        started = true;
        return true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isLastTier() {
        return lastTierCompilation;
    }

    public Future<?> getFuture() {
        return future;
    }
}
