/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.dsl.test.TestHelper.createRoot;
import static com.oracle.truffle.api.dsl.test.TestHelper.executeWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.MergeSpecializationsTestFactory.TestCachedNodeFactory;
import com.oracle.truffle.api.dsl.test.MergeSpecializationsTestFactory.TestNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeBoxingTest.TypeBoxingTypeSystem;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.ReflectionUtils;

public class MergeSpecializationsTest {

    private static final int THREADS = 25;
    private static final int ITERATIONS = 20;

    @NodeChild
    @SuppressWarnings("unused")
    @TypeSystemReference(TypeBoxingTypeSystem.class)
    abstract static class TestNode extends ValueNode {

        @Specialization
        int s1(int a) {
            return 1;
        }

        @Specialization
        int s2(long a) {
            return 2;
        }

        @Specialization
        int s3(double a) {
            return 3;
        }
    }

    @NodeChild
    @SuppressWarnings("unused")
    @TypeSystemReference(TypeBoxingTypeSystem.class)
    abstract static class TestCachedNode extends ValueNode {

        @Specialization(guards = "a == cachedA", limit = "3")
        int s1(int a, @Cached("a") int cachedA) {
            return 1;
        }

        @Specialization
        int s2(long a) {
            return 2;
        }

        @Specialization
        int s3(double a) {
            return 3;
        }
    }

    @Test
    public void testMultithreadedMergeInOrder() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            multithreadedMerge(TestNodeFactory.getInstance(), new Executions(1, 1L << 32, 1.0), 1, 2, 3);
        }
    }

    @Test
    public void testMultithreadedMergeReverse() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            multithreadedMerge(TestNodeFactory.getInstance(), new Executions(1.0, 1L << 32, 1), 3, 2, 1);
        }
    }

    @Test
    public void testMultithreadedMergeCachedInOrder() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            multithreadedMerge(TestCachedNodeFactory.getInstance(), new Executions(1, 1L << 32, 1.0), 1, 2, 3);
        }
    }

    @Test
    public void testMultithreadedMergeCachedTwoEntries() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            multithreadedMerge(TestCachedNodeFactory.getInstance(), new Executions(1, 2, 1.0), 1, 1, 3);
        }
    }

    @Test
    public void testMultithreadedMergeCachedThreeEntries() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            multithreadedMerge(TestCachedNodeFactory.getInstance(), new Executions(1, 2, 3), 1, 1, 1);
        }
    }

    private static <T extends ValueNode> void multithreadedMerge(NodeFactory<T> factory, final Executions executions, int... order) throws Exception {
        assertEquals(3, order.length);
        final TestRootNode<T> node = createRoot(factory);

        final CountDownLatch threadsStarted = new CountDownLatch(THREADS);

        final CountDownLatch beforeFirst = new CountDownLatch(1);
        final CountDownLatch executedFirst = new CountDownLatch(THREADS);

        final CountDownLatch beforeSecond = new CountDownLatch(1);
        final CountDownLatch executedSecond = new CountDownLatch(THREADS);

        final CountDownLatch beforeThird = new CountDownLatch(1);
        final CountDownLatch executedThird = new CountDownLatch(THREADS);

        Thread[] threads = new Thread[THREADS];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    threadsStarted.countDown();

                    MergeSpecializationsTest.await(beforeFirst);
                    executeWith(node, executions.firstValue);
                    executedFirst.countDown();

                    MergeSpecializationsTest.await(beforeSecond);
                    executeWith(node, executions.secondValue);
                    executedSecond.countDown();

                    MergeSpecializationsTest.await(beforeThird);
                    executeWith(node, executions.thirdValue);
                    executedThird.countDown();
                }
            });
            threads[i].start();
        }

        T checkedNode = node.getNode();

        assertState(checkedNode, order, 0);

        await(threadsStarted);
        beforeFirst.countDown();

        await(executedFirst);
        assertState(checkedNode, order, 1);

        beforeSecond.countDown();
        await(executedSecond);
        assertState(checkedNode, order, 2);

        beforeThird.countDown();
        await(executedThird);
        assertState(checkedNode, order, 3);

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                fail("interrupted");
            }
        }
    }

    private static void assertState(Node node, int[] expectedOrder, int checkedIndices) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        Field stateField = node.getClass().getDeclaredField("state_");
        ReflectionUtils.setAccessible(stateField, true);
        int state = ((((Number) stateField.get(node))).intValue());
        Arrays.sort(expectedOrder, 0, checkedIndices);
        int mask = 0;
        for (int i = 0; i < checkedIndices; i++) {
            mask |= 0b1 << expectedOrder[i] - 1;
        }
        assertEquals(mask, state & 0b111);
    }

    private static class Executions {
        public final Object firstValue;
        public final Object secondValue;
        public final Object thirdValue;

        Executions(Object firstValue, Object secondValue, Object thirdValue) {
            this.firstValue = firstValue;
            this.secondValue = secondValue;
            this.thirdValue = thirdValue;
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            fail("interrupted");
        }
    }

}
