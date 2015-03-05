/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.*;
import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.api.dsl.test.MergeSpecializationsTestFactory.TestCachedNodeFactory;
import com.oracle.truffle.api.dsl.test.MergeSpecializationsTestFactory.TestNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.*;

public class MergeSpecializationsTest {

    private static final int THREADS = 8;

    @NodeChild
    @SuppressWarnings("unused")
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
    public void testMultithreadedMergeInOrder() {
        multithreadedMerge(TestNodeFactory.getInstance(), new Executions(1, 1L << 32, 1.0), 1, 2, 3);
    }

    @Test
    public void testMultithreadedMergeReverse() {
        multithreadedMerge(TestNodeFactory.getInstance(), new Executions(1.0, 1L << 32, 1), 3, 2, 1);
    }

    @Ignore
    @Test
    public void testMultithreadedMergeCachedInOrder() {
        multithreadedMerge(TestCachedNodeFactory.getInstance(), new Executions(1, 1L << 32, 1.0), 1, 2, 3);
    }

    @Ignore
    @Test
    public void testMultithreadedMergeCachedTwoEntries() {
        multithreadedMerge(TestCachedNodeFactory.getInstance(), new Executions(1, 2, 1.0), 1, 1, 3);
    }

    @Ignore
    @Test
    public void testMultithreadedMergeCachedThreeEntries() {
        multithreadedMerge(TestCachedNodeFactory.getInstance(), new Executions(1, 2, 3), 1, 1, 1);
    }

    private static <T extends ValueNode> void multithreadedMerge(NodeFactory<T> factory, final Executions executions, int... order) {
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

        final SpecializedNode gen = (SpecializedNode) node.getNode();

        final SpecializationNode start0 = gen.getSpecializationNode();
        assertEquals("UninitializedNode_", start0.getClass().getSimpleName());

        await(threadsStarted);
        beforeFirst.countDown();
        await(executedFirst);

        final SpecializationNode start1 = gen.getSpecializationNode();
        assertEquals("S" + order[0] + "Node_", start1.getClass().getSimpleName());
        assertEquals("UninitializedNode_", nthChild(1, start1).getClass().getSimpleName());

        beforeSecond.countDown();
        await(executedSecond);

        final SpecializationNode start2 = gen.getSpecializationNode();
        Arrays.sort(order, 0, 2);
        assertEquals("PolymorphicNode_", start2.getClass().getSimpleName());
        assertEquals("S" + order[0] + "Node_", nthChild(1, start2).getClass().getSimpleName());
        assertEquals("S" + order[1] + "Node_", nthChild(2, start2).getClass().getSimpleName());
        assertEquals("UninitializedNode_", nthChild(3, start2).getClass().getSimpleName());

        beforeThird.countDown();
        await(executedThird);

        final SpecializationNode start3 = gen.getSpecializationNode();
        Arrays.sort(order);
        assertEquals("PolymorphicNode_", start3.getClass().getSimpleName());
        assertEquals("S" + order[0] + "Node_", nthChild(1, start3).getClass().getSimpleName());
        assertEquals("S" + order[1] + "Node_", nthChild(2, start3).getClass().getSimpleName());
        assertEquals("S" + order[2] + "Node_", nthChild(3, start3).getClass().getSimpleName());
        assertEquals("UninitializedNode_", nthChild(4, start3).getClass().getSimpleName());

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                fail("interrupted");
            }
        }
    }

    private static class Executions {
        public final Object firstValue;
        public final Object secondValue;
        public final Object thirdValue;

        public Executions(Object firstValue, Object secondValue, Object thirdValue) {
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

    private static Node firstChild(Node node) {
        return node.getChildren().iterator().next();
    }

    private static Node nthChild(int n, Node node) {
        if (n == 0) {
            return node;
        } else {
            return nthChild(n - 1, firstChild(node));
        }
    }
}
