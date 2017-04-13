/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Executor;

abstract class ComputeInExecutor<R> implements Runnable {
    private final Info executor;
    private R result;
    private Throwable exception;
    private boolean started;
    private boolean done;

    protected ComputeInExecutor(Info executor) {
        this.executor = executor;
    }

    protected abstract R compute();

    public final R get() {
        perform();
        if (executor != null) {
            waitForDone();
        }
        exceptionCheck();
        return result;
    }

    private void waitForDone() {
        synchronized (this) {
            while (!done) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    private void exceptionCheck() throws RuntimeException {
        if (exception instanceof RuntimeException) {
            if (exception instanceof IllegalStateException) {
                throw new IllegalStateException(exception);
            }
            throw (RuntimeException) exception;
        }
        if (exception != null) {
            throw new RuntimeException(exception);
        }
    }

    public final void perform() {
        if (started) {
            return;
        }
        started = true;
        if (executor == null) {
            run();
            exceptionCheck();
        } else {
            executor.execute(this);
        }
    }

    @Override
    public final void run() {
        try {
            if (executor != null) {
                executor.checkThread();
            }
            result = compute();
        } catch (Exception ex) {
            if (ex.getClass() == RuntimeException.class && ex.getCause() != null) {
                exception = ex.getCause();
            } else {
                exception = ex;
            }
        } finally {
            if (executor != null) {
                synchronized (this) {
                    done = true;
                    notifyAll();
                }
            } else {
                done = true;
            }
        }
    }

    @Override
    public final String toString() {
        return "value=" + result + ",exception=" + exception + ",computed=" + done;
    }

    public static Info wrap(Executor executor) {
        return executor == null ? null : new Info(executor);
    }

    static final class Info {
        private final Executor executor;
        private Thread runThread;

        private Info(Executor executor) {
            this.executor = executor;
        }

        void execute(ComputeInExecutor<?> compute) {
            executor.execute(compute);
        }

        private synchronized void checkThread() {
            if (runThread == null) {
                runThread = Thread.currentThread();
            } else {
                if (runThread != Thread.currentThread()) {
                    throw new IllegalStateException("Currently executing in " + Thread.currentThread() + " while previously running in " + runThread + " that isn't allowed");
                }
            }
        }
    }
}
