/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.GuardedValueNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.SideEffectNode;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.util.GraphOrder;

/** Tests conditional elimination of whole {@link ShortCircuitOrNode} conditions. */
public class ConditionalEliminationShortCircuitOrTest extends ConditionalEliminationTestBase {

    private static void referenceTrue(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                sink0++;
            }
        }
    }

    private static void referenceFalse(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                GraalDirectives.sideEffect();
            }
        }
    }

    private static void test00Snippet(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                if (GraalDirectives.shortCircuitOr(a == 0, b == 0)) {
                    sink0++;
                }
            }
        }
    }

    @Test
    public void test00() {
        testConditionalElimination("test00Snippet", "referenceTrue");
    }

    private static void test01Snippet(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                if (GraalDirectives.shortCircuitOr(a == 0, !(b == 0))) {
                    sink0++;
                }
            }
        }
    }

    @Test
    public void test01() {
        testConditionalElimination("test01Snippet", "referenceTrue");
    }

    private static void test10Snippet(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                if (GraalDirectives.shortCircuitOr(!(a == 0), b == 0)) {
                    sink0++;
                }
            }
        }
    }

    @Test
    public void test10() {
        testConditionalElimination("test10Snippet", "referenceTrue");
    }

    private static void test11Snippet(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                if (GraalDirectives.shortCircuitOr(!(a == 0), !(b == 0))) {
                    sink0++;
                }
                GraalDirectives.sideEffect();
            }
        }
    }

    @Test
    public void test11() {
        testConditionalElimination("test11Snippet", "referenceFalse");
    }

    private static void test00SwappedSnippet(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                if (GraalDirectives.shortCircuitOr(b == 0, a == 0)) {
                    sink0++;
                }
            }
        }
    }

    @Test
    public void test00Swapped() {
        testConditionalElimination("test00SwappedSnippet", "referenceTrue");
    }

    private static void test01SwappedSnippet(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                if (GraalDirectives.shortCircuitOr(!(b == 0), a == 0)) {
                    sink0++;
                }
            }
        }
    }

    @Test
    public void test01Swapped() {
        testConditionalElimination("test01SwappedSnippet", "referenceTrue");
    }

    private static void test10SwappedSnippet(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                if (GraalDirectives.shortCircuitOr(b == 0, !(a == 0))) {
                    sink0++;
                }
            }
        }
    }

    @Test
    public void test10Swapped() {
        testConditionalElimination("test10SwappedSnippet", "referenceTrue");
    }

    private static void test11SwappedSnippet(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                if (GraalDirectives.shortCircuitOr(!(b == 0), !(a == 0))) {
                    sink0++;
                }
                GraalDirectives.sideEffect();
            }
        }
    }

    @Test
    public void test11Swapped() {
        testConditionalElimination("test11SwappedSnippet", "referenceFalse");
    }

    private static int swappedFalseShortCircuitOrWithGuardedUsageSnippet(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                if (GraalDirectives.shortCircuitOr(!(b == 0), !(a == 0))) {
                    GraalDirectives.sideEffect(1);
                } else {
                    GraalDirectives.sideEffect(a);
                    return 1;
                }
            }
        }
        return 0;
    }

    @Test
    public void testFalseShortCircuitOrUsesDeepestProofGuard() {
        StructuredGraph graph = parseEager("swappedFalseShortCircuitOrWithGuardedUsageSnippet", AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        prepareGraph(graph, canonicalizer, getProviders(), false);

        ValueNode a = graph.getParameter(0);
        IfNode targetIf = null;
        SideEffectNode sideEffect = null;
        for (IfNode ifNode : graph.getNodes(IfNode.TYPE)) {
            if (ifNode.condition() instanceof ShortCircuitOrNode) {
                targetIf = ifNode;
            }
        }
        for (SideEffectNode candidate : graph.getNodes().filter(SideEffectNode.class)) {
            if (candidate.getValue() == a) {
                sideEffect = candidate;
            }
        }

        Assert.assertNotNull("Expected an IfNode for the short circuit OR.", targetIf);
        Assert.assertNotNull("Expected a side effect on the false successor.", sideEffect);
        Assert.assertTrue("Expected the b == 0 successor to directly precede the short circuit OR.", targetIf.predecessor() instanceof AbstractBeginNode);

        AbstractBeginNode bEqualsZeroSuccessor = (AbstractBeginNode) targetIf.predecessor();
        ValueNode guardedValueNode = graph.addOrUniqueWithInputs(GuardedValueNode.create(a, targetIf.falseSuccessor()));
        Assert.assertTrue("Expected a guarded value node.", guardedValueNode instanceof GuardedValueNode);
        GuardedValueNode guardedValue = (GuardedValueNode) guardedValueNode;
        sideEffect.replaceFirstInput(a, guardedValue);
        Assert.assertSame(targetIf.falseSuccessor(), guardedValue.getGuard());
        Assert.assertTrue("The false successor must have a guarded usage for this regression.", targetIf.falseSuccessor().hasUsagesOfType(InputType.Guard));

        new ConditionalEliminationPhase(canonicalizer, true).apply(graph, getDefaultHighTierContext());

        Assert.assertSame("The surviving guarded usage must depend on the b == 0 successor.", bEqualsZeroSuccessor, guardedValue.getGuard());
        GraphOrder.assertSchedulableGraph(graph);
    }

    private static void arraycopyAliasSnippet(Object src, int srcPos, Object dest, int destPos) {
        if (src == dest && srcPos < destPos) {
            if (GraalDirectives.injectBranchProbability(0.0, !GraalDirectives.shortCircuitOr(src != dest, destPos >= srcPos))) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            GraalDirectives.sideEffect(1);
        } else {
            if (GraalDirectives.injectBranchProbability(0.0, !GraalDirectives.shortCircuitOr(src != dest, srcPos >= destPos))) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            GraalDirectives.sideEffect(2);
        }
    }

    private static void arraycopyAliasReference(Object src, int srcPos, Object dest, int destPos) {
        if (src == dest && srcPos < destPos) {
            GraalDirectives.sideEffect(1);
        } else {
            /*
             * This false branch is the merge of two paths, one for src != dest and one for srcPos >= destPos.
             * Conditional elimination does not track disjunctive facts at merges, so it cannot eliminate this
             * ShortCircuitOr.
             */
            if (GraalDirectives.injectBranchProbability(0.0, !GraalDirectives.shortCircuitOr(src != dest, srcPos >= destPos))) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            GraalDirectives.sideEffect(2);
        }
    }

    @Test
    public void testArraycopyAliasCondition() {
        testConditionalElimination("arraycopyAliasSnippet", "arraycopyAliasReference");
    }

    private static void arraycopyAliasExplicitConditionSnippet(Object src, int srcPos, Object dest, int destPos) {
        if (src == dest && srcPos < destPos) {
            if (GraalDirectives.injectBranchProbability(0.0, !GraalDirectives.shortCircuitOr(src != dest, destPos >= srcPos))) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            GraalDirectives.sideEffect(1);
        } else if (GraalDirectives.injectBranchProbability(0.0, !GraalDirectives.shortCircuitOr(src != dest, srcPos >= destPos))) {
            GraalDirectives.log("should not reach here");
        } else {
            if (GraalDirectives.injectBranchProbability(0.0, !GraalDirectives.shortCircuitOr(src != dest, srcPos >= destPos))) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            GraalDirectives.sideEffect(2);
        }
    }

    private static void arraycopyAliasExplicitConditionReference(Object src, int srcPos, Object dest, int destPos) {
        if (src == dest && srcPos < destPos) {
            GraalDirectives.sideEffect(1);
        } else if (GraalDirectives.injectBranchProbability(0.0, !GraalDirectives.shortCircuitOr(src != dest, srcPos >= destPos))) {
            GraalDirectives.log("should not reach here");
        } else {
            GraalDirectives.sideEffect(2);
        }
    }

    @Test
    public void testArraycopyAliasExplicitCondition() {
        testConditionalElimination("arraycopyAliasExplicitConditionSnippet", "arraycopyAliasExplicitConditionReference");
    }
}
