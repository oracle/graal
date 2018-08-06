/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
