/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

/**
 * Exercises concurrent publication of specialization state and shared cached child nodes. Fresh
 * nodes are processed in batches to repeatedly race first specialization on hot worker threads.
 */
public class GR76999Test {

    private static final int DEFAULT_TEST_REPETITIONS = 1_000_000;
    private static final int NODES_PER_BATCH = 100;
    private static final int MIN_THREADS = 16;
    private static final int MAX_THREADS = 32;

    @Test
    public void testConcurrentSpecialization() throws Exception {
        int threadCount = Math.max(MIN_THREADS, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors()));
        int testRepetitions = Integer.getInteger("GR76999Test.repetitions", DEFAULT_TEST_REPETITIONS);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        TruffleString value = TruffleString.fromJavaStringUncached("test", Encoding.UTF_8);
        try {
            for (int repetition = 0; repetition < testRepetitions; repetition += NODES_PER_BATCH) {
                int batchSize = Math.min(NODES_PER_BATCH, testRepetitions - repetition);
                TestNode[] nodes = new TestNode[batchSize];
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i] = GR76999TestFactory.TestNodeGen.create();
                }
                Semaphore ready = new Semaphore(0);
                Semaphore start = new Semaphore(0);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < threadCount; i++) {
                    futures.add(executor.submit(() -> {
                        ready.release();
                        start.acquire();
                        for (TestNode node : nodes) {
                            if (!node.execute(value)) {
                                return false;
                            }
                        }
                        return true;
                    }));
                }
                ready.acquire(threadCount);
                start.release(threadCount);
                for (Future<Boolean> future : futures) {
                    assertTrue(future.get());
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @GenerateInline(false)
    abstract static class TestNode extends Node {

        abstract boolean execute(Object value);

        @Specialization(guards = "!guard.execute(inliningTarget, value)", limit = "1")
        static boolean doString(Object value,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GuardNode guard,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.RegionEqualNode regionEqualNode) {
            TruffleString string = (TruffleString) value;
            return codePointLengthNode.execute(string, Encoding.UTF_8) == 4 && regionEqualNode.execute(string, 0, string, 0, 4, Encoding.UTF_8);
        }

        @Specialization(guards = "guard.execute(inliningTarget, value)", limit = "1")
        static boolean doInteger(@SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GuardNode guard,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.RegionEqualNode regionEqualNode) {
            return codePointLengthNode != null && regionEqualNode != null;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class GuardNode extends Node {

        abstract boolean execute(Node inliningTarget, Object value);

        @Specialization
        static boolean doInteger(@SuppressWarnings("unused") Integer value) {
            return true;
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object value) {
            return false;
        }
    }

}
