/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * Tests the correctness of conditional elimination when inverting stamps along
 * {@link SignExtendNode}. The test artificially creates a graph whith the optimizable pattern:
 * {@code ((val & CONST) == CONST)}, which provides information about the set bits in val, if the
 * condition is used in a guard which is assumed to hold. The partial information about bits which
 * are set in {@code x} are propagated "upwards". A {@link SignExtendNode} must treat the partial
 * information correctly with respect to the extension bits and handle contradictions.
 * </p>
 * If {@code CONST = 1 << 4 = 0001 0000} and we assume the condition holds, we can refine a stamp
 * {@code xxxx xx11} for {@code val} to {@code xxx1 xx11}. Inverting this refined stamp along an
 * artificial 4->8 bit sign extension should treat the extension as {@code 1111} and produce an
 * inverted stamp of {@code xx11} or, even better {@code 1x11}, because we know the MSB has to be
 * one from the extension.
 * </p>
 * If the inversion is performed incorrectly, the inner condition could be considered as always
 * false and removing it during conditional elimination.
 */
public class ConditionalEliminationStampInversionTest extends GraalCompilerTest {

    public static boolean snippet(byte b) {
        short s = b;
        int i = s;

        if ((i & (1 << 16)) == (1 << 16)) {
            if (s == -5) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void test() throws InvalidInstalledCodeException {
        StructuredGraph g = parseEager("snippet", AllowAssumptions.YES);

        // replace the IntegerTest by an equivalent and/== pattern
        ValueNode intTest = g.getNodes().filter(IntegerTestNode.class).first();
        assertTrue("IntegerTestNode expected in graph.", intTest != null);

        ValueNode signExtend = g.getNodes().filter(SignExtendNode.class).first();
        assertTrue("SignExtendNode expected in graph.", signExtend != null);

        ValueNode and = g.addOrUnique(AndNode.create(signExtend, ConstantNode.forInt(1 << 16, g), NodeView.DEFAULT));
        and.inferStamp();
        LogicNode intEq = g.addOrUnique(IntegerEqualsNode.create(and, ConstantNode.forInt(1 << 16, g), NodeView.DEFAULT));
        intEq.inferStamp();

        intTest.replaceAtUsages(intEq);

        // replace the if by a fixed guard to trigger conditional elimination for the and/== pattern
        ValueNode ifNode = (ValueNode) intEq.usages().first();
        assertTrue("IfNode expected as first usage of IntegerEqualsNode.", ifNode != null && ifNode instanceof IfNode);

        FixedGuardNode guard = g.add(new FixedGuardNode(intEq, DeoptimizationReason.ArithmeticException, DeoptimizationAction.InvalidateRecompile));
        GraphUtil.killCFG(((IfNode) ifNode).trueSuccessor());
        g.replaceSplitWithFixed((IfNode) ifNode, guard, ((IfNode) ifNode).falseSuccessor());

        new ConditionalEliminationPhase(createCanonicalizerPhase(), false).apply(g, getDefaultHighTierContext());

        // the inner condition should still be alive and the following execution return true
        assert (boolean) getCode(getResolvedJavaMethod("snippet"), g, true, true, getInitialOptions()).executeVarargs((byte) -5);
    }
}
