/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.library.test;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.junit.Test;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

public class ParallelInitTest {

    static final int THREADS = 20;
    static final Semaphore AFTER_CINIT = new Semaphore(0);
    static final Semaphore BEFORE_USE = new Semaphore(0);
    static final Semaphore AFTER_USE = new Semaphore(0);
    static final CountDownLatch ALL_STARTED = new CountDownLatch(THREADS - 1);

    /*
     * If the class loading was not triggered by the test class we should not lock.
     */
    static volatile boolean testStarted = false;
    /*
     * If the class was already initialized otherwise we should not run the test.
     */
    static volatile boolean initialized = false;

    @GenerateLibrary
    public abstract static class ParallelInitLibrary extends Library {
        static final LibraryFactory<ParallelInitLibrary> FACTORY;

        static {
            if (testStarted) {
                try {
                    BEFORE_USE.acquire();
                } catch (InterruptedException e) {
                    throw new AssertionError();
                }
            }
            FACTORY = LibraryFactory.resolve(ParallelInitLibrary.class);
            assertNotNull(FACTORY.getUncached());
            assertNotNull(FACTORY);
            initialized = true;
        }

        public String m0(@SuppressWarnings("unused") Object r) {
            return "m0";
        }
    }

    @Test
    public void testInitDeadlock() throws InterruptedException, ExecutionException {
        if (initialized) {
            // test cannot be repeated
            System.out.println("Warning: ParallelInitTest cannot be repeated.");
            return;
        }
        testStarted = true;

        ExecutorService service = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int index = i;
                futures.add(service.submit(() -> {
                    if (index == 7) {
                        loadLibraryClass();
                    } else {
                        useInitLibrary(index);
                    }
                }));
            }
            ALL_STARTED.await();
            BEFORE_USE.release(THREADS);
            AFTER_CINIT.release();
            for (Future<?> future : futures) {
                future.get();
            }

        } finally {
            service.shutdown();
        }
    }

    @SuppressWarnings("unused")
    static void loadLibraryClass() {
        ParallelInitLibrary.FACTORY.getUncached().m0("");
    }

    static void useInitLibrary(@SuppressWarnings("unused") int i) {
        ALL_STARTED.countDown();
        try {
            BEFORE_USE.acquire();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        LibraryFactory.resolve(ParallelInitLibrary.class).getUncached().m0("");
    }

}
