/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test.ea;

import static org.junit.Assert.*;
import java.util.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.virtual.phases.ea.*;

public class PEAReadEliminationTest extends GraalCompilerTest {

    protected StructuredGraph graph;

    public static Object staticField;

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
        ValueNode result = getReturn("testSimpleSnippet").result();
        assertTrue(graph.getNodes().filter(LoadFieldNode.class).isEmpty());
        assertTrue(result.isConstant());
        assertDeepEquals(2, result.asConstant().asInt());
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
        ValueNode result = getReturn("testSimpleConflictSnippet").result();
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
        ValueNode result = getReturn("testParamSnippet").result();
        assertTrue(graph.getNodes().filter(LoadFieldNode.class).isEmpty());
        assertDeepEquals(graph.getParameter(1), result);
    }

    @SuppressWarnings("all")
    public static int testMaterializedSnippet(int a) {
        TestObject obj = new TestObject(a, 0);
        staticField = obj;
        return obj.x;
    }

    @Test
    public void testMaterialized() {
        ValueNode result = getReturn("testMaterializedSnippet").result();
        assertTrue(graph.getNodes().filter(LoadFieldNode.class).isEmpty());
        assertDeepEquals(graph.getParameter(0), result);
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
        ValueNode result = getReturn("testSimpleLoopSnippet").result();
        assertTrue(graph.getNodes().filter(LoadFieldNode.class).isEmpty());
        assertDeepEquals(graph.getParameter(1), result);
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
        ValueNode result = getReturn("testBadLoopSnippet").result();
        assertDeepEquals(0, graph.getNodes().filter(LoadFieldNode.class).count());
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
        ValueNode result = getReturn("testBadLoop2Snippet").result();
        assertDeepEquals(1, graph.getNodes().filter(LoadFieldNode.class).count());
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
        processMethod("testPhiSnippet");
        assertTrue(graph.getNodes().filter(LoadFieldNode.class).isEmpty());
        List<ReturnNode> returnNodes = graph.getNodes(ReturnNode.class).snapshot();
        assertDeepEquals(2, returnNodes.size());
        assertTrue(returnNodes.get(0).predecessor() instanceof StoreFieldNode);
        assertTrue(returnNodes.get(1).predecessor() instanceof StoreFieldNode);
        assertTrue(returnNodes.get(0).result().isConstant());
        assertTrue(returnNodes.get(1).result().isConstant());
    }

    @SuppressWarnings("all")
    public static void testSimpleStoreSnippet(TestObject a, int b) {
        a.x = b;
        a.x = b;
    }

    @Test
    public void testSimpleStore() {
        processMethod("testSimpleStoreSnippet");
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
        processMethod("testValueProxySnippet");
        assertDeepEquals(2, graph.getNodes().filter(LoadFieldNode.class).count());
    }

    final ReturnNode getReturn(String snippet) {
        processMethod(snippet);
        assertDeepEquals(1, graph.getNodes(ReturnNode.class).count());
        return graph.getNodes(ReturnNode.class).first();
    }

    protected void processMethod(final String snippet) {
        graph = parseEager(snippet);
        Assumptions assumptions = new Assumptions(false);
        HighTierContext context = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        new InliningPhase(new CanonicalizerPhase(true)).apply(graph, context);
        new PartialEscapePhase(false, true, new CanonicalizerPhase(true), null).apply(graph, context);
    }
}
