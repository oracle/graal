/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.TruffleReturnBoxedParameterTestFactory.IntNodeFactory;
import org.junit.Test;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * A simple test to verify that a truffle compilation unit returning a parameter re-uses the boxed
 * parameter if possible instead of re-boxing it.
 */
public class TruffleReturnBoxedParameterTest extends PartialEvaluationTest {

    @SuppressWarnings("unused")
    @GenerateNodeFactory
    public abstract static class VNode extends Node {

        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            throw new UnsupportedOperationException();
        }

    }

    @NodeChild
    public abstract static class IntNode extends VNode {

        @Specialization
        int f1(int a) {
            return a;
        }
    }

    public static class TestRootNode<E extends VNode> extends RootNode {

        @Child private E node;

        public TestRootNode(E node) {
            super(null);
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(frame);
        }

        public E getNode() {
            return node;
        }
    }

    static <E extends VNode> TestRootNode<E> createRoot(NodeFactory<E> factory, Object... constants) {
        TestRootNode<E> rootNode = new TestRootNode<>(createNode(factory, false, constants));
        rootNode.adoptChildren();
        return rootNode;
    }

    public static class ArgumentNode extends VNode {

        final int index;

        public ArgumentNode(int index) {
            this.index = index;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[index];
        }

        public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            Object o = frame.getArguments()[index];
            if (o instanceof Integer) {
                return (int) o;
            }
            throw new UnexpectedResultException(o);
        }

    }

    private static ArgumentNode[] arguments(int count) {
        ArgumentNode[] nodes = new ArgumentNode[count];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new ArgumentNode(i);
        }
        return nodes;
    }

    static <E extends VNode> E createNode(NodeFactory<E> factory, boolean prefixConstants, Object... constants) {
        ArgumentNode[] argumentNodes = arguments(factory.getExecutionSignature().size());

        List<Object> argumentList = new ArrayList<>();
        if (prefixConstants) {
            argumentList.addAll(Arrays.asList(constants));
        }
        argumentList.addAll(Arrays.asList(argumentNodes));
        if (!prefixConstants) {
            argumentList.addAll(Arrays.asList(constants));
        }
        return factory.createNode(argumentList.toArray(new Object[argumentList.size()]));
    }

    @Test
    public void testBox() throws Throwable {
        TestRootNode<IntNode> node = createRoot(IntNodeFactory.getInstance());
        OptimizedCallTarget callTarget = (OptimizedCallTarget) node.getCallTarget();
        StructuredGraph g = partialEval(callTarget, new Object[]{1});
        compile(callTarget, g);
        // no box foreign call to allocation snippet after box optimization
        for (ForeignCallNode call : g.getNodes().filter(ForeignCallNode.class)) {
            // plain methods contain a safepoint
            if (!call.getDescriptor().getName().equals("HotSpotThreadLocalHandshake.doHandshake")) {
                throw new AssertionError("Unexpected foreign call:" + call.getDescriptor().getName());
            }
        }
    }
}
