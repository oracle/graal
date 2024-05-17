/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.loop;

import static org.graalvm.compiler.nodes.loop.MathUtil.add;
import static org.graalvm.compiler.nodes.loop.MathUtil.sub;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.phases.common.util.LoopUtility;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticNode;

public class DerivedOffsetInductionVariable extends DerivedInductionVariable {

    protected final ValueNode offset;
    protected final BinaryArithmeticNode<?> value;

    public DerivedOffsetInductionVariable(LoopEx loop, InductionVariable base, ValueNode offset, BinaryArithmeticNode<?> value) {
        super(loop, base);
        this.offset = offset;
        this.value = value;
    }

    public ValueNode getOffset() {
        return offset;
    }

    @Override
    public Direction direction() {
        Direction baseDirection = base.direction();
        if (baseDirection == null) {
            return null;
        }
        if (value instanceof SubNode && base.valueNode() == value.getY()) {
            return baseDirection.opposite();
        }
        return baseDirection;
    }

    @Override
    public ValueNode valueNode() {
        return value;
    }

    @Override
    public boolean isConstantInit() {
        try {
            if (offset.isConstant() && base.isConstantInit()) {
                constantInitSafe();
                return true;
            }
        } catch (ArithmeticException e) {
            // fall through to return false
        }
        return false;
    }

    @Override
    public boolean isConstantStride() {
        if (isMaskedNegateStride()) {
            if (base.isConstantStride()) {
                try {
                    LoopUtility.multiplyExact(IntegerStamp.getBits(offset.stamp(NodeView.DEFAULT)), base.constantStride(), -1);
                    return true;
                } catch (ArithmeticException e) {
                    return false;
                }
            }
        }
        return base.isConstantStride();
    }

    @Override
    public long constantInit() {
        return constantInitSafe();
    }

    private long constantInitSafe() throws ArithmeticException {
        return opSafe(base.constantInit(), offset.asJavaConstant().asLong());
    }

    @Override
    public long constantStride() {
        return constantStrideSafe();
    }

    private long constantStrideSafe() throws ArithmeticException {
        if (isMaskedNegateStride()) {
            return LoopUtility.multiplyExact(IntegerStamp.getBits(offset.stamp(NodeView.DEFAULT)), base.constantStride(), -1);
        }
        return base.constantStride();
    }

    /**
     * Determine if the current induction variable's stride is actually one that represents a
     * negation instead of a normal offset calculation. For example
     *
     * <pre>
     * int i = 0;
     * while (i < limit) {
     *     int reversIv = off - i;
     *     i++;
     * }
     * </pre>
     *
     * here {@code reverseIv} stride node is actually {@code i} negated since the IV is not
     * {@code i op off} but {@code off op i} where {@code op} is a subtraction.
     */
    private boolean isMaskedNegateStride() {
        return value instanceof SubNode && base.valueNode() == value.getY();
    }

    @Override
    public boolean isConstantExtremum() {
        try {
            if (offset.isConstant() && base.isConstantExtremum()) {
                constantExtremumSafe();
                return true;
            }
        } catch (ArithmeticException e) {
            // fall through to return false
        }
        return false;
    }

    @Override
    public long constantExtremum() {
        return constantExtremumSafe();
    }

    private long constantExtremumSafe() throws ArithmeticException {
        return opSafe(base.constantExtremum(), offset.asJavaConstant().asLong());
    }

    @Override
    public ValueNode initNode() {
        return op(base.initNode(), offset);
    }

    @Override
    public ValueNode strideNode() {
        if (value instanceof SubNode && base.valueNode() == value.getY()) {
            return graph().addOrUniqueWithInputs(NegateNode.create(base.strideNode(), NodeView.DEFAULT));
        }
        return base.strideNode();
    }

    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp) {
        return op(base.extremumNode(assumeLoopEntered, stamp), IntegerConvertNode.convert(offset, stamp, graph(), NodeView.DEFAULT));
    }

    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp, ValueNode maxTripCount) {
        return op(base.extremumNode(assumeLoopEntered, stamp, maxTripCount), IntegerConvertNode.convert(offset, stamp, graph(), NodeView.DEFAULT));
    }

    @Override
    public ValueNode exitValueNode() {
        return op(base.exitValueNode(), offset);
    }

    private long opSafe(long b, long o) throws ArithmeticException {
        // we can use offset bits in this method because all operands (init, scale, stride and
        // extremum) have by construction equal bit sizes
        if (value instanceof AddNode) {
            return LoopUtility.addExact(IntegerStamp.getBits(offset.stamp(NodeView.DEFAULT)), b, o);
        }
        if (value instanceof SubNode) {
            if (base.valueNode() == value.getX()) {
                return LoopUtility.subtractExact(IntegerStamp.getBits(offset.stamp(NodeView.DEFAULT)), b, o);
            } else {
                assert base.valueNode() == value.getY() || base instanceof BasicInductionVariable basic && basic.getOp() instanceof IntegerExactArithmeticNode : String.format("[base]=%s;[value]=%s", base.valueNode(), value.getY());
                return LoopUtility.subtractExact(IntegerStamp.getBits(offset.stamp(NodeView.DEFAULT)), b, o);
            }
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(value); // ExcludeFromJacocoGeneratedReport
    }

    public ValueNode op(ValueNode b, ValueNode o) {
        return op(b, o, true);
    }

    public ValueNode op(ValueNode b, ValueNode o, boolean gvn) {
        if (value instanceof AddNode) {
            return add(graph(), b, o, gvn);
        }
        if (value instanceof SubNode) {
            if (base.valueNode() == value.getX()) {
                return sub(graph(), b, o, gvn);
            } else {
                assert base.valueNode() == value.getY() || base instanceof BasicInductionVariable basic && basic.getOp() instanceof IntegerExactArithmeticNode;
                return sub(graph(), o, b, gvn);
            }
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(value); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public void deleteUnusedNodes() {
    }

    @Override
    public boolean isConstantScale(InductionVariable ref) {
        return super.isConstantScale(ref) || base.isConstantScale(ref);
    }

    @Override
    public long constantScale(InductionVariable ref) {
        assert isConstantScale(ref);
        if (this == ref) {
            return 1;
        }
        return base.constantScale(ref) * (value instanceof SubNode && base.valueNode() == value.getY() ? -1 : 1);
    }

    @Override
    public boolean offsetIsZero(InductionVariable ref) {
        if (this == ref) {
            return true;
        }
        return false;
    }

    @Override
    public ValueNode offsetNode(InductionVariable ref) {
        assert !offsetIsZero(ref);
        if (!base.offsetIsZero(ref)) {
            return null;
        }
        return offset;
    }

    @Override
    public String toString(IVToStringVerbosity verbosity) {
        if (verbosity == IVToStringVerbosity.FULL) {
            return String.format("DerivedOffsetInductionVariable base (%s) %s %s", base, value.getNodeClass().shortName(), offset);
        } else {
            return String.format("(%s) %s %s", base, value.getNodeClass().shortName(), offset);
        }
    }

    @Override
    public ValueNode copyValue(InductionVariable newBase) {
        return copyValue(newBase, true);
    }

    @Override
    public ValueNode copyValue(InductionVariable newBase, boolean gvn) {
        return op(newBase.valueNode(), offset, gvn);
    }

    @Override
    public InductionVariable copy(InductionVariable newBase, ValueNode newValue) {
        if (newValue instanceof BinaryArithmeticNode<?>) {
            return new DerivedOffsetInductionVariable(loop, newBase, offset, (BinaryArithmeticNode<?>) newValue);
        } else if (newValue instanceof NegateNode) {
            return new DerivedScaledInductionVariable(loop, newBase, (NegateNode) newValue);
        } else {
            assert newValue instanceof IntegerConvertNode<?> : "Expected integer convert operation. New baseIV=" + newBase + " newValue=" + newValue;
            return new DerivedConvertedInductionVariable(loop, newBase, newValue.stamp(NodeView.DEFAULT), newValue);
        }
    }

    @Override
    public ValueNode entryTripValue() {
        return op(getBase().entryTripValue(), offset);
    }
}
