/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;

/** Continuation implementation <em>independent of</em> Project Loom. */
public final class Continuations {
    private static final Thread.UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER = (t, e) -> {
    };

    /**
     * A pool with the maximum number of threads so we can likely start a platform thread for each
     * virtual thread, which we might need when blocking I/O does not yield.
     */
    static final ForkJoinPool SCHEDULER = new ForkJoinPool(32767, CarrierThread::new, UNCAUGHT_EXCEPTION_HANDLER, true);

    public static ThreadFactory virtualThreadFactory() {
        return VirtualThread::new;
    }

    private Continuations() {
    }
}

final class VirtualThread extends Thread {
    private final Continuation cont;
    private final Runnable runContinuation;

    VirtualThread(Runnable r) {
        super(r);
        this.cont = new Continuation(r);
        this.runContinuation = this::runContinuation;
    }

    @Override
    public void start() {
        submit();
    }

    private void submit() {
        Continuations.SCHEDULER.execute(runContinuation);
    }

    private void runContinuation() {
        try {
            cont.enter();
        } finally {
            if (!cont.isDone()) {
                submit();
            }
        }
    }
}

final class CarrierThread extends ForkJoinWorkerThread {
    CarrierThread(ForkJoinPool pool) {
        super(pool);
    }
}
