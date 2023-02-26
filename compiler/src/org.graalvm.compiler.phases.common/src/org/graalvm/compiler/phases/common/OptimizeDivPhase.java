/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import java.util.Optional;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerMulHighNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import org.graalvm.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;

import jdk.vm.ci.code.CodeUtil;

/**
 * Phase that optimizes integer division operation by using various mathematical foundations to
 * express it in faster, equivalent, arithmetic.
 */
public class OptimizeDivPhase extends BasePhase<CoreProviders> {
    protected final CanonicalizerPhase canonicalizer;

    public OptimizeDivPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return this.canonicalizer.notApplicableTo(graphState);
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener();
        try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
            for (IntegerDivRemNode rem : graph.getNodes(IntegerDivRemNode.TYPE)) {
                if (rem instanceof SignedRemNode && isDivByNonZeroConstant(rem)) {
                    try (DebugCloseable position = rem.withNodeSourcePosition()) {
                        optimizeRem(rem);
                    }
                }
            }
            for (SignedFloatingIntegerRemNode nonTrappingRem : graph.getNodes(SignedFloatingIntegerRemNode.TYPE)) {
                if (isDivByNonZeroConstant(nonTrappingRem)) {
                    try (DebugCloseable position = nonTrappingRem.withNodeSourcePosition()) {
                        optimizeRem(nonTrappingRem);
                    }
                }
            }
            for (IntegerDivRemNode div : graph.getNodes(IntegerDivRemNode.TYPE)) {
                if (div instanceof SignedDivNode && isDivByNonZeroConstant(div)) {
                    try (DebugCloseable position = div.withNodeSourcePosition()) {
                        optimizeSignedDiv(div);
                    }
                }
            }
            for (SignedFloatingIntegerDivNode div : graph.getNodes(SignedFloatingIntegerDivNode.TYPE)) {
                if (isDivByNonZeroConstant(div)) {
                    try (DebugCloseable position = div.withNodeSourcePosition()) {
                        optimizeSignedDiv(div);
                    }
                }
            }
        }
        if (!ec.getNodes().isEmpty()) {
            canonicalizer.applyIncremental(graph, context, ec.getNodes());
        }

    }

    @Override
    public float codeSizeIncrease() {
        return 5.0f;
    }

    protected static boolean isDivByNonZeroConstant(Canonicalizable.Binary<ValueNode> divRemNode) {
        return divRemNode.getY().isConstant() && divRemNode.getY().asJavaConstant().asLong() != 0;
    }

    protected final void optimizeRem(Canonicalizable.Binary<ValueNode> rem) {
        assert rem instanceof IntegerDivRemNode || rem instanceof SignedFloatingIntegerRemNode;
        // Java spec 15.17.3.: (a/b)*b+(a%b) == a
        // so a%b == a-(a/b)*b
        StructuredGraph graph = ((ValueNode) rem).graph();
        ValueNode div = findDivForRem((ValueNode) rem);
        ValueNode mul = BinaryArithmeticNode.mul(graph, div, rem.getY(), NodeView.DEFAULT);
        ValueNode result = BinaryArithmeticNode.sub(graph, rem.getX(), mul, NodeView.DEFAULT);
        replacePreserveOriginalStamp(graph, (ValueNode) rem, result);
    }

    private ValueNode findDivForRem(ValueNode val) {
        if (val instanceof IntegerDivRemNode) {
            IntegerDivRemNode rem = (IntegerDivRemNode) val;
            if (rem.next() instanceof IntegerDivRemNode) {
                IntegerDivRemNode div = (IntegerDivRemNode) rem.next();
                if (div.getOp() == IntegerDivRemNode.Op.DIV && div.getType() == rem.getType() && div.getX() == rem.getX() && div.getY() == rem.getY()) {
                    return div;
                }
            }
            if (rem.predecessor() instanceof IntegerDivRemNode) {
                IntegerDivRemNode div = (IntegerDivRemNode) rem.predecessor();
                if (div.getOp() == IntegerDivRemNode.Op.DIV && div.getType() == rem.getType() && div.getX() == rem.getX() && div.getY() == rem.getY()) {
                    return div;
                }
            }
            // not found, create a new one (will be optimized away later)
            ValueNode div = rem.graph().addOrUniqueWithInputs(createDiv(rem));
            if (div instanceof FixedNode) {
                rem.graph().addAfterFixed(rem, (FixedNode) div);
            }
            return div;
        } else if (val instanceof SignedFloatingIntegerRemNode) {
            ValueNode div = val.graph().addOrUniqueWithInputs(createDiv(val));
            return div;
        }
        throw GraalError.shouldNotReachHere();
    }

    protected ValueNode createDiv(ValueNode val) {
        if (val instanceof SignedRemNode) {
            SignedRemNode rem = (SignedRemNode) val;
            return SignedDivNode.create(rem.getX(), rem.getY(), rem.getZeroGuard(), NodeView.DEFAULT);
        } else {
            SignedFloatingIntegerRemNode rem = (SignedFloatingIntegerRemNode) val;
            return SignedFloatingIntegerDivNode.create(((BinaryNode) val).getX(), ((BinaryNode) val).getY(), NodeView.DEFAULT, rem.getGuard(), rem.divisionOverflowIsJVMSCompliant());
        }
    }

    protected static void optimizeSignedDiv(Canonicalizable.Binary<ValueNode> div) {
        ValueNode forX = div.getX();
        long c = div.getY().asJavaConstant().asLong();
        assert c != 1 && c != -1 && c != 0;

        IntegerStamp dividendStamp = (IntegerStamp) forX.stamp(NodeView.DEFAULT);
        int bitSize = dividendStamp.getBits();
        Pair<Long, Integer> nums = magicDivideConstants(c, bitSize);
        long magicNum = nums.getLeft().longValue();
        int shiftNum = nums.getRight().intValue();
        assert shiftNum >= 0;
        ConstantNode m = ConstantNode.forLong(magicNum);

        ValueNode value;
        if (bitSize == 32) {
            value = new MulNode(new SignExtendNode(forX, 64), m);
            if ((c > 0 && magicNum < 0) || (c < 0 && magicNum > 0)) {
                // Get upper 32-bits of the result
                value = NarrowNode.create(new RightShiftNode(value, ConstantNode.forInt(32)), 32, NodeView.DEFAULT);
                if (c > 0) {
                    value = BinaryArithmeticNode.add(value, forX, NodeView.DEFAULT);
                } else {
                    value = BinaryArithmeticNode.sub(value, forX, NodeView.DEFAULT);
                }
                if (shiftNum > 0) {
                    value = new RightShiftNode(value, ConstantNode.forInt(shiftNum));
                }
            } else {
                value = new RightShiftNode(value, ConstantNode.forInt(32 + shiftNum));
                value = new NarrowNode(value, Integer.SIZE);
            }
        } else {
            assert bitSize == 64;
            value = new IntegerMulHighNode(forX, m);
            if (c > 0 && magicNum < 0) {
                value = BinaryArithmeticNode.add(value, forX, NodeView.DEFAULT);
            } else if (c < 0 && magicNum > 0) {
                value = BinaryArithmeticNode.sub(value, forX, NodeView.DEFAULT);
            }
            if (shiftNum > 0) {
                value = new RightShiftNode(value, ConstantNode.forInt(shiftNum));
            }
        }

        if (c < 0) {
            ConstantNode s = ConstantNode.forInt(bitSize - 1);
            ValueNode sign = UnsignedRightShiftNode.create(value, s, NodeView.DEFAULT);
            value = BinaryArithmeticNode.add(value, sign, NodeView.DEFAULT);
        } else if (dividendStamp.canBeNegative()) {
            ConstantNode s = ConstantNode.forInt(bitSize - 1);
            ValueNode sign = UnsignedRightShiftNode.create(forX, s, NodeView.DEFAULT);
            value = BinaryArithmeticNode.add(value, sign, NodeView.DEFAULT);
        }

        StructuredGraph graph = ((ValueNode) div).graph();
        assert div instanceof SignedDivNode || div instanceof SignedFloatingIntegerDivNode : "Unknown or invalid div:" + div;
        replacePreserveOriginalStamp(graph, (ValueNode) div, value);
    }

    /**
     * When this phase replaces a node with a faster computed version we have to inject a pi node
     * with the old stamp. This is done to ensure a stamp never gets worse, compensating for
     * limitations in the current integer stamp representation. This phase injects "magic" knowledge
     * about two's complement division into the graph that cannot be expressed otherwise.
     */
    private static void replacePreserveOriginalStamp(StructuredGraph graph, ValueNode originalNode, ValueNode replacement) {
        Stamp oldStamp = originalNode.stamp(NodeView.DEFAULT);
        ValueNode replacementWrapped = graph.addOrUniqueWithInputs(PiNode.create(replacement, oldStamp));
        if (originalNode instanceof FixedNode) {
            graph.replaceFixed((FixedWithNextNode) originalNode, replacementWrapped);
        } else {
            originalNode.replaceAndDelete(replacementWrapped);
        }
        graph.getOptimizationLog().report(OptimizeDivPhase.class, "DivOptimization", originalNode);
    }

    /**
     * Borrowed from Hacker's Delight by Henry S. Warren, Jr. Figure 10-1.
     */
    private static Pair<Long, Integer> magicDivideConstants(long divisor, int size) {
        final long twoW = 1L << (size - 1);                // 2 ^ (size - 1).
        long t = twoW + (divisor >>> 63);
        long ad = Math.abs(divisor);
        long anc = t - 1 - Long.remainderUnsigned(t, ad);  // Absolute value of nc.
        long q1 = Long.divideUnsigned(twoW, anc);          // Init. q1 = 2**p/|nc|.
        long r1 = Long.remainderUnsigned(twoW, anc);       // Init. r1 = rem(2**p, |nc|).
        long q2 = Long.divideUnsigned(twoW, ad);           // Init. q2 = 2**p/|d|.
        long r2 = Long.remainderUnsigned(twoW, ad);        // Init. r2 = rem(2**p, |d|).
        long delta;

        int p = size - 1;                                  // Init. p.
        do {
            p = p + 1;
            q1 = 2 * q1;                                   // Update q1 = 2**p/|nc|.
            r1 = 2 * r1;                                   // Update r1 = rem(2**p, |nc|).
            if (Long.compareUnsigned(r1, anc) >= 0) {      // Must be an unsigned comparison.
                q1 = q1 + 1;
                r1 = r1 - anc;
            }
            q2 = 2 * q2;                                   // Update q2 = 2**p/|d|.
            r2 = 2 * r2;                                   // Update r2 = rem(2**p, |d|).
            if (Long.compareUnsigned(r2, ad) >= 0) {       // Must be an unsigned comparison.
                q2 = q2 + 1;
                r2 = r2 - ad;
            }
            delta = ad - r2;
        } while (Long.compareUnsigned(q1, delta) < 0 || (q1 == delta && r1 == 0));

        long magic = CodeUtil.signExtend(q2 + 1, size);
        if (divisor < 0) {
            magic = -magic;
        }
        return Pair.create(magic, p - size);
    }

}
