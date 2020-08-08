/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ArgumentNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ChildrenNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Utility class to provide some test helper functions.
 */
class TestHelper {

    // make nodes replacable
    public static <T extends Node> T createRoot(final T node) {
        new RootNode(null) {
            @Child T child = node;

            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        }.adoptChildren();
        return node;
    }

    private static ArgumentNode[] arguments(int count) {
        ArgumentNode[] nodes = new ArgumentNode[count];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new ArgumentNode(i);
        }
        return nodes;
    }

    static <E extends ValueNode> E createNode(NodeFactory<E> factory, boolean prefixConstants, Object... constants) {
        ArgumentNode[] argumentNodes = arguments(factory.getExecutionSignature().size());

        List<Object> argumentList = new ArrayList<>();
        if (prefixConstants) {
            argumentList.addAll(Arrays.asList(constants));
        }
        if (ChildrenNode.class.isAssignableFrom(factory.getNodeClass())) {
            argumentList.add(argumentNodes);
        } else {
            argumentList.addAll(Arrays.asList(argumentNodes));
        }
        if (!prefixConstants) {
            argumentList.addAll(Arrays.asList(constants));
        }
        return factory.createNode(argumentList.toArray(new Object[argumentList.size()]));
    }

    static <E extends ValueNode> TestRootNode<E> createRoot(NodeFactory<E> factory, Object... constants) {
        TestRootNode<E> rootNode = new TestRootNode<>(createNode(factory, false, constants));
        rootNode.adoptChildren();
        return rootNode;
    }

    static <E extends ValueNode> TestRootNode<E> createRootPrefix(NodeFactory<E> factory, boolean prefixConstants, Object... constants) {
        TestRootNode<E> rootNode = new TestRootNode<>(createNode(factory, prefixConstants, constants));
        rootNode.adoptChildren();
        return rootNode;
    }

    static CallTarget createCallTarget(ValueNode node) {
        return createCallTarget(new TestRootNode<>(node));
    }

    static CallTarget createCallTarget(TestRootNode<? extends ValueNode> node) {
        return Truffle.getRuntime().createCallTarget(node);
    }

    static RootCallTarget createCallTarget(NodeFactory<? extends ValueNode> factory, Object... constants) {
        return Truffle.getRuntime().createCallTarget(createRoot(factory, constants));
    }

    static boolean assertionsEnabled() {
        boolean assertOn = false;
        // *assigns* true if assertions are on.
        assert (assertOn = true) == true;
        return assertOn;
    }

    @SuppressWarnings("unchecked")
    static <T extends ValueNode> T getNode(CallTarget target) {
        return ((TestRootNode<T>) ((RootCallTarget) target).getRootNode()).getNode();
    }

    static <E> Object executeWith(TestRootNode<? extends ValueNode> node, Object... values) {
        return node.execute(Truffle.getRuntime().createVirtualFrame(values, node.getFrameDescriptor()));
    }

    static Object[] array(Object... val) {
        return val;
    }

    static <E> List<List<E>> permutations(List<E> list) {
        return permutations(new ArrayList<E>(), list, new ArrayList<List<E>>());
    }

    static Object[][] permutations(Object... list) {
        List<List<Object>> permutations = permutations(Arrays.asList(list));

        Object[][] a = new Object[permutations.size()][];
        int index = 0;
        for (List<Object> p : permutations) {
            a[index] = p.toArray(new Object[p.size()]);
            index++;
        }

        return a;
    }

    static <E> List<List<E>> permutations(List<E> prefix, List<E> suffix, List<List<E>> output) {
        if (suffix.size() == 1) {
            ArrayList<E> newElement = new ArrayList<>(prefix);
            newElement.addAll(suffix);
            output.add(newElement);
            return output;
        }

        for (int i = 0; i < suffix.size(); i++) {
            List<E> newPrefix = new ArrayList<>(prefix);
            newPrefix.add(suffix.get(i));
            List<E> newSuffix = new ArrayList<>(suffix);
            newSuffix.remove(i);
            permutations(newPrefix, newSuffix, output);
        }

        return output;
    }

    static void assertRuns(NodeFactory<? extends ValueNode> factory, Object[] testValues, Object[] result) {
        assertRuns(factory, testValues, result, null);
    }

    /* Methods tests all test values in combinational order. */
    static void assertRuns(NodeFactory<? extends ValueNode> factory, Object[] testValues, Object[] result, TestExecutionListener listener) {
        // test each run by its own.
        for (int i = 0; i < testValues.length; i++) {
            assertValue(createRoot(factory), 0, testValues[i], result[i], listener, true);
        }

        // test all combinations of the test values
        List<Object> testValuesList = Arrays.asList(testValues);
        List<List<Object>> permuts = permutations(testValuesList);
        for (List<Object> list : permuts) {
            TestRootNode<?> root = createRoot(factory);
            int index = 0;
            for (Object object : list) {
                assertValue(root, index, object, result[testValuesList.indexOf(object)], listener, index == list.size() - 1);
                index++;
            }
        }
    }

    static void assertValue(TestRootNode<? extends ValueNode> root, int index, Object value, Object result, TestExecutionListener listener, boolean last) {
        Object actualResult = null;
        if (result instanceof Class && Throwable.class.isAssignableFrom((Class<?>) result)) {
            try {
                if (value instanceof Object[]) {
                    actualResult = executeWith(root, (Object[]) value);
                } else {
                    actualResult = executeWith(root, value);
                }
                fail(String.format("Exception %s  expected but not occurred.", result.getClass()));
            } catch (Throwable e) {
                actualResult = e;
                if (!e.getClass().isAssignableFrom(((Class<?>) result))) {
                    e.printStackTrace();
                    fail(String.format("Incompatible exception class thrown. Expected %s but was %s.", result.toString(), e.getClass()));
                }
            }
        } else if (value instanceof Object[]) {
            actualResult = executeWith(root, (Object[]) value);
            assertEquals(result, actualResult);
        } else {
            actualResult = executeWith(root, value);
            assertEquals(result, actualResult);
        }
        if (listener != null) {
            listener.afterExecution(root, index, value, result, actualResult, last);
        }
    }

    static int getSlowPathCount(Node node) {
        if (!(node.getRootNode() instanceof SlowPathCounterRoot)) {
            throw new IllegalArgumentException("Not instrumented. Instrument with instrumentSlowPath");
        }
        return ((SlowPathCounterRoot) node.getRootNode()).getSlowPathCount();
    }

    static void instrumentSlowPath(Node node) {
        if (node.getParent() != null) {
            throw new IllegalArgumentException("Node already adopted.");
        }
        SlowPathCounterRoot rootNode = new SlowPathCounterRoot(node);
        rootNode.adoptChildren();
    }

    public static final class LogListener implements TestExecutionListener {

        public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
            System.out.printf("Run %3d Node:%-20s Parameters: %10s Expected: %10s Result %10s%n", index, node.getNode().getClass().getSimpleName(), value, expectedResult, actualResult);
        }

    }

    interface TestExecutionListener {

        void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last);

    }

    static class SlowPathCounterRoot extends RootNode {

        @Child Node node;

        private final AtomicInteger lockedCount = new AtomicInteger(0);
        private final AtomicInteger slowPathCount = new AtomicInteger(0);

        @SuppressWarnings("serial")
        SlowPathCounterRoot(Node node) {
            super(null);
            this.node = node;
            try {
                Field lock = RootNode.class.getDeclaredField("lock");
                lock.setAccessible(true);
                lock.set(this, new ReentrantLock() {

                    @Override
                    public void lock() {
                        slowPathCount.incrementAndGet();
                        lockedCount.incrementAndGet();
                        super.lock();
                    }

                    @Override
                    public void unlock() {
                        lockedCount.decrementAndGet();
                        super.unlock();
                    }

                });
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

        boolean isInSlowPath() {
            return lockedCount.get() > 0;
        }

        int getSlowPathCount() {
            return slowPathCount.get();
        }

    }

}
