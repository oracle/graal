/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfig;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.calc.ObjectEqualsNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.common.cfs.FlowSensitiveReductionPhase;

import org.junit.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

import java.util.Iterator;
import java.util.List;

/**
 * Tests whether {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReductionPhase} actually
 * performs some graph rewritings that it's supposed to perform.
 */
public class FlowSenReduTest extends GraalCompilerTest {

    /*
     * A previous instanceof makes redundant a follow-up checkcast.
     */
    public Object redundantCheckCastSnippet(Number o) {
        Integer z = null;
        if (o instanceof Integer) {
            z = (Integer) o; // this CheckCastNode will be removed
        }
        return z;
    }

    static final Integer i7 = new Integer(7);

    @Test
    public void redundantCheckCastTest() {
        assertDeepEquals(i7, redundantCheckCastSnippet(i7));
        StructuredGraph result = afterFlowSensitiveReduce("redundantCheckCastSnippet");
        nodeCountEquals(result, CheckCastNode.class, 0);
        nodeCountEquals(result, InstanceOfNode.class, 1);
    }

    @SuppressWarnings("unused")
    public boolean redundantInstanceOfSnippet01(Object o) {
        if (o != null) {
            Integer x = (Integer) o;
            return (o instanceof Number); // this InstanceOfNode will be removed
        }
        return false;
    }

    @Test
    public void redundantInstanceOfTest01() {
        String snippet = "redundantInstanceOfSnippet01";
        assertDeepEquals(true, redundantInstanceOfSnippet01(i7));
        nodeCountEquals(afterFlowSensitiveReduce(snippet), InstanceOfNode.class, 1);
    }

    /*
     * The combination of (previous) non-null-check and checkcast make redundant an instanceof.
     */
    @SuppressWarnings("unused")
    public Object redundantInstanceOfSnippet02(Object o) {
        Integer x = (Integer) o;
        if (o != null) {
            if (o instanceof Number) { // this InstanceOfNode will be removed
                return o;
            }
        }
        return null;
    }

    @Test
    public void redundantInstanceOfTest02() {
        String snippet = "redundantInstanceOfSnippet02";
        assertDeepEquals(i7, redundantInstanceOfSnippet02(i7));
        int ioAfter = getNodes(afterFlowSensitiveReduce(snippet), InstanceOfNode.class).size();
        assertDeepEquals(ioAfter, 1);
    }

    /*
     * Once an exact-type has been inferred (due to instanceof final-class) a callsite is
     * devirtualized.
     */
    public int devirtualizationSnippet(Object x, Object y) {
        boolean c = x instanceof Integer;
        if (c && x == y) {
            Number z = (Number) y; // this CheckCastNode will be removed
            return z.intValue(); // devirtualized into InvokeSpecial on Integer.intValue()
        }
        return 0;
    }

    @Test
    public void devirtualizationTest() {
        String snippet = "devirtualizationSnippet";
        assertDeepEquals(i7, devirtualizationSnippet(i7, i7));
        nodeCountEquals(afterFlowSensitiveReduce(snippet), CheckCastNode.class, 0);

        StructuredGraph graph = afterFlowSensitiveReduce(snippet);
        assertDeepEquals(0, graph.getNodes().filter(CheckCastNode.class).count());

        List<InvokeNode> invokeNodes = getNodes(afterFlowSensitiveReduce(snippet), InvokeNode.class);
        assertDeepEquals(1, invokeNodes.size());

        MethodCallTargetNode target = (MethodCallTargetNode) invokeNodes.get(0).callTarget();
        assertDeepEquals(MethodCallTargetNode.InvokeKind.Special, target.invokeKind());
        assertDeepEquals("HotSpotMethod<Integer.intValue()>", target.targetMethod().toString());
    }

    /*
     * At the return statement, the returned value has been inferred to have type j.l.Number. The
     * instanceof is deemed to always evaluate to false. The interplay with tail-duplication is also
     * taken into account (resulting in two return statements, each with "false" as input).
     */
    @SuppressWarnings("unused")
    public boolean t5Snippet(Object o, boolean b) {
        Number z;
        if (b) {
            z = (Number) o; // tail duplication of return stmt, which becomes "return false"
        } else {
            z = (Integer) o; // tail duplication of return stmt, which becomes "return false"
        }
        return o instanceof String; // unreachable
    }

    @Test
    public void t5a() {
        String snippet = "t5Snippet";
        assertDeepEquals(false, t5Snippet(null, true));
        StructuredGraph resultGraph = canonicalize(afterFlowSensitiveReduce(snippet));
        nodeCountEquals(resultGraph, ReturnNode.class, 2);

        List<ReturnNode> returnNodes = getNodes(resultGraph, ReturnNode.class);
        Iterator<ReturnNode> iter = returnNodes.iterator();

        ConstantNode c1 = (ConstantNode) iter.next().result();
        ConstantNode c2 = (ConstantNode) iter.next().result();

        assertDeepEquals(c1, c2);
        assertDeepEquals(0, c1.getValue().asInt());
    }

    @Test
    public void t5b() {
        String snippet = "t5Snippet";
        StructuredGraph graph = afterFlowSensitiveReduce(snippet);
        canonicalize(graph);
        nodeCountEquals(graph, InstanceOfNode.class, 2);
    }

    public boolean t6Snippet(Object x, Object y) {
        if (!(x instanceof String)) {
            return false;
        }
        if (!(y instanceof Number)) {
            return false;
        }
        return x == y; // two known-not-to-conform reference values can't be ==
    }

    // TODO: two known-not-to-conform reference values can't be ==
    // but baseCaseObjectEqualsNode doesn't check that as of now.
    public void t6() {
        String snippet = "t6Snippet";
        // visualize(snippet);
        StructuredGraph graph = afterFlowSensitiveReduce(snippet);
        canonicalize(graph);
        nodeCountEquals(graph, ObjectEqualsNode.class, 0);
    }

    /*
     * A previous instanceof check causes a follow-up instanceof to be deemed unsatisfiable,
     * resulting in constant-substitution at that usage.
     */
    public Object t7Snippet(Object o) {
        if (o instanceof Number) {
            if (o instanceof String) { // condition amounts to false
                return o; // made unreachable
            }
        }
        return null;
    }

    @Test
    public void t7() {
        String snippet = "t7Snippet";
        StructuredGraph graph = afterFlowSensitiveReduce(snippet);
        graph = dce(canonicalize(graph));
        // TODO how to simplify IfNode(false)
        assertDeepEquals(1, getNodes(graph, InstanceOfNode.class).size());

        List<ReturnNode> returnNodes = getNodes(graph, ReturnNode.class);
        assertDeepEquals(2, returnNodes.size());
        Iterator<ReturnNode> iter = returnNodes.iterator();

        ConstantNode c1 = (ConstantNode) iter.next().result();
        ConstantNode c2 = (ConstantNode) iter.next().result();

        assertDeepEquals(c1, c2);
        Assert.assertTrue(c1.getValue().isNull());
    }

    /*
     * During FlowSensitiveReduction, an unreachable branch doesn't contribute to the merged state.
     * The resulting ("non-polluted") more precise inferred type after the merge allows
     * devirtualizing a callsite.
     */
    public int devirtualizationSnippet02(Number o) {
        if (o instanceof Integer) {
            Number z = o;
            if (o instanceof Long) {
                z = o;
            }
            /*
             * devirtualized into InvokeSpecial on Integer.intValue() ie the inferred-type is not
             * polluted with values from the unreachable branch.
             */
            return z.intValue();
        }
        return 0;
    }

    @Test
    public void devirtualizationTest02() {
        String snippet = "devirtualizationSnippet02";
        StructuredGraph graph = afterFlowSensitiveReduce(snippet);

        assertDeepEquals(1, getNodes(graph, InvokeNode.class).size());

        List<InvokeNode> invokeNodes = getNodes(graph, InvokeNode.class);
        assertDeepEquals(1, invokeNodes.size());

        MethodCallTargetNode target = (MethodCallTargetNode) invokeNodes.get(0).callTarget();
        assertDeepEquals(MethodCallTargetNode.InvokeKind.Special, target.invokeKind());
        assertDeepEquals("HotSpotMethod<Integer.intValue()>", target.targetMethod().toString());
    }

    /*
     * TODO ClassCastException known to fail --- either Deopt or throw ObjectGetClassNode The latter
     * might lead to direct jump to EH if present.
     */
    @SuppressWarnings("unused")
    public int t9Snippet(Object o) {
        try {
            if (o instanceof Number) {
                String s = (String) o;
                /*
                 * make a long story short: replace the above with throw new ClassCastException (ok,
                 * actual type of o unknown).
                 */
                return 1;
            }
        } catch (ClassCastException e) {
            return 2;
        }
        return 3;
    }

    /*
     * "Partial evaluation" via canonicalization of an expression (in the last return statement) one
     * of whose leaf sub-expressions was determined to be constant.
     */
    @SuppressWarnings("unused")
    public Object partialEvalSnippet01(Object o, boolean b) {
        if (o == null) {
            return o; // turned into "return null;"
        } else {
            Number z;
            if (b) {
                z = (Number) o;
            } else {
                z = (Integer) o;
            }
            return o instanceof String ? this : null; // turned into "return null;"
        }
    }

    @Test
    public void partialEvalTest01() {
        String snippet = "partialEvalSnippet01";

        StructuredGraph graph = afterFlowSensitiveReduce(snippet);
        canonicalize(graph);
        dce(graph);

        List<ReturnNode> returnNodes = getNodes(graph, ReturnNode.class);
        assertDeepEquals(2, returnNodes.size());
        Iterator<ReturnNode> iter = returnNodes.iterator();

        ValueNode c1 = GraphUtil.unproxify(iter.next().result());
        ValueNode c2 = GraphUtil.unproxify(iter.next().result());
        assert !iter.hasNext();

        Assert.assertTrue(c1.isNullConstant());
        Assert.assertTrue(c2.isNullConstant());
    }

    public static class C {
        public int f;
    }

    /*
     * A previous (assumed successful) instanceof check is reused later on to remove a checkcast.
     */
    public void deduplicateInstanceOfSnippet(Object o) {
        ((C) o).f = ((C) o).f; // boils down to a single instanceof test
    }

    @Test
    public void deduplicateInstanceOfTest() {
        String snippet = "deduplicateInstanceOfSnippet";
        StructuredGraph graph = afterFlowSensitiveReduce(snippet);
        List<InstanceOfNode> ioNodes = getNodes(graph, InstanceOfNode.class);
        assertDeepEquals(1, ioNodes.size());

    }

    // ---------------------------------------------
    // ----------------- UTILITIES -----------------
    // ---------------------------------------------

    private PhaseContext getPhaseContext() {
        return new PhaseContext(getProviders(), null);
    }

    private static StructuredGraph dce(StructuredGraph graph) {
        new DeadCodeEliminationPhase().apply(graph);
        return graph;
    }

    private StructuredGraph canonicalize(StructuredGraph graph) {
        new CanonicalizerPhase(true).apply(graph, getPhaseContext());
        return graph;
    }

    private StructuredGraph flowSensitiveReduce(StructuredGraph graph) {
        new FlowSensitiveReductionPhase(getMetaAccess()).apply(graph, getPhaseContext());
        return graph;
    }

    public static <N extends Node> List<N> getNodes(StructuredGraph graph, Class<N> nodeClass) {
        return graph.getNodes().filter(nodeClass).snapshot();
    }

    public <N extends Node> void nodeCountEquals(StructuredGraph graph, Class<N> nodeClass, int expected) {
        assertDeepEquals(expected, getNodes(graph, nodeClass).size());
    }

    public StructuredGraph afterFlowSensitiveReduce(String snippet) {
        StructuredGraph before = canonicalize(parseEager(snippet));
        // visualize(before, snippet + "-before");
        StructuredGraph result = flowSensitiveReduce(before);
        // visualize(result, snippet + "-after");
        return result;
    }

    public StructuredGraph visualize(StructuredGraph graph, String title) {
        DebugConfig debugConfig = DebugScope.getConfig();
        DebugConfig fixedConfig = Debug.fixedConfig(0, Debug.DEFAULT_LOG_LEVEL, false, false, false, false, debugConfig.dumpHandlers(), debugConfig.verifyHandlers(), debugConfig.output());
        try (DebugConfigScope s = Debug.setConfig(fixedConfig)) {
            Debug.dump(graph, title);

            return graph;
        }
    }

}
