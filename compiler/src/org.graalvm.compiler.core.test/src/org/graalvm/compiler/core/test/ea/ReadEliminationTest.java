/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.ea;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationPhase;
import org.junit.Assert;
import org.junit.Test;

public class ReadEliminationTest extends GraalCompilerTest {

    public static Object staticField;

    static void cfgSnippet() {
        if (staticField != null) {
            staticField = 12;
            if (staticField != null) {
                staticField = 12;
            }
            if (staticField != null) {
                staticField = 12;
            }
            if (staticField != null) {
                staticField = 12;
            }
            if (staticField != null) {
                staticField = 12;
            }
        } else {
            if (staticField != null) {
                staticField = 12;
            } else {
                if (staticField != null) {
                    staticField = 12;
                }
            }
        }
    }

    @Test
    public void testDeadBranches() {
        StructuredGraph graph = parseEager(getResolvedJavaMethod("cfgSnippet"), AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        int index = 0;
        boolean[] conditions = new boolean[]{true, false, false, true, true, true, false};
        /*
         * Create a graph with "dead" branches in the beginning.
         */
        for (Node n : graph.getNodes()) {
            if (n instanceof IfNode) {
                IfNode ifNode = (IfNode) n;
                ifNode.setCondition(LogicConstantNode.forBoolean(conditions[index++], graph));
            }
        }
        new ReadEliminationPhase(createCanonicalizerPhase()).apply(graph, context);
    }

    public static class TestObject {

        public int x;
        public int y;

        public TestObject(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class TestObject2 {

        public Object x;
        public Object y;

        public TestObject2(Object x, Object y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class TestObject3 extends TestObject {

        public int z;

        public TestObject3(int x, int y, int z) {
            super(x, y);
            this.z = z;
        }
    }

    @SuppressWarnings("all")
    public static int testSimpleSnippet(TestObject a) {
        a.x = 2;
        staticField = a;
        return a.x;
    }

    @Test
    public void testSimple() {
        // Test without lowering.
        ValueNode result = getReturn("testSimpleSnippet", false).result();
        assertTrue(result.graph().getNodes().filter(LoadFieldNode.class).isEmpty());
        assertTrue(result.isConstant());
        assertDeepEquals(2, result.asJavaConstant().asInt());

        // Test with lowering.
        result = getReturn("testSimpleSnippet", true).result();
        assertTrue(result.graph().getNodes().filter(ReadNode.class).isEmpty());
        assertTrue(result.isConstant());
        assertDeepEquals(2, result.asJavaConstant().asInt());
    }

    @SuppressWarnings("all")
    public static int testSimpleConflictSnippet(TestObject a, TestObject b) {
        a.x = 2;
        b.x = 3;
        staticField = a;
        return a.x;
    }

    @Test
    public void testSimpleConflict() {
        ValueNode result = getReturn("testSimpleConflictSnippet", false).result();
        assertFalse(result.isConstant());
        assertTrue(result instanceof LoadFieldNode);
    }

    @SuppressWarnings("all")
    public static int testParamSnippet(TestObject a, int b) {
        a.x = b;
        return a.x;
    }

    @Test
    public void testParam() {
        ValueNode result = getReturn("testParamSnippet", false).result();
        assertTrue(result.graph().getNodes().filter(LoadFieldNode.class).isEmpty());
        assertDeepEquals(result.graph().getParameter(1), result);
    }

    @SuppressWarnings("all")
    public static int testMaterializedSnippet(int a) {
        TestObject obj = new TestObject(a, 0);
        staticField = obj;
        return obj.x;
    }

    @Test
    public void testMaterialized() {
        ValueNode result = getReturn("testMaterializedSnippet", false).result();
        assertTrue(result.graph().getNodes().filter(LoadFieldNode.class).isEmpty());
        assertDeepEquals(result.graph().getParameter(0), result);
    }

    @SuppressWarnings("all")
    public static int testSimpleLoopSnippet(TestObject obj, int a, int b) {
        obj.x = a;
        for (int i = 0; i < 10; i++) {
            staticField = obj;
        }
        return obj.x;
    }

    @Test
    public void testSimpleLoop() {
        // Test without lowering.
        ValueNode result = getReturn("testSimpleLoopSnippet", false).result();
        assertTrue(result.graph().getNodes().filter(LoadFieldNode.class).isEmpty());
        assertDeepEquals(result.graph().getParameter(1), result);

        // Now test with lowering.
        result = getReturn("testSimpleLoopSnippet", true).result();
        assertTrue(result.graph().getNodes().filter(ReadNode.class).isEmpty());
        assertDeepEquals(result.graph().getParameter(1), result);
    }

    @SuppressWarnings("all")
    public static int testBadLoopSnippet(TestObject obj, TestObject obj2, int a, int b) {
        obj.x = a;
        for (int i = 0; i < 10; i++) {
            staticField = obj;
            obj2.x = 10;
            obj.x = 0;
        }
        return obj.x;
    }

    @Test
    public void testBadLoop() {
        ValueNode result = getReturn("testBadLoopSnippet", false).result();
        assertDeepEquals(0, result.graph().getNodes().filter(LoadFieldNode.class).count());
        assertTrue(result instanceof ProxyNode);
        assertTrue(((ProxyNode) result).value() instanceof ValuePhiNode);
    }

    @SuppressWarnings("all")
    public static int testBadLoop2Snippet(TestObject obj, TestObject obj2, int a, int b) {
        obj.x = a;
        for (int i = 0; i < 10; i++) {
            obj.x = 0;
            obj2.x = 10;
        }
        return obj.x;
    }

    @Test
    public void testBadLoop2() {
        ValueNode result = getReturn("testBadLoop2Snippet", false).result();
        assertDeepEquals(1, result.graph().getNodes().filter(LoadFieldNode.class).count());
        assertTrue(result instanceof LoadFieldNode);
    }

    @SuppressWarnings("all")
    public static int testPhiSnippet(TestObject a, int b) {
        if (b < 0) {
            a.x = 1;
        } else {
            a.x = 2;
        }
        return a.x;
    }

    @Test
    public void testPhi() {
        StructuredGraph graph = processMethod("testPhiSnippet", false);
        assertTrue(graph.getNodes().filter(LoadFieldNode.class).isEmpty());
        Assert.assertEquals(2, graph.getNodes().filter(StoreFieldNode.class).count());
    }

    @SuppressWarnings("all")
    public static void testSimpleStoreSnippet(TestObject a, int b) {
        a.x = b;
        a.x = b;
    }

    @Test
    public void testSimpleStore() {
        StructuredGraph graph = processMethod("testSimpleStoreSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreFieldNode.class).count());
    }

    public static int testValueProxySnippet(boolean b, TestObject o) {
        int sum = 0;
        if (b) {
            sum += o.x;
        } else {
            TestObject3 p = (TestObject3) o;
            sum += p.x;
        }
        sum += o.x;
        return sum;
    }

    @Test
    public void testValueProxy() {
        StructuredGraph graph = processMethod("testValueProxySnippet", false);
        assertDeepEquals(2, graph.getNodes().filter(LoadFieldNode.class).count());
    }

    ReturnNode getReturn(String snippet, boolean doLowering) {
        StructuredGraph graph = processMethod(snippet, doLowering);
        assertDeepEquals(1, graph.getNodes(ReturnNode.TYPE).count());
        return graph.getNodes(ReturnNode.TYPE).first();
    }

    protected StructuredGraph processMethod(String snippet, boolean doLowering) {
        StructuredGraph graph = parseEager(getResolvedJavaMethod(snippet), AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        createInliningPhase().apply(graph, context);
        if (doLowering) {
            new LoweringPhase(createCanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        }
        new ReadEliminationPhase(createCanonicalizerPhase()).apply(graph, context);
        return graph;
    }
}
