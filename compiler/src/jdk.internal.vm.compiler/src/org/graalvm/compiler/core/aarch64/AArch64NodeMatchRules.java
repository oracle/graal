/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.aarch64;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ExtendType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.core.match.ComplexMatchResult;
import org.graalvm.compiler.core.match.MatchRule;
import org.graalvm.compiler.core.match.MatchableNode;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.graalvm.compiler.lir.aarch64.AArch64BitFieldOp;
import org.graalvm.compiler.lir.aarch64.AArch64BitFieldOp.BitFieldOpCode;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NotNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.ShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@MatchableNode(nodeClass = AArch64PointerAddNode.class, inputs = {"base", "offset"})
public class AArch64NodeMatchRules extends NodeMatchRules {
    private static final EconomicMap<Class<? extends BinaryNode>, AArch64ArithmeticOp> binaryOpMap;
    private static final EconomicMap<Class<? extends BinaryNode>, AArch64BitFieldOp.BitFieldOpCode> bitFieldOpMap;
    private static final EconomicMap<Class<? extends BinaryNode>, AArch64MacroAssembler.ShiftType> shiftTypeMap;
    private static final EconomicMap<Class<? extends BinaryNode>, AArch64ArithmeticOp> logicalNotOpMap;

    static {
        binaryOpMap = EconomicMap.create(Equivalence.IDENTITY, 9);
        binaryOpMap.put(AddNode.class, AArch64ArithmeticOp.ADD);
        binaryOpMap.put(SubNode.class, AArch64ArithmeticOp.SUB);
        binaryOpMap.put(MulNode.class, AArch64ArithmeticOp.MUL);
        binaryOpMap.put(AndNode.class, AArch64ArithmeticOp.AND);
        binaryOpMap.put(OrNode.class, AArch64ArithmeticOp.OR);
        binaryOpMap.put(XorNode.class, AArch64ArithmeticOp.XOR);
        binaryOpMap.put(LeftShiftNode.class, AArch64ArithmeticOp.LSL);
        binaryOpMap.put(RightShiftNode.class, AArch64ArithmeticOp.ASR);
        binaryOpMap.put(UnsignedRightShiftNode.class, AArch64ArithmeticOp.LSR);

        bitFieldOpMap = EconomicMap.create(Equivalence.IDENTITY, 2);
        bitFieldOpMap.put(UnsignedRightShiftNode.class, BitFieldOpCode.UBFX);
        bitFieldOpMap.put(LeftShiftNode.class, BitFieldOpCode.UBFIZ);

        logicalNotOpMap = EconomicMap.create(Equivalence.IDENTITY, 3);
        logicalNotOpMap.put(AndNode.class, AArch64ArithmeticOp.BIC);
        logicalNotOpMap.put(OrNode.class, AArch64ArithmeticOp.ORN);
        logicalNotOpMap.put(XorNode.class, AArch64ArithmeticOp.EON);

        shiftTypeMap = EconomicMap.create(Equivalence.IDENTITY, 3);
        shiftTypeMap.put(LeftShiftNode.class, AArch64MacroAssembler.ShiftType.LSL);
        shiftTypeMap.put(RightShiftNode.class, AArch64MacroAssembler.ShiftType.ASR);
        shiftTypeMap.put(UnsignedRightShiftNode.class, AArch64MacroAssembler.ShiftType.LSR);
    }

    public AArch64NodeMatchRules(LIRGeneratorTool gen) {
        super(gen);
    }

    /**
     * Checks whether all arguments are numeric integers.
     */
    protected boolean isNumericInteger(ValueNode... values) {
        for (ValueNode value : values) {
            if (!value.getStackKind().isNumericInteger()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether all arguments are numeric floats.
     */
    protected boolean isNumericFloat(ValueNode... values) {
        for (ValueNode value : values) {
            if (!value.getStackKind().isNumericFloat()) {
                return false;
            }
        }
        return true;
    }

    protected LIRFrameState getState(MemoryAccess access) {
        if (access instanceof DeoptimizingNode) {
            return state((DeoptimizingNode) access);
        }
        return null;
    }

    protected AArch64Kind getMemoryKind(MemoryAccess access) {
        return (AArch64Kind) gen.getLIRKind(((ValueNode) access).stamp(NodeView.DEFAULT)).getPlatformKind();
    }

    private static boolean isSupportedExtendedAddSubShift(IntegerConvertNode<?> node, int clampedShiftAmt) {
        assert clampedShiftAmt >= 0;
        if (clampedShiftAmt <= 4) {
            switch (node.getInputBits()) {
                case Byte.SIZE:
                case Short.SIZE:
                case Integer.SIZE:
                case Long.SIZE:
                    return true;
            }
        }
        return false;
    }

    private static ExtendType getZeroExtendType(int fromBits) {
        switch (fromBits) {
            case Byte.SIZE:
                return ExtendType.UXTB;
            case Short.SIZE:
                return ExtendType.UXTH;
            case Integer.SIZE:
                return ExtendType.UXTW;
            case Long.SIZE:
                return ExtendType.UXTX;
            default:
                GraalError.shouldNotReachHere("extended from " + fromBits + "bits is not supported!"); // ExcludeFromJacocoGeneratedReport
                return null;
        }
    }

    private static ExtendType getSignExtendType(int fromBits) {
        switch (fromBits) {
            case Byte.SIZE:
                return ExtendType.SXTB;
            case Short.SIZE:
                return ExtendType.SXTH;
            case Integer.SIZE:
                return ExtendType.SXTW;
            case Long.SIZE:
                return ExtendType.SXTX;
            default:
                GraalError.shouldNotReachHere("extended from " + fromBits + "bits is not supported!"); // ExcludeFromJacocoGeneratedReport
                return null;
        }
    }

    private AllocatableValue moveSp(AllocatableValue value) {
        return getLIRGeneratorTool().moveSp(value);
    }

    /**
     * Clamp shift amounts into range 0 <= shiftamt < size according to JLS.
     */
    private static int getClampedShiftAmt(ShiftNode<?> op) {
        int clampMask = op.getShiftAmountMask();
        assert clampMask == 63 || clampMask == 31;
        return op.getY().asJavaConstant().asInt() & clampMask;
    }

    protected ComplexMatchResult emitBinaryShift(AArch64ArithmeticOp op, ValueNode value, ShiftNode<?> shift) {
        AArch64MacroAssembler.ShiftType shiftType = shiftTypeMap.get(shift.getClass());
        assert shiftType != null;
        assert isNumericInteger(value, shift.getX());

        return builder -> {
            Value a = operand(value);
            Value b = operand(shift.getX());
            Variable result = gen.newVariable(LIRKind.combine(a, b));
            AllocatableValue x = moveSp(gen.asAllocatable(a));
            AllocatableValue y = moveSp(gen.asAllocatable(b));
            int shiftAmount = getClampedShiftAmt(shift);
            gen.append(new AArch64ArithmeticOp.BinaryShiftOp(op, result, x, y, shiftType, shiftAmount));
            return result;
        };
    }

    private ComplexMatchResult emitBitTestAndBranch(FixedNode trueSuccessor, FixedNode falseSuccessor,
                    ValueNode value, double trueProbability, int nbits) {
        return builder -> {
            LabelRef trueDestination = getLIRBlock(trueSuccessor);
            LabelRef falseDestination = getLIRBlock(falseSuccessor);
            AllocatableValue src = moveSp(gen.asAllocatable(operand(value)));
            gen.append(new AArch64ControlFlow.BitTestAndBranchOp(trueDestination, falseDestination, src,
                            trueProbability, nbits));
            return null;
        };
    }

    /**
     * Helper used by emitBitFieldInsert and emitBitFieldExtract.
     */
    private ComplexMatchResult emitBitFieldHelper(JavaKind kind, AArch64BitFieldOp.BitFieldOpCode op, ValueNode value, int lsb, int width) {
        assert isNumericInteger(value);
        assert lsb + width <= kind.getBitCount();

        return builder -> {
            Value a = operand(value);
            LIRKind resultKind = LIRKind.fromJavaKind(gen.target().arch, kind);
            Variable result = gen.newVariable(resultKind);
            AllocatableValue src = moveSp(gen.asAllocatable(a));
            gen.append(new AArch64BitFieldOp(op, result, src, lsb, width));
            return result;
        };
    }

    /**
     * Copy (width) bits from the least significant bits of the source register to bit position lsb
     * of the destination register.
     *
     * @param kind expected final size of the bitfield operation
     * @param op The type of bitfield operation
     * @param value The value to extract bits from
     * @param lsb (least significant bit) the starting index of where the value is moved to
     * @param width The number of bits to extract from value.
     */
    private ComplexMatchResult emitBitFieldInsert(JavaKind kind, AArch64BitFieldOp.BitFieldOpCode op, ValueNode value, int lsb, int width) {
        assert op == BitFieldOpCode.SBFIZ || op == BitFieldOpCode.UBFIZ;
        return emitBitFieldHelper(kind, op, value, lsb, width);
    }

    /**
     * Copy (width) bits from the lsb bit position of the source register to the bottom of the
     * destination register.
     *
     * @param kind expected final size of the bitfield operation
     * @param op The type of bitfield operation
     * @param value The value to extract bits from
     * @param lsb (least significant bit) the starting index of where the value copied from
     * @param width The number of bits to extract from value.
     */
    private ComplexMatchResult emitBitFieldExtract(JavaKind kind, AArch64BitFieldOp.BitFieldOpCode op, ValueNode value, int lsb, int width) {
        assert op == BitFieldOpCode.SBFX || op == BitFieldOpCode.UBFX;
        return emitBitFieldHelper(kind, op, value, lsb, width);
    }

    private ComplexMatchResult emitExtendedAddSubShift(BinaryNode op, ValueNode x, ValueNode y, ExtendType extType, int shiftAmt) {
        assert op instanceof AddNode || op instanceof SubNode;
        return builder -> {
            AllocatableValue src1 = gen.asAllocatable(operand(x));
            AllocatableValue src2 = moveSp(gen.asAllocatable(operand(y)));
            Variable result = gen.newVariable(LIRKind.combine(operand(x), operand(y)));
            AArch64ArithmeticOp arithmeticOp = op instanceof AddNode ? AArch64ArithmeticOp.ADD : AArch64ArithmeticOp.SUB;
            gen.append(new AArch64ArithmeticOp.ExtendedAddSubShiftOp(arithmeticOp, result, src1, src2, extType, shiftAmt));
            return result;
        };
    }

    /**
     * Goal: Use AArch64's add/sub (extended register) instructions to fold away extend and shift of
     * left operand.
     */
    @MatchRule("(Add=op x (LeftShift=lshift (SignExtend=ext y) Constant))")
    @MatchRule("(Sub=op x (LeftShift=lshift (SignExtend=ext y) Constant))")
    @MatchRule("(Add=op x (LeftShift=lshift (ZeroExtend=ext y) Constant))")
    @MatchRule("(Sub=op x (LeftShift=lshift (ZeroExtend=ext y) Constant))")
    public ComplexMatchResult mergeSignExtendByShiftIntoAddSub(BinaryNode op, LeftShiftNode lshift, ValueNode ext, ValueNode x, ValueNode y) {
        assert isNumericInteger(lshift);
        int shiftAmt = getClampedShiftAmt(lshift);
        if (!isSupportedExtendedAddSubShift((IntegerConvertNode<?>) ext, shiftAmt)) {
            return null;
        }
        ExtendType extType;
        if (ext instanceof SignExtendNode) {
            extType = getSignExtendType(((SignExtendNode) ext).getInputBits());
        } else {
            extType = getZeroExtendType(((ZeroExtendNode) ext).getInputBits());
        }
        return emitExtendedAddSubShift(op, x, y, extType, shiftAmt);
    }

    /**
     * Goal: Use AArch64's add/sub (extended register) instructions to fold away the and operation
     * (into a zero extend) and shift of left operand.
     */
    @MatchRule("(Add=op x (LeftShift=lshift (And y Constant=constant) Constant))")
    @MatchRule("(Sub=op x (LeftShift=lshift (And y Constant=constant) Constant))")
    public ComplexMatchResult mergeShiftDowncastIntoAddSub(BinaryNode op, LeftShiftNode lshift, ConstantNode constant, ValueNode x, ValueNode y) {
        assert isNumericInteger(lshift, constant);
        int shiftAmt = getClampedShiftAmt(lshift);
        if (shiftAmt > 4) {
            return null;
        }
        long mask = constant.asJavaConstant().asLong() & CodeUtil.mask(op.getStackKind().getBitCount());
        if (mask != 0xFFL && mask != 0xFFFFL && mask != 0xFFFFFFFFL) {
            return null;
        }
        int numBits = 64 - Long.numberOfLeadingZeros(mask);
        return emitExtendedAddSubShift(op, x, y, getZeroExtendType(numBits), shiftAmt);
    }

    /**
     * Goal: Switch ((x << amt) >> amt) into a sign extend and fold into AArch64 add/sub (extended
     * register) instruction.
     */
    @MatchRule("(Add=op x (RightShift=signExt (LeftShift y Constant=shiftConst) Constant=shiftConst))")
    @MatchRule("(Sub=op x (RightShift=signExt (LeftShift y Constant=shiftConst) Constant=shiftConst))")
    public ComplexMatchResult mergePairShiftIntoAddSub(BinaryNode op, RightShiftNode signExt, ValueNode x, ValueNode y) {
        int signExtendAmt = getClampedShiftAmt(signExt);
        int opSize = op.getStackKind().getBitCount();
        assert opSize == 32 || opSize == 64;

        int remainingBits = opSize - signExtendAmt;
        if (remainingBits != 8 && remainingBits != 16 && remainingBits != 32) {
            return null;
        }
        return emitExtendedAddSubShift(op, x, y, getSignExtendType(remainingBits), 0);
    }

    /**
     * Goal: Fold ((x << amt) >> amt) << [0,4] into AArch64 add/sub (extended register) instruction
     * with a sign extend and a shift.
     */
    @MatchRule("(Add=op x (LeftShift=outerShift (RightShift=signExt (LeftShift y Constant=shiftConst) Constant=shiftConst) Constant))")
    @MatchRule("(Sub=op x (LeftShift=outerShift (RightShift=signExt (LeftShift y Constant=shiftConst) Constant=shiftConst) Constant))")
    public ComplexMatchResult mergeShiftedPairShiftIntoAddSub(BinaryNode op, LeftShiftNode outerShift, RightShiftNode signExt, ValueNode x, ValueNode y) {
        int shiftAmt = getClampedShiftAmt(outerShift);
        if (shiftAmt > 4) {
            return null;
        }
        int signExtendAmt = getClampedShiftAmt(signExt);
        int opSize = op.getStackKind().getBitCount();
        assert opSize == 32 || opSize == 64;

        int remainingBits = opSize - signExtendAmt;
        if (remainingBits != 8 && remainingBits != 16 && remainingBits != 32) {
            return null;
        }
        return emitExtendedAddSubShift(op, x, y, getSignExtendType(remainingBits), shiftAmt);
    }

    /**
     * Goal: Fold zero extend and (optional) shift into AArch64 add/sub (extended register)
     * instruction.
     */
    @MatchRule("(AArch64PointerAdd=addP base ZeroExtend)")
    @MatchRule("(AArch64PointerAdd=addP base (LeftShift ZeroExtend Constant))")
    public ComplexMatchResult extendedPointerAddShift(AArch64PointerAddNode addP) {
        ValueNode offset = addP.getOffset();
        ZeroExtendNode zeroExtend;
        int shiftAmt;
        if (offset instanceof ZeroExtendNode) {
            zeroExtend = (ZeroExtendNode) offset;
            shiftAmt = 0;
        } else {
            LeftShiftNode shift = (LeftShiftNode) offset;
            zeroExtend = (ZeroExtendNode) shift.getX();
            shiftAmt = getClampedShiftAmt(shift);
        }
        if (!isSupportedExtendedAddSubShift(zeroExtend, shiftAmt)) {
            return null;
        }

        int fromBits = zeroExtend.getInputBits();
        int toBits = zeroExtend.getResultBits();
        if (toBits != 64) {
            return null;
        }
        assert fromBits <= toBits;
        ExtendType extendType = getZeroExtendType(fromBits);

        ValueNode base = addP.getBase();
        return builder -> {
            AllocatableValue x = gen.asAllocatable(operand(base));
            AllocatableValue y = moveSp(gen.asAllocatable(operand(zeroExtend.getValue())));
            AllocatableValue baseReference = LIRKind.derivedBaseFromValue(x);
            LIRKind kind = LIRKind.combineDerived(gen.getLIRKind(addP.stamp(NodeView.DEFAULT)),
                            baseReference, null);
            Variable result = gen.newVariable(kind);
            gen.append(new AArch64ArithmeticOp.ExtendedAddSubShiftOp(AArch64ArithmeticOp.ADD, result, x, y,
                            extendType, shiftAmt));
            return result;
        };
    }

    /**
     * This method determines, if possible, how to use AArch64's bit unsigned field insert/extract
     * (UBFIZ/UBFX) instructions to copy over desired bits in the presence of the pattern [(X >>> #)
     * & MASK] or [(X & MASK) << #] (with some zero extensions maybe sprinkled in).
     *
     * The main idea is that the mask and shift will force many bits to be zeroed and this
     * information can be leveraged to extract the meaningful bits.
     */
    private ComplexMatchResult unsignedBitFieldHelper(JavaKind finalOpKind, BinaryNode shift, ValueNode value, ConstantNode maskNode, int andOpSize) {
        long mask = maskNode.asJavaConstant().asLong() & CodeUtil.mask(andOpSize);
        if (!CodeUtil.isPowerOf2(mask + 1)) {
            /* MaskNode isn't a mask: initial assumption doesn't hold true */
            return null;
        }
        int maskSize = 64 - Long.numberOfLeadingZeros(mask);

        int shiftSize = shift.getStackKind().getBitCount();
        int shiftAmt = getClampedShiftAmt((ShiftNode<?>) shift);
        /*
         * The width of the insert/extract will be the bits which are not shifted out and/or not
         * part of the mask.
         */
        int width = Math.min(maskSize, shiftSize - shiftAmt);
        if (width == finalOpKind.getBitCount()) {
            assert maskSize == finalOpKind.getBitCount() && shiftAmt == 0;
            // original value is unaffected
            return builder -> operand(value);
        } else if (shift instanceof UnsignedRightShiftNode) {
            // this is an extract
            return emitBitFieldExtract(finalOpKind, BitFieldOpCode.UBFX, value, shiftAmt, width);
        } else {
            // is a left shift - means this is an insert
            return emitBitFieldInsert(finalOpKind, BitFieldOpCode.UBFIZ, value, shiftAmt, width);
        }
    }

    /**
     * Goal: Use AArch64's bit unsigned field insert/extract (UBFIZ/UBFX) instructions to copy over
     * desired bits.
     */
    @MatchRule("(And (UnsignedRightShift=shift value Constant) Constant=a)")
    @MatchRule("(LeftShift=shift (And value Constant=a) Constant)")
    public ComplexMatchResult unsignedBitField(BinaryNode shift, ValueNode value, ConstantNode a) {
        return unsignedBitFieldHelper(shift.getStackKind(), shift, value, a, shift.getStackKind().getBitCount());
    }

    /**
     * Goal: Use AArch64's bit unsigned field insert/extract (UBFIZ/UBFX) instructions to copy over
     * desired bits.
     */
    @MatchRule("(LeftShift=shift (ZeroExtend=extend (And value Constant=a)) Constant)")
    @MatchRule("(ZeroExtend=extend (And (UnsignedRightShift=shift value Constant) Constant=a))")
    @MatchRule("(ZeroExtend=extend (LeftShift=shift (And value Constant=a) Constant))")
    public ComplexMatchResult unsignedExtBitField(ZeroExtendNode extend, BinaryNode shift, ValueNode value, ConstantNode a) {
        return unsignedBitFieldHelper(extend.getStackKind(), shift, value, a, extend.getInputBits());
    }

    /**
     * Goal: Use AArch64's signed bitfield insert in zeros (sbfiz) instruction to extract desired
     * bits while folding away sign extend.
     */
    @MatchRule("(LeftShift=shift (SignExtend value) Constant)")
    public ComplexMatchResult signedBitField(LeftShiftNode shift, ValueNode value) {
        assert isNumericInteger(shift);

        SignExtendNode extend = (SignExtendNode) shift.getX();
        int inputBits = extend.getInputBits();
        int resultBits = extend.getResultBits();
        JavaKind kind = shift.getStackKind();
        assert kind.getBitCount() == resultBits;

        int lsb = getClampedShiftAmt(shift);
        /*
         * Get the min value of the inputBits and post-shift bits (resultBits - lsb) as the bitfield
         * width. Note if (resultBits-lsb) is smaller than inputBits, the in reality no sign
         * extension takes place.
         */
        int width = Math.min(inputBits, resultBits - lsb);
        assert width >= 1 && width <= resultBits - lsb;

        return emitBitFieldInsert(kind, BitFieldOpCode.SBFIZ, value, lsb, width);
    }

    /**
     * Goal: Use AArch64's bit field insert/extract instructions to copy over desired bits.
     */
    @MatchRule("(RightShift=rshift (LeftShift=lshift value Constant) Constant)")
    @MatchRule("(UnsignedRightShift=rshift (LeftShift=lshift value Constant) Constant)")
    public ComplexMatchResult bitFieldMove(BinaryNode rshift, LeftShiftNode lshift, ValueNode value) {
        assert isNumericInteger(rshift);
        JavaKind opKind = rshift.getStackKind();
        int opSize = opKind.getBitCount();

        int lshiftAmt = getClampedShiftAmt(lshift);
        int rshiftAmt = getClampedShiftAmt((ShiftNode<?>) rshift);

        /*
         * Get the width of the bitField. It will be in the range 1 to 32(64).
         *
         * Get the final width of the extract. Because the left and right shift go in opposite
         * directions, the number of meaningful bits after performing a (x << #) >>(>) # is
         * dependent on the maximum of the two shifts.
         */
        int width = opSize - Math.max(lshiftAmt, rshiftAmt);
        assert width > 1 || width <= opSize;

        if (width == opSize) {
            // this means that no shifting was performed: can directly return value
            return builder -> operand(value);
        }

        /*
         * Use bitfield insert (SBFIZ/UBFIZ) if left shift number is larger than right shift number,
         * otherwise use bitfield extract (SBFX/UBFX).
         *
         * If lshiftAmt > rshiftAmt, this means that the destination value will have some of its
         * bottom bits cleared, whereas if lshiftAmt <= rshiftAmt, then the destination value will
         * have bits starting from index 0 copied from the input.
         */
        if (lshiftAmt > rshiftAmt) {
            int lsb = lshiftAmt - rshiftAmt;
            BitFieldOpCode op = rshift instanceof RightShiftNode ? BitFieldOpCode.SBFIZ : BitFieldOpCode.UBFIZ;
            return emitBitFieldInsert(opKind, op, value, lsb, width);
        } else {
            // is a bit field extract
            int lsb = rshiftAmt - lshiftAmt;
            BitFieldOpCode op = rshift instanceof RightShiftNode ? BitFieldOpCode.SBFX : BitFieldOpCode.UBFX;
            return emitBitFieldExtract(opKind, op, value, lsb, width);
        }
    }

    /**
     * Goal: Use AArch64's ror instruction for rotations.
     */
    @MatchRule("(Or=op (LeftShift=x src Constant) (UnsignedRightShift=y src Constant))")
    @MatchRule("(Or=op (UnsignedRightShift=x src Constant) (LeftShift=y src Constant))")
    @MatchRule("(Add=op (LeftShift=x src Constant) (UnsignedRightShift=y src Constant))")
    @MatchRule("(Add=op (UnsignedRightShift=x src Constant) (LeftShift=y src Constant))")
    public ComplexMatchResult rotationConstant(ValueNode op, ValueNode x, ValueNode y, ValueNode src) {
        assert isNumericInteger(src);
        assert src.getStackKind() == op.getStackKind() && op.getStackKind() == x.getStackKind();

        int shiftAmt1 = getClampedShiftAmt((ShiftNode<?>) x);
        int shiftAmt2 = getClampedShiftAmt((ShiftNode<?>) y);
        if (shiftAmt1 + shiftAmt2 == 0 && op instanceof OrNode) {
            assert shiftAmt1 == 0 && shiftAmt2 == 0;
            /* No shift performed: for or operation, this is equivalent to original value */
            return builder -> operand(src);
        } else if (src.getStackKind().getBitCount() == shiftAmt1 + shiftAmt2) {
            /* Shifts are equivalent to a rotate. */
            return builder -> {
                AllocatableValue a = moveSp(gen.asAllocatable(operand(src)));
                /* Extract the right shift amount */
                JavaConstant b = JavaConstant.forInt(x instanceof LeftShiftNode ? shiftAmt2 : shiftAmt1);
                Variable result = gen.newVariable(LIRKind.combine(a));
                getArithmeticLIRGenerator().emitBinaryConst(result, AArch64ArithmeticOp.ROR, a, b);
                return result;
            };
        }

        return null;
    }

    /**
     * Goal: Use AArch64's ror instruction for rotations.
     */
    @MatchRule("(Or (LeftShift=x src shiftAmount) (UnsignedRightShift src (Sub=y Constant shiftAmount)))")
    @MatchRule("(Or (UnsignedRightShift=x src shiftAmount) (LeftShift src (Sub=y Constant shiftAmount)))")
    @MatchRule("(Or (LeftShift=x src (Negate shiftAmount)) (UnsignedRightShift src (Add=y Constant shiftAmount)))")
    @MatchRule("(Or (UnsignedRightShift=x src (Negate shiftAmount)) (LeftShift src (Add=y Constant shiftAmount)))")
    @MatchRule("(Or (LeftShift=x src shiftAmount) (UnsignedRightShift src (Negate=y shiftAmount)))")
    @MatchRule("(Or (UnsignedRightShift=x src shiftAmount) (LeftShift src (Negate=y shiftAmount)))")
    public ComplexMatchResult rotationExpander(ValueNode src, ValueNode shiftAmount, ValueNode x, ValueNode y) {
        assert isNumericInteger(src);
        assert shiftAmount.getStackKind().getBitCount() == 32;

        if (y instanceof SubNode || y instanceof AddNode) {
            BinaryNode binary = (BinaryNode) y;
            ConstantNode delta = (ConstantNode) (binary.getX() != shiftAmount ? binary.getX() : binary.getY());
            /*
             * For this pattern to match a right rotate, then the "clamped" delta (i.e. the value of
             * delta within a shift) must be 0.
             */
            int opSize = src.getStackKind().getBitCount();
            assert opSize == 32 || opSize == 64;
            long clampedDelta = delta.asJavaConstant().asInt() & (opSize - 1);
            if (clampedDelta != 0) {
                return null;
            }
        }

        return builder -> {
            Value a = operand(src);
            Value b;
            if (y instanceof AddNode) {
                b = x instanceof LeftShiftNode ? operand(shiftAmount) : getArithmeticLIRGenerator().emitNegate(operand(shiftAmount), false);
            } else {
                b = x instanceof LeftShiftNode ? getArithmeticLIRGenerator().emitNegate(operand(shiftAmount), false) : operand(shiftAmount);
            }
            return getArithmeticLIRGenerator().emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.ROR, false, a, b);
        };
    }

    /**
     * Goal: Use AArch64 binary shift add/sub ops to fold shift.
     */
    @MatchRule("(Add=binary a (LeftShift=shift b Constant))")
    @MatchRule("(Add=binary a (RightShift=shift b Constant))")
    @MatchRule("(Add=binary a (UnsignedRightShift=shift b Constant))")
    @MatchRule("(Sub=binary a (LeftShift=shift b Constant))")
    @MatchRule("(Sub=binary a (RightShift=shift b Constant))")
    @MatchRule("(Sub=binary a (UnsignedRightShift=shift b Constant))")
    public ComplexMatchResult addSubShift(BinaryNode binary, ValueNode a, BinaryNode shift) {
        AArch64ArithmeticOp op = binaryOpMap.get(binary.getClass());
        assert op != null;
        return emitBinaryShift(op, a, (ShiftNode<?>) shift);
    }

    /**
     * Goal: Use AArch64 binary shift logic ops to fold shift.
     */
    @MatchRule("(And=binary a (LeftShift=shift b Constant))")
    @MatchRule("(And=binary a (RightShift=shift b Constant))")
    @MatchRule("(And=binary a (UnsignedRightShift=shift b Constant))")
    @MatchRule("(Or=binary a (LeftShift=shift b Constant))")
    @MatchRule("(Or=binary a (RightShift=shift b Constant))")
    @MatchRule("(Or=binary a (UnsignedRightShift=shift b Constant))")
    @MatchRule("(Xor=binary a (LeftShift=shift b Constant))")
    @MatchRule("(Xor=binary a (RightShift=shift b Constant))")
    @MatchRule("(Xor=binary a (UnsignedRightShift=shift b Constant))")
    @MatchRule("(And=binary a (Not (LeftShift=shift b Constant)))")
    @MatchRule("(And=binary a (Not (RightShift=shift b Constant)))")
    @MatchRule("(And=binary a (Not (UnsignedRightShift=shift b Constant)))")
    @MatchRule("(Or=binary a (Not (LeftShift=shift b Constant)))")
    @MatchRule("(Or=binary a (Not (RightShift=shift b Constant)))")
    @MatchRule("(Or=binary a (Not (UnsignedRightShift=shift b Constant)))")
    @MatchRule("(Xor=binary a (Not (LeftShift=shift b Constant)))")
    @MatchRule("(Xor=binary a (Not (RightShift=shift b Constant)))")
    @MatchRule("(Xor=binary a (Not (UnsignedRightShift=shift b Constant)))")
    public ComplexMatchResult logicShift(BinaryNode binary, ValueNode a, BinaryNode shift) {
        AArch64ArithmeticOp op;
        ValueNode operand = binary.getX() == a ? binary.getY() : binary.getX();
        if (operand instanceof NotNode) {
            op = logicalNotOpMap.get(binary.getClass());
        } else {
            op = binaryOpMap.get(binary.getClass());
        }
        assert op != null;
        return emitBinaryShift(op, a, (ShiftNode<?>) shift);
    }

    /**
     * Goal: fold shift into negate operation using AArch64's sub (shifted register) instruction.
     */
    @MatchRule("(Negate (UnsignedRightShift=shift a Constant=b))")
    @MatchRule("(Negate (RightShift=shift a Constant=b))")
    @MatchRule("(Negate (LeftShift=shift a Constant=b))")
    public ComplexMatchResult negShift(BinaryNode shift, ValueNode a, ConstantNode b) {
        assert isNumericInteger(a, b);
        int shiftAmt = getClampedShiftAmt((ShiftNode<?>) shift);
        AArch64Assembler.ShiftType shiftType = shiftTypeMap.get(shift.getClass());
        return builder -> {
            AllocatableValue src = moveSp(gen.asAllocatable(operand(a)));
            LIRKind kind = LIRKind.combine(operand(a));
            Variable result = gen.newVariable(kind);
            gen.append(new AArch64ArithmeticOp.BinaryShiftOp(AArch64ArithmeticOp.SUB, result, AArch64.zr.asValue(kind), src, shiftType, shiftAmt));
            return result;
        };
    }

    /**
     * Goal: use AArch64's and not (bic) & or not (orn) instructions.
     */
    @MatchRule("(And=logic value1 (Not=not value2))")
    @MatchRule("(Or=logic value1 (Not=not value2))")
    public final ComplexMatchResult bitwiseLogicNot(BinaryNode logic, NotNode not) {
        assert isNumericInteger(logic);
        AArch64ArithmeticOp op = logicalNotOpMap.get(logic.getClass());
        assert op != null;
        ValueNode src1 = logic.getX() == not ? logic.getY() : logic.getX();
        ValueNode src2 = not.getValue();
        return builder -> {
            Value a = operand(src1);
            Value b = operand(src2);
            LIRKind resultKind = LIRKind.combine(a, b);
            return getArithmeticLIRGenerator().emitBinary(resultKind, op, false, a, b);
        };
    }

    /**
     * Goal: Use AArch64's bitwise exclusive or not (eon) instruction.
     *
     * Note that !(A^B) == (!A)^B == A^(!B).
     */
    @MatchRule("(Not (Xor value1 value2))")
    @MatchRule("(Xor value1 (Not value2))")
    @MatchRule("(Xor (Not value1) value2)")
    public ComplexMatchResult bitwiseNotXor(ValueNode value1, ValueNode value2) {
        return builder -> {
            Value a = operand(value1);
            Value b = operand(value2);
            LIRKind resultKind = LIRKind.combine(a, b);
            return getArithmeticLIRGenerator().emitBinary(resultKind, AArch64ArithmeticOp.EON, true, a, b);
        };
    }

    /**
     * Checks whether this multiply operation which can fold in a sign extend operation . This can
     * happen if:
     *
     * <ul>
     * <li>The multiply result is of type long</li>
     * <li>Both inputs are sign extended from 32 bits to 64 bits</li>
     * </ul>
     */
    private static boolean isI2LMultiply(MulNode mul, SignExtendNode x, SignExtendNode y) {
        if (mul.getStackKind() == JavaKind.Long) {
            assert x.getResultBits() == Long.SIZE && y.getResultBits() == Long.SIZE;
            return x.getInputBits() == Integer.SIZE && y.getInputBits() == Integer.SIZE;
        }
        return false;
    }

    /**
     * Goal: use AArch64's (i32,i32) -> i64 multiply instructions to fold away sign extensions.
     */
    @MatchRule("(Add=binary (Mul=mul (SignExtend a) (SignExtend b)) c)")
    @MatchRule("(Sub=binary c (Mul=mul (SignExtend a) (SignExtend b)))")
    public ComplexMatchResult signedMultiplyAddSubLong(BinaryNode binary, MulNode mul, ValueNode a, ValueNode b, ValueNode c) {
        assert isNumericInteger(binary, mul, a, b, c);
        if (isI2LMultiply(mul, (SignExtendNode) mul.getX(), (SignExtendNode) mul.getY())) {
            if (binary instanceof AddNode) {
                return builder -> getArithmeticLIRGenerator().emitIntegerMAdd(operand(a), operand(b), operand(c), true);
            }
            return builder -> getArithmeticLIRGenerator().emitIntegerMSub(operand(a), operand(b), operand(c), true);
        } else {
            return null;
        }
    }

    /**
     * Goal: use AArch64's (i32,i32) -> i64 multiply instructions to fold away sign extensions.
     */
    @MatchRule("(Negate (Mul=mul (SignExtend=ext1 a) (SignExtend=ext2 b)))")
    @MatchRule("(Mul=mul (Negate (SignExtend=ext1 a)) (SignExtend=ext2 b))")
    public ComplexMatchResult signedMultiplyNegLong(MulNode mul, SignExtendNode ext1, SignExtendNode ext2, ValueNode a, ValueNode b) {
        assert isNumericInteger(mul, ext1, ext2, a, b);
        if (isI2LMultiply(mul, ext1, ext2)) {
            LIRKind resultKind = LIRKind.fromJavaKind(gen.target().arch, JavaKind.Long);
            return builder -> getArithmeticLIRGenerator().emitBinary(
                            resultKind, AArch64ArithmeticOp.SMNEGL, true, operand(a), operand(b));
        } else {
            return null;
        }
    }

    /**
     * Goal: use AArch64's (i32,i32) -> i64 multiply instructions to fold away sign extensions.
     */
    @MatchRule("(Mul=mul (SignExtend a) (SignExtend b))")
    public ComplexMatchResult signedMultiplyLong(MulNode mul, ValueNode a, ValueNode b) {
        assert isNumericInteger(mul, a, b);
        if (isI2LMultiply(mul, (SignExtendNode) mul.getX(), (SignExtendNode) mul.getY())) {
            LIRKind resultKind = LIRKind.fromJavaKind(gen.target().arch, JavaKind.Long);
            return builder -> getArithmeticLIRGenerator().emitBinary(
                            resultKind, AArch64ArithmeticOp.SMULL, true, operand(a), operand(b));
        } else {
            return null;
        }
    }

    /**
     * Goal: Use AArch64's add/sub (extended register) instructions to fold in and operand.
     */
    @MatchRule("(Add=op x (And y Constant=constant))")
    @MatchRule("(Sub=op x (And y Constant=constant))")
    public ComplexMatchResult mergeDowncastIntoAddSub(BinaryNode op, ValueNode x, ValueNode y, ConstantNode constant) {
        assert isNumericInteger(constant);

        long mask = constant.asJavaConstant().asLong() & CodeUtil.mask(op.getStackKind().getBitCount());
        if (mask != 0xFFL && mask != 0xFFFFL && mask != 0xFFFFFFFFL) {
            return null;
        }

        int numBits = 64 - Long.numberOfLeadingZeros(mask);
        return emitExtendedAddSubShift(op, x, y, getZeroExtendType(numBits), 0);
    }

    /**
     * Goal: Use AArch64's add/sub (extended register) instruction.
     */
    @MatchRule("(Add=op x (SignExtend=ext y))")
    @MatchRule("(Sub=op x (SignExtend=ext y))")
    @MatchRule("(Add=op x (ZeroExtend=ext y))")
    @MatchRule("(Sub=op x (ZeroExtend=ext y))")
    public ComplexMatchResult mergeSignExtendIntoAddSub(BinaryNode op, UnaryNode ext, ValueNode x, ValueNode y) {
        if (!isSupportedExtendedAddSubShift((IntegerConvertNode<?>) ext, 0)) {
            return null;
        }

        ExtendType extType;
        if (ext instanceof SignExtendNode) {
            extType = getSignExtendType(((SignExtendNode) ext).getInputBits());
        } else {
            extType = getZeroExtendType(((ZeroExtendNode) ext).getInputBits());
        }
        return emitExtendedAddSubShift(op, x, y, extType, 0);
    }

    /**
     * Goal: Use AArch64's multiple-negate (mneg) instruction.
     */
    @MatchRule("(Mul (Negate a) b)")
    @MatchRule("(Negate (Mul a b))")
    public final ComplexMatchResult multiplyNegate(ValueNode a, ValueNode b) {
        if (isNumericInteger(a, b)) {
            return builder -> getArithmeticLIRGenerator().emitMNeg(operand(a), operand(b));
        }
        return null;
    }

    /**
     * Goal: Use AArch64's multiply-add (madd) and multiply-subtract (msub) instructions.
     */
    @MatchRule("(Add=binary (Mul a b) c)")
    @MatchRule("(Sub=binary c (Mul a b))")
    public final ComplexMatchResult multiplyAddSub(BinaryNode binary, ValueNode a, ValueNode b, ValueNode c) {
        if (!(isNumericInteger(a, b, c))) {
            return null;
        }

        if (binary instanceof AddNode) {
            return builder -> getArithmeticLIRGenerator().emitIntegerMAdd(operand(a), operand(b), operand(c), false);
        }
        return builder -> getArithmeticLIRGenerator().emitIntegerMSub(operand(a), operand(b), operand(c), false);
    }

    /**
     * Goal: Transform ((x & (1 << n)) == 0) -> (tbz/tbnz n label).
     */
    @MatchRule("(If (IntegerTest value Constant=a))")
    public ComplexMatchResult testBitAndBranch(IfNode root, ValueNode value, ConstantNode a) {
        if (isNumericInteger(value)) {
            long constant = a.asJavaConstant().asLong();
            if (Long.bitCount(constant) == 1) {
                return emitBitTestAndBranch(root.trueSuccessor(), root.falseSuccessor(), value,
                                root.getTrueSuccessorProbability(), Long.numberOfTrailingZeros(constant));
            }
        }
        return null;
    }

    /**
     * Goal: Transform (if x < 0) -> (tbz x, sizeOfBits(x) - 1, label).
     */
    @MatchRule("(If (IntegerLessThan=lessNode x Constant=y))")
    public ComplexMatchResult checkNegativeAndBranch(IfNode root, IntegerLessThanNode lessNode, ValueNode x, ConstantNode y) {
        assert isNumericInteger(x);
        if (y.isJavaConstant() && (0 == y.asJavaConstant().asLong()) && lessNode.condition().equals(CanonicalCondition.LT)) {
            return emitBitTestAndBranch(root.falseSuccessor(), root.trueSuccessor(), x,
                            1.0 - root.getTrueSuccessorProbability(), x.getStackKind().getBitCount() - 1);
        }
        return null;
    }

    /**
     * Goal: Use directly AArch64's single-precision fsqrt op.
     */
    @MatchRule("(FloatConvert=a (Sqrt (FloatConvert=b c)))")
    public final ComplexMatchResult floatSqrt(FloatConvertNode a, FloatConvertNode b, ValueNode c) {
        if (isNumericFloat(a, c)) {
            if (a.getFloatConvert() == FloatConvert.D2F && b.getFloatConvert() == FloatConvert.F2D) {
                return builder -> getArithmeticLIRGenerator().emitMathSqrt(operand(c));
            }
        }
        return null;
    }

    @MatchRule("(Conditional (IntegerBelow x y) Constant=cm1 (Conditional (IntegerEquals x y) Constant=c0 Constant=c1))")
    public ComplexMatchResult normalizedIntegerCompare(ValueNode x, ValueNode y, ConstantNode cm1, ConstantNode c0, ConstantNode c1) {
        if (cm1.getStackKind() == JavaKind.Int && cm1.asJavaConstant().asInt() == -1 && c0.getStackKind() == JavaKind.Int && c0.asJavaConstant().asInt() == 0 && c1.getStackKind() == JavaKind.Int &&
                        c1.asJavaConstant().asInt() == 1) {
            LIRKind compareKind = gen.getLIRKind(x.stamp(NodeView.DEFAULT));
            return builder -> getArithmeticLIRGenerator().emitNormalizedUnsignedCompare(compareKind, operand(x), operand(y));
        }
        return null;
    }

    @Override
    public AArch64LIRGenerator getLIRGeneratorTool() {
        return (AArch64LIRGenerator) gen;
    }

    protected AArch64ArithmeticLIRGenerator getArithmeticLIRGenerator() {
        return (AArch64ArithmeticLIRGenerator) getLIRGeneratorTool().getArithmetic();
    }
}
