/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util;

import java.util.concurrent.*;

import com.sun.max.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * Compute given functions in always one and the same single thread.
 *
 * This is for example necessary in Linux where one and
 * the same thread needs to be reused to represent the whole process in certain contexts,
 * e.g. when using the ptrace interface.
 */
public class SingleThread extends Thread {

    private static Thread worker;
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable runnable) {
            ProgramError.check(worker == null, "Single worker thread died unexpectedly");
            worker = new Thread(runnable);
            worker.setName("SingleThread");
            return worker;
        }
    });

    private static final boolean disabled = false;

    public static <V> V execute(Function<V> function) {
        if (disabled || Thread.currentThread() == worker) {
            try {
                return function.call();
            } catch (Exception exception) {
                throw ProgramError.unexpected(exception);
            }
        }
        synchronized (executorService) {
            final Future<V> future = executorService.submit(function);
            while (true) {
                try {
                    return future.get();
                } catch (ExecutionException e) {
                    throw Utils.cast(RuntimeException.class, e.getCause());
                } catch (InterruptedException exception) {
                    // continue
                }
            }
        }
    }

    public static <V> V executeWithException(Function<V> function) throws Exception {
        if (disabled || Thread.currentThread() == worker) {
            return function.call();
        }
        synchronized (executorService) {
            final Future<V> future = executorService.submit(function);
            while (true) {
                try {
                    return future.get();
                } catch (ExecutionException e) {
                    final Throwable cause = e.getCause();
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    throw ProgramError.unexpected(cause);
                } catch (InterruptedException exception) {
                    // continue
                }
            }
        }
    }

    public static void execute(final Runnable runnable) {
        if (disabled || Thread.currentThread() == worker) {
            runnable.run();
        }
        synchronized (executorService) {
            final Future<?> future = executorService.submit(runnable);
            while (true) {
                try {
                    future.get();
                    return;
                } catch (ExecutionException e) {
                    final Throwable cause = e.getCause();
                    throw ProgramError.unexpected(cause);

                } catch (InterruptedException exception) {
                    // continue
                }
            }
        }
    }
}
