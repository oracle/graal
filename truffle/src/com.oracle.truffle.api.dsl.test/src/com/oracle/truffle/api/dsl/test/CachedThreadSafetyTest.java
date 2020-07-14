/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.CachedThreadSafetyTestFactory.SingleCachedFieldNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class CachedThreadSafetyTest {

    private static final int TASKS = 128;
    private static final int PARALLELISM = 16;
    private static final int NODES = 5;
    private static final int REPEATS = 5;

    @Test
    public void testSingleField() throws InterruptedException {
        Random random = new Random();
        Object[][] specializations = new Object[][]{
                        new Object[]{""},
                        new Object[]{(byte) 42},
                        new Object[]{42},
                        new Object[]{42L}
        };
        for (int iteration = 0; iteration < REPEATS; iteration++) {
            for (int specialization = 0; specialization < specializations.length; specialization++) {
                SingleCachedFieldNode[] nodes = createNodes();
                int finalSpecialization = specialization;
                runInParallel(() -> {
                    testSpecializationInInterpreter(specializations, finalSpecialization, nodes);
                });
            }

            for (int i = 0; i < 10; i++) {
                SingleCachedFieldNode[] nodes = createNodes();
                runInParallel(() -> {
                    int specialization = random.nextInt(specializations.length);
                    testSpecializationInInterpreter(specializations, specialization, nodes);
                });
            }

            for (int specialization = 0; specialization < specializations.length; specialization++) {
                SingleCachedFieldNode[] nodes = createNodes();
                int finalSpecialization = specialization;
                runInParallel(() -> {
                    testSpecializationInCallTarget(specializations, finalSpecialization, nodes);
                });
            }

            for (int i = 0; i < 10; i++) {
                SingleCachedFieldNode[] nodes = createNodes();
                runInParallel(() -> {
                    int specialization = random.nextInt(specializations.length);
                    testSpecializationInCallTarget(specializations, specialization, nodes);
                });
            }
        }
    }

    private static SingleCachedFieldNode[] createNodes() {
        SingleCachedFieldNode[] nodes = new SingleCachedFieldNode[NODES];
        for (int nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
            SingleCachedFieldNode node = SingleCachedFieldNodeGen.create();
            TestRootNode root = new TestRootNode(null, node);
            Truffle.getRuntime().createCallTarget(root);
            nodes[nodeIndex] = node;
            assertNotNull(node.getParent());
        }
        return nodes;
    }

    private static void testSpecializationInInterpreter(Object[][] specializations, int specializationIndex, SingleCachedFieldNode[] nodes) {
        for (int nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
            Object param = specializations[specializationIndex][0];
            SingleCachedFieldNode node = nodes[nodeIndex];
            node.execute(param);
        }
    }

    private static void testSpecializationInCallTarget(Object[][] specializations, int specializationIndex, SingleCachedFieldNode[] nodes) {
        for (int nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
            nodes[nodeIndex].getRootNode().getCallTarget().call(specializations[specializationIndex]);
        }
    }

    private static class TestRootNode extends RootNode {

        @Child private SingleCachedFieldNode node;

        protected TestRootNode(TruffleLanguage<?> language, SingleCachedFieldNode node) {
            super(language);
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(frame.getArguments()[0]);
        }

    }

    static final class Signature {

    }

    abstract static class SingleCachedFieldNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object doString(@SuppressWarnings("unused") String arg,
                        @Cached("cachedInit()") Object cached0) {
            if (cached0 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
            return cached0;
        }

        @Specialization
        Object doByte(@SuppressWarnings("unused") Byte arg,
                        @Cached("cachedInit()") Object cached0,
                        @Cached("cachedInit()") Object cached1) {
            if (cached0 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
            if (cached1 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }

            return cached0;
        }

        @Specialization
        Object doInt(@SuppressWarnings("unused") Integer arg,
                        @Cached("cachedInit()") Object cached0,
                        @Cached("cachedInit()") Object cached1,
                        @Cached("cachedInit()") Object cached2) {
            if (cached0 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
            if (cached1 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
            if (cached2 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
            return cached0;
        }

        @Specialization
        Object doLong(@SuppressWarnings("unused") Long arg,
                        @Cached("cachedInit()") Object cached0,
                        @Cached("cachedInit()") Object cached1,
                        @Cached("cachedInit()") Object cached2,
                        @Cached("cachedInit()") Object cached3) {
            if (cached0 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
            if (cached1 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
            if (cached2 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
            if (cached3 == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
            return cached0;
        }

        static Object cachedInit() {
            return new Object();
        }

    }

    private static void runInParallel(Runnable callable) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < Math.max(PARALLELISM, TASKS); i++) {
            futures.add(executor.submit(() -> {
                callable.run();
                return null;
            }));
        }
        try {
            for (int i = 0; i < futures.size(); i++) {
                Future<?> future = futures.get(i);
                try {
                    future.get();
                } catch (ExecutionException e) {
                    throw new AssertionError("Future task failed with index " + i, e.getCause());
                }
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(10000, TimeUnit.MILLISECONDS);
        }
    }

}
