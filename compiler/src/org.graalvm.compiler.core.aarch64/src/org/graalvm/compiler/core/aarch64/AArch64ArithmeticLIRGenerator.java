/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_DREM;
import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_FREM;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static org.graalvm.compiler.lir.aarch64.AArch64BitManipulationOp.BitManipulationOpCode.BSR;
import static org.graalvm.compiler.lir.aarch64.AArch64BitManipulationOp.BitManipulationOpCode.CLZ;
import static org.graalvm.compiler.lir.aarch64.AArch64BitManipulationOp.BitManipulationOpCode.CTZ;
import static org.graalvm.compiler.lir.aarch64.AArch64BitManipulationOp.BitManipulationOpCode.POPCNT;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.CastValue;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.graalvm.compiler.lir.aarch64.AArch64BitManipulationOp;
import org.graalvm.compiler.lir.aarch64.AArch64Convert;
import org.graalvm.compiler.lir.aarch64.AArch64MathCopySignOp;
import org.graalvm.compiler.lir.aarch64.AArch64MathSignumOp;
import org.graalvm.compiler.lir.aarch64.AArch64Move;
import org.graalvm.compiler.lir.aarch64.AArch64Move.LoadOp;
import org.graalvm.compiler.lir.aarch64.AArch64Move.StoreOp;
import org.graalvm.compiler.lir.aarch64.AArch64Move.StoreZeroOp;
import org.graalvm.compiler.lir.aarch64.AArch64ReinterpretOp;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class AArch64ArithmeticLIRGenerator extends ArithmeticLIRGenerator implements ArithmeticLIRGeneratorTool {

    public AArch64ArithmeticLIRGenerator(AllocatableValue nullRegisterValue) {
        this.nullRegisterValue = nullRegisterValue;
    }

    private final AllocatableValue nullRegisterValue;

    @Override
    public AArch64LIRGenerator getLIRGen() {
        return (AArch64LIRGenerator) super.getLIRGen();
    }

    public boolean mustReplaceNullWithNullRegister(JavaConstant nullConstant) {
        /* Uncompressed null pointers only */
        return nullRegisterValue != null && JavaConstant.NULL_POINTER.equals(nullConstant);
    }

    public AllocatableValue getNullRegisterValue() {
        assert nullRegisterValue != null : "Should not be requesting null register value.";
        return nullRegisterValue;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        return ((AArch64Kind) kind).isInteger();
    }

    @Override
    protected Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            AArch64ArithmeticOp op = setFlags ? AArch64ArithmeticOp.ADDS : AArch64ArithmeticOp.ADD;
            return emitBinary(resultKind, op, true, a, b);
        } else {
            assert !setFlags : "Cannot set flags on floating point arithmetic";
            return emitBinary(resultKind, AArch64ArithmeticOp.FADD, true, a, b);
        }
    }

    @Override
    protected Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            AArch64ArithmeticOp op = setFlags ? AArch64ArithmeticOp.SUBS : AArch64ArithmeticOp.SUB;
            return emitBinary(resultKind, op, false, a, b);
        } else {
            assert !setFlags : "Cannot set flags on floating point arithmetic";
            return emitBinary(resultKind, AArch64ArithmeticOp.FSUB, false, a, b);
        }
    }

    public Value emitExtendMemory(boolean isSigned, AArch64Kind accessKind, int resultBits, AArch64AddressValue address, LIRFrameState state) {
        /*
         * Issue an extending load of the proper bit size and set the result to the proper kind.
         */
        GraalError.guarantee(accessKind.isInteger(), "can only extend integer kinds");
        AArch64Kind resultKind = resultBits <= 32 ? AArch64Kind.DWORD : AArch64Kind.QWORD;
        Variable result = getLIRGen().newVariable(LIRKind.value(resultKind));

        AArch64Move.ExtendKind extend = isSigned ? AArch64Move.ExtendKind.SIGN_EXTEND : AArch64Move.ExtendKind.ZERO_EXTEND;
        getLIRGen().append(new AArch64Move.LoadOp(accessKind, resultKind.getSizeInBytes() * Byte.SIZE, extend, result, address, state));
        return result;
    }

    @Override
    public Value emitMul(Value a, Value b, boolean setFlags) {
        AArch64ArithmeticOp intOp = setFlags ? AArch64ArithmeticOp.MULVS : AArch64ArithmeticOp.MUL;
        return emitBinary(LIRKind.combine(a, b), getOpCode(a, intOp, AArch64ArithmeticOp.FMUL), true, a, b);
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.SMULH, true, a, b);
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.UMULH, true, a, b);
    }

    public Value emitMNeg(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind()) && isNumericInteger(b.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.MNEG, true, a, b);
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        return emitBinary(LIRKind.combine(a, b), getOpCode(a, AArch64ArithmeticOp.DIV, AArch64ArithmeticOp.FDIV), false, asAllocatable(a), asAllocatable(b));
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        if (isNumericInteger(a.getPlatformKind())) {
            return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.REM, false, asAllocatable(a), asAllocatable(b));
        } else {
            switch ((AArch64Kind) a.getPlatformKind()) {
                case SINGLE:
                    ForeignCallLinkage fremCall = getLIRGen().getForeignCalls().lookupForeignCall(ARITHMETIC_FREM);
                    return getLIRGen().emitForeignCall(fremCall, state, a, b);
                case DOUBLE:
                    ForeignCallLinkage dremCall = getLIRGen().getForeignCalls().lookupForeignCall(ARITHMETIC_DREM);
                    return getLIRGen().emitForeignCall(dremCall, state, a, b);
                default:
                    GraalError.shouldNotReachHere("emitRem on unexpected kind " + a.getPlatformKind());
                    return null;
            }
        }
    }

    @Override
    public Value emitUDiv(Value a, Value b, LIRFrameState state) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.UDIV, false, asAllocatable(a), asAllocatable(b));
    }

    @Override
    public Value emitURem(Value a, Value b, LIRFrameState state) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.UREM, false, asAllocatable(a), asAllocatable(b));
    }

    @Override
    public Value emitAnd(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.AND, true, a, b);
    }

    @Override
    public Value emitOr(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.OR, true, a, b);
    }

    @Override
    public Value emitXor(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.XOR, true, a, b);
    }

    @Override
    public Value emitXorFP(Value a, Value b) {
        assert (a.getPlatformKind() == AArch64Kind.SINGLE || a.getPlatformKind() == AArch64Kind.DOUBLE) &&
                        a.getPlatformKind() == b.getPlatformKind();

        /*
         * Use the ASIMD XOR instruction to perform this operation. This requires casting the
         * operands to SIMD kinds of the equivalent size.
         */
        LIRKind simdKind;
        if (a.getPlatformKind() == AArch64Kind.SINGLE) {
            simdKind = LIRKind.value(AArch64Kind.V32_BYTE);
        } else {
            simdKind = LIRKind.value(AArch64Kind.V64_BYTE);
        }
        Variable result = getLIRGen().newVariable(simdKind);

        CastValue castA = new CastValue(simdKind, asAllocatable(a));
        CastValue castB = new CastValue(simdKind, asAllocatable(b));
        getLIRGen().append(AArch64ArithmeticOp.generateASIMDBinaryInstruction(AArch64ArithmeticOp.XOR, result, castA, castB));

        // Must cast SIMD result back to appropriate FP type
        return new CastValue(LIRKind.combine(a, b), result);
    }

    @Override
    public Value emitShl(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.LSL, false, a, b);
    }

    @Override
    public Value emitShr(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.ASR, false, a, b);
    }

    @Override
    public Value emitUShr(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.LSR, false, a, b);
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        PlatformKind resultPlatformKind = getFloatConvertResultKind(op);
        LIRKind resultLirKind = LIRKind.combine(inputVal).changeType(resultPlatformKind);
        Variable result = getLIRGen().newVariable(resultLirKind);
        getLIRGen().append(new AArch64Convert.FloatConvertOp(op, result, asAllocatable(inputVal)));
        return result;
    }

    public Value emitIntegerMAdd(Value a, Value b, Value c, boolean isI2L) {
        return emitMultiplyAddSub(isI2L ? AArch64ArithmeticOp.SMADDL : AArch64ArithmeticOp.MADD, a, b, c);
    }

    public Value emitIntegerMSub(Value a, Value b, Value c, boolean isI2L) {
        return emitMultiplyAddSub(isI2L ? AArch64ArithmeticOp.SMSUBL : AArch64ArithmeticOp.MSUB, a, b, c);
    }

    protected Value emitMultiplyAddSub(AArch64ArithmeticOp op, Value a, Value b, Value c) {
        assert a.getPlatformKind() == b.getPlatformKind();
        Variable result;
        if (op == AArch64ArithmeticOp.SMADDL || op == AArch64ArithmeticOp.SMSUBL) {
            // For signed multiply int and then add/sub long.
            assert a.getPlatformKind() != c.getPlatformKind();
            result = getLIRGen().newVariable(LIRKind.combine(c));
        } else {
            assert a.getPlatformKind() == c.getPlatformKind();
            if (op == AArch64ArithmeticOp.FMADD) {
                // For floating-point Math.fma intrinsic.
                assert a.getPlatformKind() == AArch64Kind.SINGLE || a.getPlatformKind() == AArch64Kind.DOUBLE;
            } else {
                // For int/long multiply add or sub.
                assert op == AArch64ArithmeticOp.MADD || op == AArch64ArithmeticOp.MSUB;
                assert isNumericInteger(a.getPlatformKind());
            }
            result = getLIRGen().newVariable(LIRKind.combine(a, b, c));
        }

        AllocatableValue x = moveSp(asAllocatable(a));
        AllocatableValue y = moveSp(asAllocatable(b));
        AllocatableValue z = moveSp(asAllocatable(c));
        getLIRGen().append(new AArch64ArithmeticOp.MultiplyAddSubOp(op, result, x, y, z));
        return result;
    }

    protected static AArch64Kind getFloatConvertResultKind(FloatConvert op) {
        switch (op) {
            case F2I:
            case D2I:
                return AArch64Kind.DWORD;
            case F2L:
            case D2L:
                return AArch64Kind.QWORD;
            case I2F:
            case L2F:
            case D2F:
                return AArch64Kind.SINGLE;
            case I2D:
            case L2D:
            case F2D:
                return AArch64Kind.DOUBLE;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        ValueKind<?> from = inputVal.getValueKind();
        if (to.equals(from)) {
            return inputVal;
        }
        Variable result = getLIRGen().newVariable(to);
        getLIRGen().append(new AArch64ReinterpretOp(result, asAllocatable(inputVal)));
        return result;
    }

    @Override
    public Value emitNarrow(Value inputVal, int toBits) {
        /*
         * The net effect of a narrow is only to change the underlying AArchKind. Because AArch64
         * instructions operate on registers of either 32 or 64 bits, if needed, we switch the value
         * type from QWORD to DWORD.
         *
         * Ideally, switching the value type shouldn't require a move, but instead could be
         * reinterpreted in some other way.
         */
        if (inputVal.getPlatformKind() == AArch64Kind.QWORD && toBits <= 32) {
            LIRKind resultKind = getLIRKindForBitSize(toBits, inputVal);
            assert resultKind.getPlatformKind() == AArch64Kind.DWORD;
            Variable result = getLIRGen().newVariable(resultKind);
            getLIRGen().emitMove(result, inputVal);
            return result;
        } else {
            return inputVal;
        }
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        }
        LIRKind resultKind = getLIRKindForBitSize(toBits, inputVal);
        long mask = NumUtil.getNbitNumberLong(fromBits);
        Value maskValue = new ConstantValue(resultKind, JavaConstant.forLong(mask));
        return emitBinary(resultKind, AArch64ArithmeticOp.AND, true, inputVal, maskValue);
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        LIRKind resultKind = getLIRKindForBitSize(toBits, inputVal);
        if (fromBits == toBits) {
            return inputVal;
        } else if (isJavaConstant(inputVal)) {
            JavaConstant javaConstant = asJavaConstant(inputVal);
            long constant;
            if (javaConstant.isNull()) {
                constant = 0;
            } else {
                constant = javaConstant.asLong();
            }
            /*
             * Performing sign extend via a left shift followed by an arithmetic right shift. First,
             * a left shift of (64 - fromBits) is performed to remove non-meaningful bits, and then
             * an arithmetic right shift is used to set correctly all sign bits. Note the "toBits"
             * size is not considered, as the constant is saved as a long value.
             */
            int shiftSize = 64 - fromBits;
            long signExtendedValue = (constant << shiftSize) >> shiftSize;
            return new ConstantValue(resultKind, JavaConstant.forLong(signExtendedValue));
        }
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AArch64Convert.SignExtendOp(result, asAllocatable(inputVal), fromBits, toBits));
        return result;
    }

    private static LIRKind getLIRKindForBitSize(int bitSize, Value... inputValues) {
        /*
         * AArch64 general-purpose operations are either 32 or 64 bits.
         */
        assert bitSize <= 64;
        if (bitSize <= 32) {
            return LIRKind.combine(inputValues).changeType(AArch64Kind.DWORD);
        } else {
            return LIRKind.combine(inputValues).changeType(AArch64Kind.QWORD);
        }
    }

    protected Variable emitBinary(LIRKind resultKind, AArch64ArithmeticOp op, boolean commutative, Value a, Value b) {
        Variable result = getLIRGen().newVariable(resultKind);
        emitBinary(result, op, commutative, a, b);
        return result;
    }

    protected void emitBinary(AllocatableValue result, AArch64ArithmeticOp op, boolean commutative, Value a, Value b) {
        AArch64Kind opKind = (AArch64Kind) result.getPlatformKind();
        if (isValidBinaryConstant(op, opKind, b)) {
            emitBinaryConst(result, op, asAllocatable(a), asJavaConstant(b));
        } else if (commutative && isValidBinaryConstant(op, opKind, a)) {
            emitBinaryConst(result, op, asAllocatable(b), asJavaConstant(a));
        } else {
            emitBinaryVar(result, op, asAllocatable(a), asAllocatable(b));
        }
    }

    private void emitBinaryVar(AllocatableValue result, AArch64ArithmeticOp op, AllocatableValue a, AllocatableValue b) {
        AllocatableValue x = moveSp(a);
        AllocatableValue y = moveSp(b);
        getLIRGen().append(new AArch64ArithmeticOp.BinaryOp(op, result, x, y));
    }

    public void emitBinaryConst(AllocatableValue result, AArch64ArithmeticOp op, AllocatableValue a, JavaConstant b) {
        AllocatableValue x = moveSp(a);
        getLIRGen().append(new AArch64ArithmeticOp.BinaryConstOp(op, result, x, b));
    }

    private static boolean isValidBinaryConstant(AArch64ArithmeticOp op, AArch64Kind opKind, Value val) {
        if (!isJavaConstant(val)) {
            return false;
        }
        JavaConstant constValue = asJavaConstant(val);
        switch (op.category) {
            case LOGICAL:
                return isLogicalConstant(opKind, constValue);
            case ADDSUBTRACT:
                return isAddSubtractConstant(constValue);
            case SHIFT:
                return true;
            case NONE:
                return false;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private static boolean isLogicalConstant(AArch64Kind kind, JavaConstant constValue) {
        long value = constValue.asLong();
        switch (kind) {
            case DWORD:
                return AArch64MacroAssembler.isLogicalImmediate(32, value);
            case QWORD:
                return AArch64MacroAssembler.isLogicalImmediate(64, value);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public static boolean isAddSubtractConstant(JavaConstant constValue) {
        switch (constValue.getJavaKind().getStackKind()) {
            case Int:
            case Long:
                return AArch64MacroAssembler.isAddSubtractImmediate(constValue.asLong(), true);
            case Object:
                return constValue.isNull();
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitNegate(Value inputVal) {
        return emitUnary(getOpCode(inputVal, AArch64ArithmeticOp.NEG, AArch64ArithmeticOp.FNEG), inputVal);
    }

    @Override
    public Value emitNot(Value input) {
        assert isNumericInteger(input.getPlatformKind());
        return emitUnary(AArch64ArithmeticOp.NOT, input);
    }

    @Override
    public Value emitMathAbs(Value input) {
        return emitUnary(getOpCode(input, AArch64ArithmeticOp.ABS, AArch64ArithmeticOp.FABS), input);
    }

    @Override
    public Value emitMathMax(Value a, Value b) {
        assert a.getPlatformKind() == b.getPlatformKind();
        assert a.getPlatformKind() == AArch64Kind.DOUBLE ||
                        a.getPlatformKind() == AArch64Kind.SINGLE;
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.FMAX, true, a, b);
    }

    @Override
    public Value emitMathMin(Value a, Value b) {
        assert a.getPlatformKind() == b.getPlatformKind();
        assert a.getPlatformKind() == AArch64Kind.DOUBLE ||
                        a.getPlatformKind() == AArch64Kind.SINGLE;
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.FMIN, true, a, b);
    }

    @Override
    public Value emitMathSqrt(Value input) {
        assert input.getPlatformKind() == AArch64Kind.DOUBLE ||
                        input.getPlatformKind() == AArch64Kind.SINGLE;
        return emitUnary(AArch64ArithmeticOp.FSQRT, input);
    }

    @Override
    public Value emitMathSignum(Value input) {
        assert input.getPlatformKind() == AArch64Kind.DOUBLE ||
                        input.getPlatformKind() == AArch64Kind.SINGLE;
        Variable result = getLIRGen().newVariable(input.getValueKind());
        getLIRGen().append(new AArch64MathSignumOp(getLIRGen(), result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathCopySign(Value magnitude, Value sign) {
        assert magnitude.getPlatformKind() == AArch64Kind.DOUBLE ||
                        magnitude.getPlatformKind() == AArch64Kind.SINGLE;
        Variable result = getLIRGen().newVariable(magnitude.getValueKind());
        getLIRGen().append(new AArch64MathCopySignOp(result, asAllocatable(magnitude), asAllocatable(sign)));
        return result;
    }

    @Override
    public Variable emitBitScanForward(Value value) {
        throw GraalError.unimplemented();
    }

    @Override
    public Value emitBitCount(Value operand) {
        assert ((AArch64Kind) operand.getPlatformKind()).isInteger();
        Variable result = getLIRGen().newVariable(LIRKind.combine(operand).changeType(AArch64Kind.DWORD));
        getLIRGen().append(new AArch64BitManipulationOp(getLIRGen(), POPCNT, result, asAllocatable(operand)));
        return result;
    }

    @Override
    public Value emitBitScanReverse(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AArch64Kind.DWORD));
        getLIRGen().append(new AArch64BitManipulationOp(getLIRGen(), BSR, result, asAllocatable(value)));
        return result;
    }

    @Override
    public Value emitFusedMultiplyAdd(Value a, Value b, Value c) {
        return emitMultiplyAddSub(AArch64ArithmeticOp.FMADD, a, b, c);
    }

    @Override
    public Value emitCountLeadingZeros(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AArch64Kind.DWORD));
        getLIRGen().append(new AArch64BitManipulationOp(getLIRGen(), CLZ, result, asAllocatable(value)));
        return result;
    }

    @Override
    public Value emitCountTrailingZeros(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AArch64Kind.DWORD));
        getLIRGen().append(new AArch64BitManipulationOp(getLIRGen(), CTZ, result, asAllocatable(value)));
        return result;
    }

    private Variable emitUnary(AArch64ArithmeticOp op, Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AArch64ArithmeticOp.UnaryOp(op, result, input));
        return result;
    }

    private AllocatableValue moveSp(AllocatableValue val) {
        return getLIRGen().moveSp(val);
    }

    /**
     * Returns the opcode depending on the platform kind of val.
     */
    private AArch64ArithmeticOp getOpCode(Value val, AArch64ArithmeticOp intOp, AArch64ArithmeticOp floatOp) {
        return isNumericInteger(val.getPlatformKind()) ? intOp : floatOp;
    }

    @Override
    public Variable emitLoad(LIRKind lirKind, Value address, LIRFrameState state) {
        AArch64Kind kind = (AArch64Kind) lirKind.getPlatformKind();
        Variable result = getLIRGen().newVariable(getLIRGen().toRegisterKind(lirKind));
        AArch64AddressValue loadAddress = getLIRGen().asAddressValue(address, kind.getSizeInBytes() * Byte.SIZE);
        getLIRGen().append(new LoadOp(kind, result, loadAddress, state));
        return result;
    }

    @Override
    public Variable emitVolatileLoad(LIRKind lirKind, Value address, LIRFrameState state) {
        AArch64Kind kind = (AArch64Kind) lirKind.getPlatformKind();
        Variable result = getLIRGen().newVariable(getLIRGen().toRegisterKind(lirKind));
        AArch64AddressValue loadAddress = getLIRGen().asAddressValue(address, kind.getSizeInBytes() * Byte.SIZE);
        getLIRGen().append(new AArch64Move.VolatileLoadOp(kind, result, loadAddress, state));
        return result;
    }

    @Override
    public void emitStore(ValueKind<?> lirKind, Value address, Value inputVal, LIRFrameState state) {
        AArch64Kind kind = (AArch64Kind) lirKind.getPlatformKind();
        AArch64AddressValue storeAddress = getLIRGen().asAddressValue(address, kind.getSizeInBytes() * Byte.SIZE);

        /* We can store 0 directly via the gp zr register. */
        if (kind.getSizeInBytes() <= Long.BYTES && isJavaConstant(inputVal)) {
            JavaConstant c = asJavaConstant(inputVal);
            if (c.isDefaultForKind()) {
                getLIRGen().append(new StoreZeroOp(kind, storeAddress, state));
                return;
            }
        }
        AllocatableValue input = asAllocatable(inputVal);
        getLIRGen().append(new StoreOp(kind, storeAddress, input, state));
    }

    @Override
    public void emitVolatileStore(ValueKind<?> lirKind, Value address, Value inputVal, LIRFrameState state) {
        AArch64Kind kind = (AArch64Kind) lirKind.getPlatformKind();
        AllocatableValue input = asAllocatable(inputVal);
        AArch64AddressValue storeAddress = getLIRGen().asAddressValue(address, kind.getSizeInBytes() * Byte.SIZE);
        getLIRGen().append(new AArch64Move.VolatileStoreOp(kind, storeAddress, input, state));
    }

    @Override
    public Value emitRound(Value value, ArithmeticLIRGeneratorTool.RoundingMode mode) {
        AArch64ArithmeticOp op;
        switch (mode) {
            case NEAREST:
                op = AArch64ArithmeticOp.FRINTN;
                break;
            case UP:
                op = AArch64ArithmeticOp.FRINTP;
                break;
            case DOWN:
                op = AArch64ArithmeticOp.FRINTM;
                break;
            case TRUNCATE:
                op = AArch64ArithmeticOp.FRINTZ;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }

        return emitUnary(op, value);
    }
}
