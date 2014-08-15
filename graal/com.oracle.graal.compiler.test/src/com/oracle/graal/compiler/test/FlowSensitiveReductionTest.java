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

import static com.oracle.graal.nodes.ConstantNode.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.cfs.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Collection of tests for {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReductionPhase}
 * including those that triggered bugs in this phase.
 */
public class FlowSensitiveReductionTest extends GraalCompilerTest {

    public static Object field;

    static class Entry {

        final String name;

        public Entry(String name) {
            this.name = name;
        }
    }

    static class EntryWithNext extends Entry {

        public EntryWithNext(String name, Entry next) {
            super(name);
            this.next = next;
        }

        final Entry next;
    }

    public static Entry search(Entry start, String name, Entry alternative) {
        Entry current = start;
        do {
            while (current instanceof EntryWithNext) {
                if (name != null && current.name == name) {
                    current = null;
                } else {
                    Entry next = ((EntryWithNext) current).next;
                    current = next;
                }
            }

            if (current != null) {
                if (current.name.equals(name)) {
                    return current;
                }
            }
            if (current == alternative) {
                return null;
            }
            current = alternative;

        } while (true);
    }

    /**
     * This test presents a code pattern that triggered a bug where a (non-eliminated) checkcast
     * caused an enclosing instanceof (for the same object and target type) to be incorrectly
     * eliminated.
     */
    @Test
    public void testReanchoringIssue() {
        Entry end = new Entry("end");
        EntryWithNext e1 = new EntryWithNext("e1", end);
        EntryWithNext e2 = new EntryWithNext("e2", e1);

        test("search", e2, "e3", new Entry("e4"));
    }

    @SuppressWarnings("unused")
    public static int testNullnessSnippet(Object a, Object b) {
        if (a == null) {
            if (a == b) {
                if (b == null) {
                    return 1;
                } else {
                    return -2;
                }
            } else {
                if (b == null) {
                    return -3;
                } else {
                    return 4;
                }
            }
        } else {
            if (a == b) {
                if (b == null) {
                    return -5;
                } else {
                    return 6;
                }
            } else {
                if (b == null) {
                    return 7;
                } else {
                    return 8;
                }
            }
        }
    }

    @Test
    public void testNullness() {
        test("testNullnessSnippet", null, null);
        test("testNullnessSnippet", null, new Object());
        test("testNullnessSnippet", new Object(), null);
        test("testNullnessSnippet", new Object(), new Object());

        StructuredGraph graph = parseEager("testNullnessSnippet");
        PhaseContext context = new PhaseContext(getProviders(), null);
        new FlowSensitiveReductionPhase(getMetaAccess()).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, context);
        for (ConstantNode constant : getConstantNodes(graph)) {
            assertTrue("unexpected constant: " + constant, constant.asConstant().isNull() || constant.asConstant().asInt() > 0);
        }
    }

    @SuppressWarnings("unused")
    public static int testDisjunctionSnippet(Object a) {
        try {
            if (a instanceof Integer) {
                if (a == null) {
                    return -1;
                } else {
                    return 2;
                }
            } else {
                return 3;
            }
        } finally {
            field = null;
        }
    }

    @Test
    public void testDisjunction() {
        StructuredGraph graph = parseEager("testDisjunctionSnippet");
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), null));
        IfNode ifNode = (IfNode) graph.start().next();
        InstanceOfNode instanceOf = (InstanceOfNode) ifNode.condition();
        IsNullNode x = graph.unique(new IsNullNode(graph.getParameter(0)));
        InstanceOfNode y = instanceOf;
        ShortCircuitOrNode disjunction = graph.unique(new ShortCircuitOrNode(x, false, y, false, NOT_FREQUENT_PROBABILITY));
        LogicNegationNode negation = graph.unique(new LogicNegationNode(disjunction));
        ifNode.setCondition(negation);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), null));
        new FlowSensitiveReductionPhase(getMetaAccess()).apply(graph, new PhaseContext(getProviders(), null));
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), null));
        for (ConstantNode constant : getConstantNodes(graph)) {
            assertTrue("unexpected constant: " + constant, constant.asConstant().isNull() || constant.asConstant().asInt() > 0);
        }
    }

    public static int testInvokeSnippet(Number n) {
        if (n instanceof Integer) {
            return n.intValue();
        } else {
            return 1;
        }
    }

    @Test
    public void testInvoke() {
        test("testInvokeSnippet", new Integer(16));
        StructuredGraph graph = parseEager("testInvokeSnippet");
        PhaseContext context = new PhaseContext(getProviders(), null);
        new CanonicalizerPhase(true).apply(graph, context);
        new FlowSensitiveReductionPhase(getMetaAccess()).apply(graph, context);

        InvokeNode invoke = graph.getNodes().filter(InvokeNode.class).first();
        assertDeepEquals(InvokeKind.Special, ((MethodCallTargetNode) invoke.callTarget()).invokeKind());
    }

    public static void testTypeMergingSnippet(Object o, boolean b) {
        if (b) {
            if (!(o instanceof Double)) {
                return;
            }
        } else {
            if (!(o instanceof Integer)) {
                return;
            }
        }

        /*
         * For this test the conditional elimination has to correctly merge the type information it
         * has about o, so that it can remove the check on Number.
         */
        if (!(o instanceof Number)) {
            field = o;
        }
    }

    @Test
    public void testTypeMerging() {
        StructuredGraph graph = parseEager("testTypeMergingSnippet");
        PhaseContext context = new PhaseContext(getProviders(), null);
        new CanonicalizerPhase(true).apply(graph, context);
        new FlowSensitiveReductionPhase(getMetaAccess()).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), null));

        assertDeepEquals(0, graph.getNodes().filter(StoreFieldNode.class).count());
    }

    public static String testInstanceOfCheckCastSnippet(Object e) {
        if (e instanceof Entry) {
            return ((Entry) e).name;
        }
        return null;
    }

    @Test
    public void testInstanceOfCheckCast() {
        StructuredGraph graph = parseEager("testInstanceOfCheckCastSnippet");
        PhaseContext context = new PhaseContext(getProviders(), null);
        new CanonicalizerPhase(true).apply(graph, context);
        new FlowSensitiveReductionPhase(getMetaAccess()).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), null));

        assertDeepEquals(0, graph.getNodes().filter(CheckCastNode.class).count());
    }

    public static int testDuplicateNullChecksSnippet(Object a) {
        if (a == null) {
            return 2;
        }
        try {
            return ((Integer) a).intValue();
        } catch (ClassCastException e) {
            return 0;
        }
    }

    @Test
    @Ignore
    public void testDuplicateNullChecks() {
        // This tests whether explicit null checks properly eliminate later null guards. Currently
        // it's failing.
        StructuredGraph graph = parseEager("testDuplicateNullChecksSnippet");
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase(true);
        PhaseContext context = new PhaseContext(getProviders(), null);

        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);
        new FloatingReadPhase().apply(graph);
        new FlowSensitiveReductionPhase(getMetaAccess()).apply(graph, context);
        canonicalizer.apply(graph, context);

        assertDeepEquals(1, graph.getNodes().filter(GuardNode.class).count());
    }

}
