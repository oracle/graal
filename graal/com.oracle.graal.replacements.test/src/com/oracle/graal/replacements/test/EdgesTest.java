/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Edges.Type;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;

public class EdgesTest extends GraalCompilerTest {

    @NodeInfo
    static class TestNode extends Node {
        @Input NodeInputList<ValueNode> itail;
        @Input ConstantNode i1;
        @Input FloatingNode i2;

        public static TestNode create() {
            return USE_GENERATED_NODES ? new EdgesTest_TestNodeGen() : new TestNode();
        }
    }

    StructuredGraph graph = new StructuredGraph();
    TestNode node;
    ConstantNode i1;
    ConstantNode i2;
    ConstantNode i3;
    ConstantNode i4;
    Edges inputs;

    public EdgesTest() {
        node = TestNode.create();
        i1 = ConstantNode.forInt(1, graph);
        i2 = ConstantNode.forDouble(1.0d, graph);
        i3 = ConstantNode.forInt(4, graph);
        i4 = ConstantNode.forInt(14, graph);
        node.itail = new NodeInputList<>(node, new ValueNode[]{i3, i4});
        node.i1 = i1;
        node.i2 = i2;
        graph.add(node);
        inputs = node.getNodeClass().getEdges(Type.Inputs);
    }

    /**
     * Checks that there are no checkcasts in the compiled version of
     * {@link Edges#getNode(Node, int)}
     */
    @Test
    public void test0() {
        testMethod(getMethod("getNode", Node.class, int.class), inputs, node, 0);
    }

    /**
     * Checks that there are no checkcasts in the compiled version of
     * {@link Edges#getNodeList(Node, int)}
     */
    @Test
    public void test1() {
        testMethod(getMethod("getNodeList", Node.class, int.class), inputs, node, 2);
    }

    /**
     * Checks that there are no checkcasts in the compiled version of
     * {@link Edges#setNode(Node, int, Node)}
     */
    @Test
    public void test2() {
        testMethod(getMethod("setNode", Node.class, int.class, Node.class), inputs, node, 1, i2);
    }

    private void testMethod(Method method, Object receiver, Object... args) {
        try {
            // Invoke the method to ensure it has a type profile
            for (int i = 0; i < 5000; i++) {
                method.invoke(receiver, args);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        StructuredGraph g = parseProfiled(method);
        Assumptions assumptions = new Assumptions(false);
        HighTierContext context = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        new InliningPhase(new CanonicalizerPhase(true)).apply(g, context);
        new CanonicalizerPhase(false).apply(g, context);
        Assert.assertTrue(g.getNodes().filter(CheckCastNode.class).isEmpty());
    }

    private static Method getMethod(final String name, Class<?>... parameters) {
        try {
            return Edges.class.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
