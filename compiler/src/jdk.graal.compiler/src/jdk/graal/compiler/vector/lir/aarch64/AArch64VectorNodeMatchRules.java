/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.aarch64;

import java.util.function.Predicate;

import jdk.graal.compiler.core.aarch64.AArch64NodeMatchRules;
import jdk.graal.compiler.core.aarch64.AArch64PointerAddNode;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.match.ComplexMatchResult;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

public class AArch64VectorNodeMatchRules extends AArch64NodeMatchRules {
    public AArch64VectorNodeMatchRules(LIRGeneratorTool gen) {
        super(gen);
        assert gen.getArithmetic() instanceof VectorLIRGeneratorTool : gen.getArithmetic();
    }

    /**
     * Returns true if any of the arguments have a SimdStamp.
     */
    private static boolean hasSimdStamp(ValueNode... values) {
        for (ValueNode value : values) {
            if (value.stamp(NodeView.DEFAULT) instanceof SimdStamp) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSimdStamp(ValueNode value) {
        return value.stamp(NodeView.DEFAULT) instanceof SimdStamp;
    }

    private static String stampString(ValueNode... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            sb.append(values[i]).append("=").append(values[i].stamp(NodeView.DEFAULT));
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Checks whether either all values have a SimdStamp or none of them have a SimdStamp.
     */
    private static boolean allOrNoSimdStamps(ValueNode... values) {
        boolean hasSimd = values[0].stamp(NodeView.DEFAULT) instanceof SimdStamp;
        for (int i = 1; i < values.length; i++) {
            if (hasSimd != values[i].stamp(NodeView.DEFAULT) instanceof SimdStamp) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether all values have SimdStamp, and, if so, if the SimdStamp components satisfy the
     * provided condition.
     */
    private static boolean simdComponentStampCheck(Predicate<Stamp> condition, ValueNode... values) {
        for (ValueNode value : values) {
            Stamp stamp = value.stamp(NodeView.DEFAULT);
            if (stamp instanceof SimdStamp) {
                SimdStamp simd = (SimdStamp) stamp;
                /*
                 * Because all components of a SimdStamp must map to the same ops, for the checks
                 * were are performing right now it is suffice to only check one component.
                 */
                if (!condition.test(simd.getComponent(0))) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;

    }

    @Override
    protected boolean isNumericInteger(ValueNode... values) {
        /* Add logic for checking SimdStamps. */
        if (hasSimdStamp(values)) {
            return simdComponentStampCheck((stamp) -> stamp.getStackKind().isNumericInteger(), values);
        } else {
            return super.isNumericInteger(values);
        }
    }

    @Override
    protected boolean isNumericFloat(ValueNode... values) {
        /* Add logic for checking SimdStamps. */
        if (hasSimdStamp(values)) {
            return simdComponentStampCheck((stamp) -> stamp.getStackKind().isNumericFloat(), values);
        } else {
            return super.isNumericFloat(values);
        }
    }

    /* No NEON equivalent for {@code A +- (shifted B)}. */
    @Override
    public ComplexMatchResult mergeSignExtendByShiftIntoAddSub(BinaryNode op, LeftShiftNode lshift, ValueNode ext, ValueNode x, ValueNode y) {
        assert allOrNoSimdStamps(op, lshift, ext, x, y) : "All or no simd stamp failed " + stampString(op, lshift, ext, x, y);
        if (isSimdStamp(op)) {
            return null;
        } else {
            return super.mergeSignExtendByShiftIntoAddSub(op, lshift, ext, x, y);
        }
    }

    /* Don't think this is possible. */
    @Override
    public ComplexMatchResult mergeShiftDowncastIntoAddSub(BinaryNode op, LeftShiftNode lshift, ConstantNode constant, ValueNode x, ValueNode y) {
        assert allOrNoSimdStamps(op, lshift, x, y, constant) : "All or no simd stamp failed " + stampString(op, lshift, x, y, constant);
        if (isSimdStamp(op)) {
            return null;
        } else {
            return super.mergeShiftDowncastIntoAddSub(op, lshift, constant, x, y);
        }
    }

    // GR-31745 TODO add support
    /*
     * May be possible to use a s(add|sub)l, s(add|sub)w.
     */
    @Override
    public ComplexMatchResult mergePairShiftIntoAddSub(BinaryNode op, RightShiftNode signExt, ValueNode x, ValueNode y) {
        assert allOrNoSimdStamps(op, signExt, x, y) : "All or no simd stamp failed " + stampString(op, x, y);
        if (isSimdStamp(op)) {
            return null;
        } else {
            return super.mergePairShiftIntoAddSub(op, signExt, x, y);
        }
    }

    /* No NEON equivalent for {@code A +- (shifted B)}. */
    @Override
    public ComplexMatchResult mergeShiftedPairShiftIntoAddSub(BinaryNode op, LeftShiftNode outerShift, RightShiftNode signExt, ValueNode x, ValueNode y) {
        assert allOrNoSimdStamps(op, outerShift, signExt, x, y) : "All or no simd stamp failed " + stampString(op, signExt, x, y);
        if (isSimdStamp(op)) {
            return null;
        } else {
            return super.mergeShiftedPairShiftIntoAddSub(op, outerShift, signExt, x, y);
        }
    }

    // GR-32744 Re-evaluate once object vectorization support is enabled
    /* Shouldn't be vectorized until at least objects are supported. */
    @Override
    public ComplexMatchResult extendedPointerAddShift(AArch64PointerAddNode addP) {
        if (isSimdStamp(addP)) {
            throw GraalError.shouldNotReachHere("Simd stamp expected: " + addP); // ExcludeFromJacocoGeneratedReport
        }
        return super.extendedPointerAddShift(addP);
    }

    // GR-31745 TODO add support
    /*
     * Need to make my own logic.
     */
    @Override
    public ComplexMatchResult unsignedBitField(BinaryNode shift, ValueNode value, ConstantNode a) {
        assert allOrNoSimdStamps(shift, value, a) : "All or no simd stamp failed " + stampString(shift, value, a);
        if (isSimdStamp(shift)) {
            return null;
        } else {
            return super.unsignedBitField(shift, value, a);
        }
    }

    // GR-31745 TODO add support
    /*
     * Need to make my own logic.
     */
    @Override
    public ComplexMatchResult unsignedExtBitField(ZeroExtendNode extend, BinaryNode shift, ValueNode value, ConstantNode a) {
        assert allOrNoSimdStamps(extend, shift, value, a) : "All or no simd stamp failed " + stampString(extend, shift, value, a);
        if (isSimdStamp(extend)) {
            return null;
        } else {
            return super.unsignedExtBitField(extend, shift, value, a);
        }
    }

    // GR-31745 TODO add support
    /*
     * Need to make my own logic.
     */
    @Override
    public ComplexMatchResult signedBitField(LeftShiftNode shift, ValueNode value) {
        assert allOrNoSimdStamps(shift, value) : "All or no simd stamp failed " + stampString(shift, value);
        if (isSimdStamp(shift)) {
            return null;
        } else {
            return super.signedBitField(shift, value);
        }
    }

    // GR-31745 TODO add support
    /*
     * Looks like it should just be a shift in the end. Think I need my own logic.
     */
    @Override
    public ComplexMatchResult bitFieldMove(BinaryNode rshift, LeftShiftNode lshift, ValueNode value) {
        assert allOrNoSimdStamps(rshift, lshift, value) : "All or no simd stamp failed " + stampString(rshift, lshift, value);
        if (isSimdStamp(rshift)) {
            return null;
        } else {
            return super.bitFieldMove(rshift, lshift, value);
        }
    }

    /*
     * Don't think there is a NEON equivalent for ROR.
     */
    @Override
    public ComplexMatchResult rotationConstant(ValueNode op, ValueNode x, ValueNode y, ValueNode src) {
        assert allOrNoSimdStamps(op, x, y, src) : "All or no simd stamp failed " + stampString(op, x, y, src);
        if (isSimdStamp(op)) {
            return null;
        } else {
            return super.rotationConstant(op, x, y, src);
        }
    }

    /*
     * Don't think there is a NEON equivalent for ROR.
     */
    @Override
    public ComplexMatchResult rotationExpander(ValueNode src, ValueNode shiftAmount, ValueNode x, ValueNode y) {
        assert allOrNoSimdStamps(src, x) : "All or no simd stamp failed " + stampString(src, x);
        if (isSimdStamp(src)) {
            return null;
        } else {
            assert !isSimdStamp(shiftAmount) : shiftAmount + " " + shiftAmount.stamp(NodeView.DEFAULT);
            assert !isSimdStamp(y) : y + " " + y.stamp(NodeView.DEFAULT);
            return super.rotationExpander(src, shiftAmount, x, y);
        }
    }

    /** No NEON equivalent for {@code A +- (shifted B)}. */
    @Override
    public ComplexMatchResult addSubShift(BinaryNode binary, ValueNode a, BinaryNode shift) {
        assert allOrNoSimdStamps(binary, a, shift) : "All or no simd stamp failed " + stampString(binary, a, shift);
        if (isSimdStamp(binary)) {
            return null;
        } else {
            return super.addSubShift(binary, a, shift);
        }
    }

    /* No NEON equivalent for {@code A binaryOp (shifted B)}. */
    @Override
    public ComplexMatchResult logicShift(BinaryNode binary, ValueNode a, BinaryNode shift) {
        assert allOrNoSimdStamps(binary, a, shift) : "All or no simd stamp failed " + stampString(binary, a, shift);
        if (isSimdStamp(binary)) {
            return null;
        } else {
            return super.logicShift(binary, a, shift);
        }
    }

    /* No NEON equivalent for {@code NEG (shift a)}. */
    @Override
    public ComplexMatchResult negShift(BinaryNode shift, ValueNode a, ConstantNode b) {
        assert allOrNoSimdStamps(shift, a) : "All or no simd stamp failed " + stampString(shift, a);
        assert !isSimdStamp(b) : b;
        if (isSimdStamp(shift)) {
            return null;
        } else {
            return super.negShift(shift, a, b);
        }
    }

    /* Neon instructions exist: can use parent logic. */
    // public ComplexMatchResult bitwiseLogicNot(BinaryNode logic, NotNode not);

    /* No NEON instruction for EON. */
    @Override
    public ComplexMatchResult bitwiseNotXor(ValueNode value1, ValueNode value2) {
        assert allOrNoSimdStamps(value1, value2) : "All or no simd stamp failed " + stampString(value1, value2);
        if (isSimdStamp(value1)) {
            return null;
        } else {
            return super.bitwiseNotXor(value1, value2);
        }
    }

    // GR-31745 TODO add support
    /*
     * Need different logic than parent (need to know input length and pick between mls, smlsl, mla,
     * smlal), but should be able to do this.
     */
    @Override
    public ComplexMatchResult signedMultiplyAddSubLong(BinaryNode binary, MulNode mul, ValueNode a, ValueNode b, ValueNode c) {
        assert allOrNoSimdStamps(binary, mul, a, b, c) : "All or no simd stamp failed " + stampString(binary, mul, a, b, c);
        if (isSimdStamp(binary)) {
            return null;
        } else {
            return super.signedMultiplyAddSubLong(binary, mul, a, b, c);
        }
    }

    // GR-31745 TODO add support
    /*
     * Need different logic than parent (need to know input length and pick between mls, smlsl), but
     * should be able to do this.
     */
    @Override
    public ComplexMatchResult signedMultiplyNegLong(MulNode mul, SignExtendNode ext1, SignExtendNode ext2, ValueNode a, ValueNode b) {
        assert allOrNoSimdStamps(mul, ext1, ext2, a, b) : "All or no simd stamp failed " + stampString(mul, ext1, ext2, a, b);
        if (isSimdStamp(mul)) {
            return null;
        } else {
            return super.signedMultiplyNegLong(mul, ext1, ext2, a, b);
        }
    }

    // GR-31745 TODO add support
    /*
     * Need different logic than parent (need to know input length and pick between mul, smull), but
     * should be able to do this.
     */
    @Override
    public ComplexMatchResult signedMultiplyLong(MulNode mul, ValueNode a, ValueNode b) {
        assert allOrNoSimdStamps(mul, a, b) : "All or no simd stamp failed " + stampString(mul, a, b);
        if (isSimdStamp(mul)) {
            return null;
        } else {
            return super.signedMultiplyLong(mul, a, b);
        }
    }

    /* No NEON equivalent. */
    @Override
    public ComplexMatchResult mergeDowncastIntoAddSub(BinaryNode op, ValueNode x, ValueNode y, ConstantNode constant) {
        assert allOrNoSimdStamps(op, x, y, constant) : "All or no simd stamp failed " + stampString(op, x, y, constant);
        if (isSimdStamp(op)) {
            return null;
        } else {
            return super.mergeDowncastIntoAddSub(op, x, y, constant);
        }
    }

    // GR-31745 TODO add support
    /*
     * Should be able to use SADDL, SSUBL to implement some cases.
     */
    @Override
    public ComplexMatchResult mergeSignExtendIntoAddSub(BinaryNode op, UnaryNode ext, ValueNode x, ValueNode y) {
        assert allOrNoSimdStamps(op, ext, x, y) : "All or no simd stamp failed " + stampString(op, ext, x, y);
        if (isSimdStamp(op)) {
            return null;
        } else {
            return super.mergeSignExtendIntoAddSub(op, ext, x, y);
        }
    }

    /* Neon instructions exist: can use parent logic. However, probably is not too beneficial. */
    // public ComplexMatchResult multiplyNegate(ValueNode a, ValueNode b);

    /* Neon instructions exist: can use parent logic. */
    // public ComplexMatchResult multiplyAddSub(BinaryNode binary, ValueNode a, ValueNode b,
    // ValueNode c);

    /* This pattern is not vectorizable. */
    @Override
    public ComplexMatchResult testBitAndBranch(IfNode root, ValueNode value, ConstantNode a) {
        assert allOrNoSimdStamps(root, value, a) : "All or no simd stamp failed " + stampString(root, value, a);
        if (isSimdStamp(root)) {
            throw GraalError.shouldNotReachHereUnexpectedValue(root); // ExcludeFromJacocoGeneratedReport
        } else {
            return super.testBitAndBranch(root, value, a);
        }
    }

    /* This pattern is not vectorizable. */
    @Override
    public ComplexMatchResult checkNegativeAndBranch(IfNode root, IntegerLessThanNode lessNode, ValueNode x, ConstantNode y) {
        assert allOrNoSimdStamps(root, lessNode, x, y) : "All or no simd stamp failed " + stampString(root, lessNode, x, y);
        if (isSimdStamp(root)) {
            throw GraalError.shouldNotReachHereUnexpectedValue(root); // ExcludeFromJacocoGeneratedReport
        } else {
            return super.checkNegativeAndBranch(root, lessNode, x, y);
        }
    }

    /* Neon instructions exist: can use parent logic. */
    // public ComplexMatchResult floatSqrt(FloatConvertNode a, FloatConvertNode b, ValueNode c);

}
