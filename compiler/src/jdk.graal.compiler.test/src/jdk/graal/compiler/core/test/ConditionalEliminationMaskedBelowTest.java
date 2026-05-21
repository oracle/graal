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

import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * Tests that conditional elimination (CE) folds masked bounds after proving the upper value is
 * positive.
 *
 * <pre>{@code
 * // Before
 * if (upper > 0) {
 *     int masked = index & (upper - 1);
 *     if (Integer.compareUnsigned(masked, upper) < 0) {
 *         inBounds();
 *     } else {
 *         outOfBounds();
 *     }
 * }
 *
 * // After
 * if (upper > 0) {
 *     inBounds();
 * }
 * }</pre>
 */
public class ConditionalEliminationMaskedBelowTest extends ConditionalEliminationTestBase {

    public static Object arrayAccessProofSnippet(Object[] array, int index) {
        if (array.length > 0) {
            return array[index & (array.length - 1)];
        }
        return null;
    }

    public static Object hashMapGetProofSnippet(Object[] table, int index) {
        Object[] tab;
        Object first;
        int n;
        if ((tab = table) != null && (n = tab.length) > 0 && (first = tab[(n - 1) & index]) != null) {
            return first;
        }
        return null;
    }

    public static void arrayLengthProofSnippet(Object[] array, int index) {
        int length = array.length;
        if (length == 0) {
            sink0 = -1;
        } else {
            int wrapped = index & (length - 1);
            if (Integer.compareUnsigned(wrapped, length) < 0) {
                sink1 = 0;
            } else {
                sink2 = -2;
            }
        }
    }

    public static void noArrayLengthProofSnippet(Object[] array, int index) {
        int wrapped = index & (array.length - 1);
        if (Integer.compareUnsigned(wrapped, array.length) < 0) {
            sink1 = 0;
        } else {
            sink2 = -2;
        }
    }

    public static void differentArrayLengthProofSnippet(Object[] array, Object[] other, int index) {
        int firstLength = array.length;
        if (firstLength == 0) {
            sink0 = -1;
        } else {
            int wrapped = index & (other.length - 1);
            if (Integer.compareUnsigned(wrapped, other.length) < 0) {
                sink1 = 0;
            } else {
                sink2 = -2;
            }
        }
    }

    public static void arrayLengthProofReferenceSnippet(Object[] array, int index) {
        int length = array.length;
        if (length == 0) {
            sink0 = -1;
        } else {
            // Keep the reference graph's parameter shape aligned with the folded test snippets.
            int wrapped = index & (length - 1);
            sink1 = 0;
        }
    }

    /**
     * Checks that repeated {@code array.length} nodes are deduplicated, letting
     * {@code array[index & (array.length - 1)]} have no remaining bounds check after the length has
     * a positive tracked stamp.
     */
    @Test
    public void testArrayAccessProofFoldsBoundsCheck() {
        assertBoundsCheckFolds("arrayAccessProofSnippet");
    }

    /**
     * Checks the same masked array access shape as the table lookup in
     * {@link java.util.HashMap#get(Object)}.
     */
    @Test
    public void testHashMapGetProofFoldsBoundsCheck() {
        assertBoundsCheckFolds("hashMapGetProofSnippet");
    }

    /**
     * Checks that {@code length != 0} folds the branch guarded by
     * {@code Integer.compareUnsigned(index & (length - 1), length) < 0}.
     */
    @Test
    public void testArrayLengthProofFolds() {
        testConditionalElimination("arrayLengthProofSnippet", "arrayLengthProofReferenceSnippet", false, true);
    }

    /**
     * Checks that {@code Integer.compareUnsigned(index & (array.length - 1), array.length) < 0}
     * stays guarded without {@code array.length != 0}.
     */
    @Test
    public void testNoArrayLengthProofDoesNotFold() {
        testConditionalElimination("noArrayLengthProofSnippet", "noArrayLengthProofSnippet", false, true);
    }

    /**
     * Checks that {@code array.length != 0} does not fold
     * {@code Integer.compareUnsigned(index & (other.length - 1), other.length) < 0}.
     */
    @Test
    public void testDifferentArrayLengthProofDoesNotFold() {
        testConditionalElimination("differentArrayLengthProofSnippet", "differentArrayLengthProofSnippet", false, true);
    }

    private void assertBoundsCheckFolds(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        CoreProviders context = getProviders();

        prepareGraph(graph, canonicalizer, context, true);
        new FloatingReadPhase(canonicalizer).apply(graph, context);
        Assert.assertEquals(1, countBoundsCheckGuards(graph));

        new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);
        canonicalizer.apply(graph, context);

        Assert.assertEquals(0, countBoundsCheckGuards(graph));
    }

    private static long countBoundsCheckGuards(StructuredGraph graph) {
        return graph.getNodes(GuardNode.TYPE).filter(guard -> ((GuardNode) guard).getReason() == DeoptimizationReason.BoundsCheckException).count() +
                        graph.getNodes(FixedGuardNode.TYPE).filter(guard -> ((FixedGuardNode) guard).getReason() == DeoptimizationReason.BoundsCheckException).count();
    }
}
