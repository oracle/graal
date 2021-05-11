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
package com.oracle.truffle.api.dsl.test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Regression test for GR-30689. Tries to trigger race condition between a read of the field storing
 * the CachedLibrary in the generated "execute" method and write of that field in the generated
 * "executeAndSpecialize" method. Note that the race was reproducible only on JDK8.
 */
public class CachedDataRaceTest {
    private static final int TEST_REPETITIONS = 1000;
    // Don't go too crazy, if JVM reports lots of available threads
    private static final int MAX_THREADS = 8;

    public abstract static class TestNodeBase extends Node {
        public abstract Object execute(Object obj);
    }

    public abstract static class TestCachedLibNode extends TestNodeBase {
        @Specialization(limit = "20")
        protected Object readDirect(Object obj,
                        @CachedLibrary("obj") InteropLibrary dylib) {
            return dylib.isBoolean(obj);
        }
    }

    public static final class CachedNode extends Node {
        public final Object field;
        public final Object another;

        public CachedNode(Object field, Object another) {
            this.field = field;
            this.another = another;
        }
    }

    public abstract static class TestCachedNodeNode extends TestNodeBase {
        @Specialization(guards = "node.field == obj", limit = "20")
        protected Object readDirect(Object obj,
                        @Cached("new(obj, obj)") CachedNode node) {
            return obj == node.another;
        }
    }

    @Test
    public void testCachedLibraryDataRace() throws Exception {
        testCachedDataRace(CachedDataRaceTestFactory.TestCachedLibNodeGen::create);
    }

    @Test
    public void testCachedNodeDataRace() throws Exception {
        testCachedDataRace(CachedDataRaceTestFactory.TestCachedNodeNodeGen::create);
    }

    public static void testCachedDataRace(Supplier<TestNodeBase> nodeFactory) throws Exception {
        int threadsCount = Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors());
        ExecutorService executorService = Executors.newFixedThreadPool(threadsCount);
        AtomicBoolean done = new AtomicBoolean();
        try {
            testCachedDataRace(nodeFactory, executorService, threadsCount, done);
        } finally {
            done.set(true);
            executorService.shutdown();
            executorService.awaitTermination(10000, TimeUnit.MILLISECONDS);
        }
    }

    private static void testCachedDataRace(Supplier<TestNodeBase> nodeFactory, ExecutorService executorService, int threadsCount, AtomicBoolean done) throws Exception {
        Object[] items = new Object[]{1, 1.2, 1L, "string", new Object(), true, (byte) 1, (short) 2, (float) 1.2, 'a'};
        Future<?>[] futures = new Future<?>[threadsCount];

        for (int i = 0; i < TEST_REPETITIONS; i++) {
            TestNodeBase node = nodeFactory.get();
            done.set(false);

            // "reader" threads keep on reading the CachedLibrary until done
            for (int idx = 0; idx < futures.length - 1; idx++) {
                futures[idx] = executorService.submit(() -> {
                    while (!done.get()) {
                        node.execute(42);
                    }
                });
            }

            // "writer" thread creates new CachedLibrary instances, then sets done
            futures[futures.length - 1] = executorService.submit(() -> {
                for (Object item : items) {
                    node.execute(item);
                }
                done.set(true);
            });

            for (Future<?> future : futures) {
                future.get();
            }
        }
    }
}
