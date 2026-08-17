/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.Optional;

import org.graalvm.collections.Pair;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerMulHighNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SignedDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.nodes.calc.UnsignedDivNode;
import jdk.graal.compiler.nodes.calc.UnsignedRemNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.replacements.nodes.arithmetic.UnsignedMulHighNode;
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
                if (rem.getOp() == IntegerDivRemNode.Op.REM && isDivByNonZeroConstantNonOverflowingAbs(rem)) {
                    try (DebugCloseable position = rem.withNodeSourcePosition()) {
                        optimizeRem(rem);
                    }
                }
            }
            for (SignedFloatingIntegerRemNode nonTrappingRem : graph.getNodes(SignedFloatingIntegerRemNode.TYPE)) {
                if (isDivByNonZeroConstantNonOverflowingAbs(nonTrappingRem)) {
                    try (DebugCloseable position = nonTrappingRem.withNodeSourcePosition()) {
                        optimizeRem(nonTrappingRem);
                    }
                }
            }
            for (IntegerDivRemNode div : graph.getNodes(IntegerDivRemNode.TYPE)) {
                if (isDivByNonZeroConstantNonOverflowingAbs(div)) {
                    if (div instanceof SignedDivNode) {
                        try (DebugCloseable position = div.withNodeSourcePosition()) {
                            optimizeSignedDiv(div);
                        }
                    } else if (div instanceof UnsignedDivNode) {
                        try (DebugCloseable position = div.withNodeSourcePosition()) {
                            optimizeUnsignedDiv((UnsignedDivNode) div);
                        }
                    }
                }
            }
            for (SignedFloatingIntegerDivNode div : graph.getNodes(SignedFloatingIntegerDivNode.TYPE)) {
                if (isDivByNonZeroConstantNonOverflowingAbs(div)) {
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

    protected static boolean isDivByNonZeroConstantNonOverflowingAbs(Canonicalizable.Binary<ValueNode> divRemNode) {
        if (divRemNode.getY().isConstant()) {
            ValueNode divisor = divRemNode.getY();
            long constantVal = divisor.asJavaConstant().asLong();
            return constantVal != 0 && !NumUtil.absOverflows(constantVal, IntegerStamp.getBits(divisor.stamp(NodeView.DEFAULT)));
        }
        return false;
    }

    protected final void optimizeRem(Canonicalizable.Binary<ValueNode> rem) {
        assert rem instanceof IntegerDivRemNode || rem instanceof SignedFloatingIntegerRemNode : Assertions.errorMessageContext("rem", rem);
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
        throw GraalError.shouldNotReachHereUnexpectedValue(val); // ExcludeFromJacocoGeneratedReport
    }

    protected ValueNode createDiv(ValueNode val) {
        if (val instanceof UnsignedRemNode) {
            UnsignedRemNode rem = (UnsignedRemNode) val;
            return UnsignedDivNode.create(rem.getX(), rem.getY(), rem.getZeroGuard(), NodeView.DEFAULT);
        } else if (val instanceof SignedRemNode) {
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
        if (c == 1 || c == -1 || c == 0) {
            /*
             * Leave to canonicalization. The constant may have been produced by optimizing another
             * div, without a chance to canonicalize this div yet.
             */
            return;
        }

        IntegerStamp dividendStamp = (IntegerStamp) forX.stamp(NodeView.DEFAULT);
        int bitSize = dividendStamp.getBits();
        Pair<Long, Integer> nums = magicDivideConstants(c, bitSize);
        long magicNum = nums.getLeft().longValue();
        int shiftNum = nums.getRight().intValue();
        assert NumUtil.assertNonNegativeInt(shiftNum);
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
            assert bitSize == 64 : bitSize;
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

    private static void optimizeUnsignedDiv(UnsignedDivNode div) {
        StructuredGraph graph = div.graph();
        long divisor = div.getY().asJavaConstant().asLong();
        if (divisor == 1 || divisor == -1 || divisor == 0) {
            /*
             * Leave to canonicalization. The constant may have been produced by optimizing another
             * div, without a chance to canonicalize this div yet.
             */
            return;
        }

        // Hacker's delight chapter 10
        // we only need to code the case for non-power-of-2
        // the power-of-2 case is handled by the canonicalizer
        IntegerStamp dividendStamp = (IntegerStamp) div.getX().stamp(NodeView.DEFAULT);
        int bits = dividendStamp.getBits();
        UMagic magic = computeUMagic(divisor, bits);

        ConstantNode m = ConstantNode.forIntegerBits(bits, magic.m, graph);
        ValueNode quot = graph.unique(new UnsignedMulHighNode(m, div.getX()));

        if (magic.a) {
            assert magic.s > 0 : "Magic.s must be positive " + magic.s;
            /*
             * What we want here is (x + q) >>> s, but because of overflow, we need to use the
             * equivalent (((x - q) >>> 1) + q) >>> (s-1).
             *
             * That's correct because (x + q) / 2 == (x - q) / 2 + q.
             *
             * It's possible to use (x + q) >>> s directly on platforms that support a
             * "shift right with carry" instruction, using only 2 instead of 4 instructions. If a
             * platform only supports "rotate right with carry", we could still rotate right by 1,
             * and then shift the rest, getting 3 instructions.
             *
             * None of these additional optimizations are currently implemented, since we don't
             * support flags dependencies in the compiler graph.
             */
            ValueNode t = BinaryArithmeticNode.sub(graph, div.getX(), quot, NodeView.DEFAULT);
            t = graph.addOrUnique(UnsignedRightShiftNode.create(t, ConstantNode.forInt(1, graph), NodeView.DEFAULT));
            quot = BinaryArithmeticNode.add(graph, t, quot, NodeView.DEFAULT);
            if (magic.s > 1) {
                ConstantNode s = ConstantNode.forInt(magic.s - 1, graph);
                quot = graph.addOrUnique(UnsignedRightShiftNode.create(quot, s, NodeView.DEFAULT));
            }
        } else if (magic.s > 0) {
            ConstantNode s = ConstantNode.forInt(magic.s, graph);
            quot = graph.addOrUnique(UnsignedRightShiftNode.create(quot, s, NodeView.DEFAULT));
        }

        graph.replaceFixedWithFloating(div, graph.addOrUniqueWithInputs(PiNode.create(quot, div.stamp(NodeView.DEFAULT))));
        graph.getOptimizationLog().report(OptimizeDivPhase.class, "UnsignedDivOptimized", div);
    }

    // Unsigned division uses the magic-number construction from Hacker's Delight, chapter 10.
    private static final class UMagic {
        public long m;
        public boolean a;
        public int s;
    }

    private static UMagic computeUMagic(/* unsigned */ long dInput, int bits) {
        long bitsMask = CodeUtil.mask(bits);
        /*
         * Fix up d if it was sign-extended from a negative int value because we must interpret it
         * as unsigned.
         */
        long d = dInput & bitsMask;

        // Checkstyle: stop
        // Hacker's delight, figure 10-2
        /*
         * This version differs from the printed version (2nd edition, 2013) as it includes the fix
         * from the errata at:
         * https://ptgmedia.pearsoncmg.com/images/9780321842688/Errata/9780321842688errata031417.pdf
         *
         * An extra flag "gt" tracks if q1 exceeded the value of 0x800..000. According to the errata
         * this is needed for correct handling of the divisor 0x800..001. In our tests this doesn't
         * seem to make a difference, but it doesn't hurt either.
         *
         * The online version of Hacker's Delight at https://learning.oreilly.com contains an
         * incomplete version of the fix for this erratum.
         */
        int p, gt = 0;
        /* unsigned */ long nc, delta, q1, r1, q2, r2;
        /* unsigned */ long twoP;
        // Checkstyle: resume

        UMagic magu = new UMagic();
        magu.a = false;                                             // Initialize "add" indicator.
        nc = bitsMask - Long.remainderUnsigned((-d) & bitsMask, d); // Unsigned arithmetic here.
        p = bits - 1;                                               // Init. p.
        twoP = 1L << p;
        q1 = Long.divideUnsigned(twoP, nc);                         // Init. q1 = 2**p/nc.
        r1 = twoP - q1 * nc;                                        // Init. r1 = rem(2**p, nc)
        q2 = Long.divideUnsigned(twoP - 1, d);                      // Init. q2 = (2**p - 1)/d.
        r2 = (twoP - 1) - q2 * d;                                   // Init. r2 = rem(2**p - 1, d).
        do {
            p = p + 1;
            if (Long.compareUnsigned(q1, twoP) >= 0) {
                gt = 1; // Means q1 > delta.
            }
            if (Long.compareUnsigned(r1, nc - r1) >= 0) {
                q1 = (2 * q1 + 1) & bitsMask;       // Update q1.
                r1 = (2 * r1 - nc) & bitsMask;      // Update r1.
            } else {
                q1 = (2 * q1) & bitsMask;
                r1 = (2 * r1) & bitsMask;
            }
            if (Long.compareUnsigned(r2 + 1, d - r2) >= 0) {
                if (Long.compareUnsigned(q2, twoP - 1) >= 0) {
                    magu.a = true;
                }
                q2 = (2 * q2 + 1) & bitsMask;       // Update q2.
                r2 = (2 * r2 + 1 - d) & bitsMask;   // Update r2.
            } else {
                if (Long.compareUnsigned(q2, twoP) >= 0) {
                    magu.a = true;
                }
                q2 = (2 * q2) & bitsMask;
                r2 = (2 * r2 + 1) & bitsMask;
            }
            delta = d - 1 - r2;
        } while (gt == 0 && p < 2 * bits && (Long.compareUnsigned(q1, delta) < 0 || (q1 == delta && r1 == 0)));

        magu.m = q2 + 1;                            // Magic number
        magu.s = p - bits;                          // and shift amount to return
        return magu;                                // (magu.a was set above).
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
        /*
         * We know the abs here can never overflow because we check that in
         * #isDivByNonZeroConstantNonOverflowingAbs.
         */
        long ad = NumUtil.safeAbs(divisor, 64);
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
