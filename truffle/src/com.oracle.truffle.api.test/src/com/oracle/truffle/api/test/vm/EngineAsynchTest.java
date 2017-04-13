/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import java.util.concurrent.Executors;

import org.junit.Test;

import com.oracle.truffle.api.vm.PolyglotEngine;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

public class EngineAsynchTest extends EngineTest {
    @Test
    public void marker() {
    }

    @Override
    protected Thread forbiddenThread() {
        return Thread.currentThread();
    }

    @Override
    protected PolyglotEngine.Builder createBuilder() {
        return PolyglotEngine.newBuilder().executor(new RecurrentExecutor(Executors.newSingleThreadExecutor()));
    }

    private static class RecurrentExecutor implements Executor {
        private final Executor delegate;
        private final Thread worker;

        RecurrentExecutor(Executor delegate) {
            this.delegate = delegate;
            final Thread[] arr = {null};
            CountDownLatch cdl = new CountDownLatch(1);
            delegate.execute(() -> {
                arr[0] = Thread.currentThread();
                cdl.countDown();
            });
            try {
                cdl.await();
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
            this.worker = arr[0];
        }

        @Override
        public void execute(Runnable command) {
            if (Thread.currentThread() == worker) {
                command.run();
            } else {
                delegate.execute(command);
            }
        }

    }
}
