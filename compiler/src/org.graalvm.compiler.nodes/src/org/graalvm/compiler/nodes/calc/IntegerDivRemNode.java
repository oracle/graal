/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_32;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.vm.ci.code.CodeUtil;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

@NodeInfo(cycles = CYCLES_32, size = SIZE_1)
public abstract class IntegerDivRemNode extends FixedBinaryNode implements Lowerable {

    public static final NodeClass<IntegerDivRemNode> TYPE = NodeClass.create(IntegerDivRemNode.class);

    public enum Op {
        DIV,
        REM
    }

    public enum Type {
        SIGNED,
        UNSIGNED
    }

    @OptionalInput(InputType.Guard) private GuardingNode zeroCheck;

    private final Op op;
    private final Type type;
    private final boolean canDeopt;

    protected IntegerDivRemNode(NodeClass<? extends IntegerDivRemNode> c, Stamp stamp, Op op, Type type, ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        super(c, stamp, x, y);
        this.zeroCheck = zeroCheck;
        this.op = op;
        this.type = type;

        // Assigning canDeopt during constructor, because it must never change during lifetime of
        // the node.
        IntegerStamp yStamp = (IntegerStamp) getY().stamp(NodeView.DEFAULT);
        this.canDeopt = (yStamp.contains(0) && zeroCheck == null) || yStamp.contains(-1);
    }

    public final GuardingNode getZeroCheck() {
        return zeroCheck;
    }

    public final Op getOp() {
        return op;
    }

    public final Type getType() {
        return type;
    }

    protected static ValueNode canonicalizeSignedDivConstant(ValueNode forX, long c, NodeView view) {
        assert c != 1 && c != -1;
        if (c == 0) {
            return null;
        }
        IntegerStamp dividendStamp = (IntegerStamp) forX.stamp(view);
        int bitSize = dividendStamp.getBits();
        Pair<Long, Integer> nums = magicDivideConstants(c, bitSize);
        long magicNum = nums.getLeft().longValue();
        int shiftNum = nums.getRight().intValue();
        ConstantNode m = ConstantNode.forIntegerBits(bitSize, magicNum);
        ValueNode quot = new IntegerMulHighNode(m, forX);
        if (c > 0 && magicNum < 0) {
            quot = BinaryArithmeticNode.add(quot, forX, NodeView.DEFAULT);
        } else if (c < 0 && magicNum > 0) {
            quot = BinaryArithmeticNode.sub(quot, forX, NodeView.DEFAULT);
        }
        if (shiftNum > 0) {
            ConstantNode s = ConstantNode.forInt(shiftNum);
            quot = new RightShiftNode(quot, s);
        }
        if (c < 0) {
            ConstantNode s = ConstantNode.forInt(bitSize - 1);
            ValueNode sign = UnsignedRightShiftNode.create(quot, s, NodeView.DEFAULT);
            quot = BinaryArithmeticNode.add(quot, sign, NodeView.DEFAULT);
        } else if (dividendStamp.canBeNegative()) {
            ConstantNode s = ConstantNode.forInt(bitSize - 1);
            ValueNode sign = UnsignedRightShiftNode.create(forX, s, NodeView.DEFAULT);
            quot = BinaryArithmeticNode.add(quot, sign, NodeView.DEFAULT);
        }
        return quot;
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

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public boolean canDeoptimize() {
        return canDeopt;
    }
}
