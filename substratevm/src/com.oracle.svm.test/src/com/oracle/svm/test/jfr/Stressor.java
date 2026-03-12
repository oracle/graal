/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Class to help run multiple threads executing some task.
 */
public class Stressor {
    public static void execute(int numberOfThreads, Runnable task) throws Throwable {
        Thread[] threads = new Thread[numberOfThreads];
        StoreUncaughtException[] uncaughtExceptions = new StoreUncaughtException[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(task);
            uncaughtExceptions[i] = new StoreUncaughtException();
            threads[i].setUncaughtExceptionHandler(uncaughtExceptions[i]);
        }

        for (int i = 0; i < numberOfThreads; i++) {
            threads[i].start();
        }

        for (int i = 0; i < numberOfThreads; i++) {
            try {
                threads[i].join();

                /* Rethrow any uncaught exceptions. */
                Throwable throwable = uncaughtExceptions[i].get();
                if (throwable != null) {
                    throw throwable;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class StoreUncaughtException implements Thread.UncaughtExceptionHandler {
        private final AtomicReference<Throwable> throwable = new AtomicReference<>();

        public Throwable get() {
            return throwable.get();
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            this.throwable.set(e);
        }
    }
}
