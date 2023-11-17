/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Edges;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.common.inlining.policy.InlineMethodSubstitutionsPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@SuppressWarnings("this-escape")
public class EdgesTest extends GraalCompilerTest {

    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    static final class TestNode extends Node {
        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);
        @Input NodeInputList<ValueNode> itail;
        @Input ConstantNode i1;
        @Input FloatingNode i2;

        protected TestNode() {
            super(TYPE);
        }

    }

    protected StructuredGraph createGraph() {
        OptionValues options = getInitialOptions();
        return new StructuredGraph.Builder(options, getDebugContext(options)).build();
    }

    StructuredGraph graph = createGraph();
    TestNode node;
    ConstantNode i1;
    ConstantNode i2;
    ConstantNode i3;
    ConstantNode i4;
    Edges inputs;

    public EdgesTest() {
        node = new TestNode();
        i1 = ConstantNode.forInt(1, graph);
        i2 = ConstantNode.forDouble(1.0d, graph);
        i3 = ConstantNode.forInt(4, graph);
        i4 = ConstantNode.forInt(14, graph);
        node.itail = new NodeInputList<>(node, new ValueNode[]{i3, i4});
        node.i1 = i1;
        node.i2 = i2;
        graph.add(node);
        inputs = node.getNodeClass().getInputEdges();
    }

    /**
     * Checks that there are no checkcasts in the compiled version of
     * {@link Edges#getNode(Node, long[], int)}.
     */
    @Test
    public void test0() {
        testMethod(getMethod("getNode", Node.class, long[].class, int.class), null, node, inputs.getOffsets(), 0);
    }

    /**
     * Checks that there are no checkcasts in the compiled version of
     * {@link Edges#getNodeList(Node, long[], int)}.
     */
    @Test
    public void test1() {
        testMethod(getMethod("getNodeList", Node.class, long[].class, int.class), null, node, inputs.getOffsets(), 2);
    }

    /**
     * Checks that there are no checkcasts in the compiled version of
     * {@link Edges#setNode(Node, int, Node)}.
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

        ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(method);
        StructuredGraph g = parseProfiled(javaMethod, AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        new InliningPhase(new InlineMethodSubstitutionsPolicy(), createCanonicalizerPhase()).apply(g, context);
        this.createCanonicalizerPhase().apply(g, context);
        Assert.assertTrue(g.getNodes().filter(InstanceOfNode.class).isEmpty());
    }

    private static Method getMethod(final String name, Class<?>... parameters) {
        try {
            return Edges.class.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
