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

import static jdk.graal.compiler.lir.LIRValueUtil.asConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.asJavaConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isConstantValue;
import static jdk.graal.compiler.lir.LIRValueUtil.isJavaConstant;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.ABS;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.ADD;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.AND;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.ASR;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.BIC;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.FABS;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.FADD;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.FDIV;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.FMAX;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.FMIN;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.FMUL;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.FNEG;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.FSQRT;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.FSUB;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.LSL;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.LSR;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.MUL;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.NEG;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.NOT;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.OR;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.SMADDL;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.SMAX;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.SMIN;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.SMSUBL;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.SUB;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.TST;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.UMAX;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.UMIN;
import static jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp.XOR;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDMacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.ASIMDKind;
import jdk.graal.compiler.core.aarch64.AArch64ArithmeticLIRGenerator;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp;
import jdk.graal.compiler.lir.aarch64.AArch64ByteSwap;
import jdk.graal.compiler.lir.aarch64.AArch64Compare;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow;
import jdk.graal.compiler.lir.aarch64.AArch64Convert;
import jdk.graal.compiler.lir.aarch64.AArch64PermuteOp;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class AArch64VectorArithmeticLIRGenerator extends AArch64ArithmeticLIRGenerator implements VectorLIRGeneratorTool {
    public AArch64VectorArithmeticLIRGenerator(AllocatableValue nullRegisterValue) {
        super(nullRegisterValue);
    }

    private static boolean isImmediateEncodable(AArch64Kind vectorKind, JavaConstant constant) {
        AArch64Kind scalarKind = vectorKind.getScalar();
        ElementSize eSize = ElementSize.fromKind(scalarKind);
        switch (scalarKind) {
            case BYTE:
            case WORD:
            case DWORD:
            case QWORD:
                return AArch64ASIMDMacroAssembler.isMoveImmediate(eSize, constant.asLong());
            case SINGLE:
                return AArch64ASIMDMacroAssembler.isMoveImmediate(constant.asFloat());
            case DOUBLE:
                return AArch64ASIMDMacroAssembler.isMoveImmediate(constant.asDouble());
        }
        return false;
    }

    @Override
    public Value emitVectorFill(LIRKind lirKind, Value value) {
        assert lirKind.getPlatformKind().getVectorLength() > 1 : lirKind.getPlatformKind();

        AArch64Kind kind = (AArch64Kind) lirKind.getPlatformKind();
        JavaConstant constant = isJavaConstant(value) ? asJavaConstant(value) : null;

        Variable result = getLIRGen().newVariable(lirKind);
        if (constant != null && isImmediateEncodable(kind, constant)) {
            getLIRGen().append(new AArch64ASIMDMove.ConstantVectorFillOp(result, constant));
        } else {
            getLIRGen().append(new AArch64ASIMDMove.VectorFill(result, asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Value emitSimdFromScalar(LIRKind lirKind, Value value) {
        assert lirKind.getPlatformKind().getVectorLength() > 1 : lirKind.getPlatformKind();
        assert value.getPlatformKind().getVectorLength() == 1 : value.getPlatformKind();
        Variable result = getLIRGen().newVariable(lirKind);
        getLIRGen().append(new AArch64ASIMDMove.ScalarToSIMDOp(result, asAllocatable(value)));
        return result;
    }

    @Override
    public Value emitVectorCut(int startIdx, int length, Value vector) {
        AArch64Kind oldKind = (AArch64Kind) vector.getPlatformKind();
        assert 0 <= startIdx && (startIdx + length) <= oldKind.getVectorLength() : "vector cut out of bounds: " + oldKind + "[" + startIdx + ".." + (startIdx + length - 1) + "]";
        AArch64Kind newKind = ASIMDKind.getASIMDKind(oldKind.getScalar(), length);
        /*
         * For scalar GP, the result size must be at least DWORD to work with subsequent operations.
         */
        if (newKind == AArch64Kind.BYTE || newKind == AArch64Kind.WORD) {
            newKind = AArch64Kind.DWORD;
        }
        ValueKind<?> resultKind = vector.getValueKind().changeType(newKind);
        if (oldKind == newKind) {
            assert startIdx == 0 : startIdx + " " + vector;
            /* No cut is actually being performed. */
            return vector;
        } else if (startIdx == 0 && newKind.isSIMD()) {
            assert oldKind.isSIMD() : oldKind;
            /*
             * Result will be in the same register category, so can use same value - just need to
             * cast to new type.
             */
            return new CastValue(resultKind, asAllocatable(vector));
        } else {
            Variable result = getLIRGen().newVariable(resultKind);
            getLIRGen().append(new AArch64ASIMDMove.VectorCut(result, asAllocatable(vector), startIdx));
            return result;
        }
    }

    @Override
    public Value emitVectorInsert(int offset, Value vector, Value val) {
        Variable result = getLIRGen().newVariable(vector.getValueKind());
        getLIRGen().append(new AArch64ASIMDMove.VectorInsert(result, asAllocatable(vector), asAllocatable(val), offset));
        return result;
    }

    private static boolean isVectorKind(Value value) {
        return value.getPlatformKind().getVectorLength() > 1;
    }

    private static boolean isVectorKind(ValueKind<?> value) {
        return value.getPlatformKind().getVectorLength() > 1;
    }

    private boolean isVectorNumericInteger(Value value) {
        AArch64Kind kind = (AArch64Kind) value.getPlatformKind();
        return isNumericInteger(kind.getScalar());
    }

    private AArch64ArithmeticOp getASIMDOpCode(Value value, AArch64ArithmeticOp intOp, AArch64ArithmeticOp floatOp) {
        return isVectorNumericInteger(value) ? intOp : floatOp;
    }

    private static boolean isValidVectorBinaryConstant(AArch64ArithmeticOp op, ValueKind<?> resultKind, Value value) {
        if (!(op == OR || op == BIC)) {
            /* Currently only OR or BIC can have a constant operand. */
            return false;
        }
        if (isConstantValue(value) && asConstant(value) instanceof SimdConstant) {
            SimdConstant simdConstant = (SimdConstant) asConstant(value);
            if (simdConstant.isAllSame()) {
                ElementSize eSize = ElementSize.fromKind(resultKind.getPlatformKind());
                long imm = simdConstant.getPrimitiveValue(0);
                switch (op) {
                    case OR:
                        return AArch64ASIMDMacroAssembler.isOrrImmediate(eSize, imm);
                    case BIC:
                        return AArch64ASIMDMacroAssembler.isBicImmediate(eSize, imm);
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
                }
            }
        }
        return false;
    }

    private Variable emitVectorBinaryConst(AArch64ArithmeticOp op, LIRKind resultKind, Value a, Value b) {
        SimdConstant simdConstant = (SimdConstant) asConstant(b);
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AArch64ArithmeticOp.ASIMDBinaryConstOp(op, result, asAllocatable(a), (JavaConstant) simdConstant.getValue(0)));
        return result;
    }

    private Variable emitVectorBinary(AArch64ArithmeticOp op, boolean commutative, LIRKind resultKind, Value a, Value b) {
        if (isValidVectorBinaryConstant(op, resultKind, b)) {
            return emitVectorBinaryConst(op, resultKind, a, b);
        } else if (commutative && isValidVectorBinaryConstant(op, resultKind, a)) {
            return emitVectorBinaryConst(op, resultKind, b, a);
        } else {
            return emitVectorBinary(op, resultKind, a, b);
        }
    }

    private Variable emitVectorBinary(AArch64ArithmeticOp op, LIRKind resultKind, Value a, Value b) {
        assert !resultKind.isUnknownReference() : resultKind;

        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(AArch64ArithmeticOp.generateASIMDBinaryInstruction(op, result, asAllocatable(a), asAllocatable(b)));
        return result;
    }

    private Variable emitVectorBinary(AArch64ArithmeticOp op, Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitVectorBinary(op, resultKind, a, b);
    }

    private Variable emitVectorUnary(AArch64ArithmeticOp op, Value a) {
        LIRKind resultKind = LIRKind.combine(a);
        assert !resultKind.isUnknownReference() : a + " " + resultKind;

        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AArch64ArithmeticOp.ASIMDUnaryOp(op, result, asAllocatable(a)));
        return result;
    }

    @Override
    protected Variable emitBinary(LIRKind resultKind, AArch64ArithmeticOp op, boolean commutative, Value a, Value b) {
        if (isVectorKind(resultKind)) {
            return emitVectorBinary(op, commutative, resultKind, a, b);
        } else {
            return super.emitBinary(resultKind, op, commutative, a, b);
        }
    }

    @Override
    public Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isVectorKind(resultKind)) {
            assert !setFlags : "Must not use flags with vector kinds " + resultKind + " " + a + " " + b;
            return emitVectorBinary(getASIMDOpCode(a, ADD, FADD), resultKind, a, b);
        } else {
            return super.emitAdd(resultKind, a, b, setFlags);
        }
    }

    @Override
    public Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isVectorKind(resultKind)) {
            assert !setFlags : "Must not use flags with vector kinds " + resultKind + " " + a + " " + b;
            return emitVectorBinary(getASIMDOpCode(a, SUB, FSUB), resultKind, a, b);
        } else {
            return super.emitSub(resultKind, a, b, setFlags);
        }
    }

    @Override
    public Value emitMul(Value a, Value b, boolean setFlags) {
        if (isVectorKind(a)) {
            assert !setFlags : "Must not use flags with vector kinds " + a + " " + b;
            return emitVectorBinary(getASIMDOpCode(a, MUL, FMUL), a, b);
        } else {
            return super.emitMul(a, b, setFlags);
        }
    }

    @Override
    public Value emitMNeg(Value a, Value b) {
        if (isVectorKind(a)) {
            assert isVectorKind(b) : b.getPlatformKind();
            return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.MNEG, true, a, b);
        } else {
            return super.emitMNeg(a, b);
        }
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        if (isVectorKind(a)) {
            assert !isVectorNumericInteger(a) : a.getPlatformKind();
            return emitVectorBinary(FDIV, a, b);
        } else {
            return super.emitDiv(a, b, state);
        }
    }

    @Override
    public Value emitAnd(Value a, Value b) {
        if (isVectorKind(a)) {
            return emitVectorBinary(AND, a, b);
        } else {
            return super.emitAnd(a, b);
        }
    }

    @Override
    public Value emitOr(Value a, Value b) {
        if (isVectorKind(a)) {
            LIRKind resultKind = LIRKind.combine(a, b);
            return emitVectorBinary(OR, true, resultKind, a, b);
        } else {
            return super.emitOr(a, b);
        }
    }

    @Override
    public Value emitXor(Value a, Value b) {
        if (isVectorKind(a)) {
            return emitVectorBinary(XOR, a, b);
        } else {
            return super.emitXor(a, b);
        }
    }

    @Override
    public Value emitMathMax(Value a, Value b) {
        if (isVectorKind(a)) {
            return emitVectorBinary(isVectorNumericInteger(a) ? SMAX : FMAX, a, b);
        } else {
            return super.emitMathMax(a, b);
        }
    }

    @Override
    public Value emitMathMin(Value a, Value b) {
        if (isVectorKind(a)) {
            return emitVectorBinary(isVectorNumericInteger(a) ? SMIN : FMIN, a, b);
        } else {
            return super.emitMathMin(a, b);
        }
    }

    @Override
    public Value emitMathUnsignedMax(Value a, Value b) {
        GraalError.guarantee(isVectorKind(a) && isVectorNumericInteger(a), "unsigned max only supported on integer vectors");
        return emitVectorBinary(UMAX, a, b);
    }

    @Override
    public Value emitMathUnsignedMin(Value a, Value b) {
        GraalError.guarantee(isVectorKind(a) && isVectorNumericInteger(a), "unsigned min only supported on integer vectors");
        return emitVectorBinary(UMIN, a, b);
    }

    @Override
    public Value emitNegate(Value input, boolean setFlags) {
        if (isVectorKind(input)) {
            assert !setFlags : " " + input;
            return emitVectorUnary(getASIMDOpCode(input, NEG, FNEG), input);
        } else {
            return super.emitNegate(input, setFlags);
        }
    }

    @Override
    public Value emitNot(Value input) {
        if (isVectorKind(input)) {
            assert isVectorNumericInteger(input) : input.getPlatformKind();
            return emitVectorUnary(NOT, input);
        } else {
            return super.emitNot(input);
        }
    }

    @Override
    public Value emitMathAbs(Value input) {
        if (isVectorKind(input)) {
            return emitVectorUnary(getASIMDOpCode(input, ABS, FABS), input);
        } else {
            return super.emitMathAbs(input);
        }
    }

    @Override
    public Value emitMathSqrt(Value input) {
        if (isVectorKind(input)) {
            return emitVectorUnary(FSQRT, input);
        } else {
            return super.emitMathSqrt(input);
        }
    }

    private Value emitVectorShift(AArch64ArithmeticOp op, Value a, Value b) {
        /*
         * Remember that, as specified in JLS-15.19, the value of the shift must be clamped between
         * 0 and 31/63. AArch64 gp instructions do this automatically, but on NEON this must be done
         * manually.
         */
        Variable result;
        ElementSize eSize = ElementSize.fromKind(a.getPlatformKind());
        int mask = eSize == ElementSize.DoubleWord ? 63 : 31;
        if (isConstantValue(b) && asConstant(b) instanceof PrimitiveConstant) {
            int clampSize = eSize == ElementSize.DoubleWord ? 64 : 32;
            int clampedShift = AArch64MacroAssembler.clampShiftAmt(clampSize, ((PrimitiveConstant) asConstant(b)).asLong());
            if (clampedShift == 0) {
                /* No shift: don't need to do anything. */
                return a;
            } else if (clampedShift >= eSize.bits()) {
                /*
                 * All bits are being shifted out.
                 *
                 * Note that only right shift is able to shift out all bits.
                 */
                result = getLIRGen().newVariable(LIRKind.combine(a));
                if (op == ASR) {
                    /*
                     * Need to set everything to the sign bit. Note the allowed shift range is (0,
                     * eSize].
                     */
                    getLIRGen().append(new AArch64ArithmeticOp.ASIMDBinaryConstOp(ASR, result, asAllocatable(a), JavaConstant.forLong(eSize.bits())));
                } else {
                    /* In this case the result will be "0" - optimize to a vector fill. */
                    getLIRGen().append(new AArch64ASIMDMove.ConstantVectorFillOp(result, JavaConstant.forLong(0)));
                }
            } else {
                /* Perform operation with constant shift. */
                result = getLIRGen().newVariable(LIRKind.combine(a));
                getLIRGen().append(new AArch64ArithmeticOp.ASIMDBinaryConstOp(op, result, asAllocatable(a), JavaConstant.forLong(clampedShift)));
            }
        } else if (!isVectorKind(b)) {
            assert b.getPlatformKind().getVectorLength() == 1 : b.getPlatformKind();
            LIRKind lirKind = LIRKind.combine(a);

            /* First make sure imm is between 0 <= shift < (eSize == 64 ? 64 : 32). */
            Value maskValue = new ConstantValue(b.getValueKind(), JavaConstant.forLong(mask));
            Value clampedShift = emitAnd(b, maskValue);

            /* Next, broadcast imm shift value to each element. */
            Value bVector = emitVectorFill(lirKind, clampedShift);

            /* Finally, perform binary operation. */
            result = emitVectorBinary(op, lirKind, a, bVector);
        } else {
            LIRKind lirKind = LIRKind.combine(a, b);
            Value maskValue = new ConstantValue(b.getValueKind(), SimdConstant.broadcast(JavaConstant.forPrimitiveInt(eSize.bits(), mask), b.getPlatformKind().getVectorLength()));
            Value clampedShift = emitVectorBinary(AND, b, maskValue);
            result = emitVectorBinary(op, lirKind, a, clampedShift);
        }
        return result;
    }

    @Override
    public Value emitXorFP(Value a, Value b) {
        if (isVectorKind(a)) {
            return emitXor(a, b);
        } else {
            return super.emitXorFP(a, b);
        }
    }

    @Override
    public Value emitShl(Value a, Value b) {
        if (isVectorKind(a)) {
            return emitVectorShift(LSL, a, b);
        } else {
            return super.emitShl(a, b);
        }
    }

    @Override
    public Value emitShr(Value a, Value b) {
        if (isVectorKind(a)) {
            return emitVectorShift(ASR, a, b);
        } else {
            return super.emitShr(a, b);
        }
    }

    @Override
    public Value emitUShr(Value a, Value b) {
        if (isVectorKind(a)) {
            return emitVectorShift(LSR, a, b);
        } else {
            return super.emitUShr(a, b);
        }
    }

    @Override
    public Value emitFusedMultiplyAdd(Value a, Value b, Value c) {
        if (isVectorKind(a)) {
            Variable result = getLIRGen().newVariable(LIRKind.combine(a, b, c));
            getLIRGen().append(new AArch64ArithmeticOp.ASIMDMultiplyAddSubOp(AArch64ArithmeticOp.FMADD, asAllocatable(result), asAllocatable(a), asAllocatable(b), asAllocatable(c)));
            return result;
        } else {
            return super.emitFusedMultiplyAdd(a, b, c);
        }
    }

    @Override
    public Value emitVectorPackedEquals(Value vectorA, Value vectorB) {
        assert isVectorNumericInteger(vectorA) && isVectorNumericInteger(vectorB) : vectorA + " " + vectorB;
        return emitVectorPackedComparison(Condition.EQ, vectorA, vectorB, false);
    }

    @Override
    public Value emitVectorPackedComparison(CanonicalCondition condition, Value vectorA, Value vectorB, boolean unorderedIsTrue) {
        return emitVectorPackedComparison(condition.asCondition(), vectorA, vectorB, unorderedIsTrue);
    }

    private Variable getVectorComparisonResult(Value input) {
        assert isVectorKind(input) : input.getPlatformKind();

        boolean isNumericInteger = isVectorNumericInteger(input);
        Variable result;
        if (isNumericInteger) {
            result = getLIRGen().newVariable(LIRKind.value(input.getPlatformKind()));
        } else {
            /*
             * Result of float vector compare should be considered an integer vector of the same
             * element size and length.
             */
            AArch64Kind vectorKind = ((AArch64Kind) input.getPlatformKind());
            AArch64Kind scalarKind = vectorKind.getScalar();
            assert scalarKind == AArch64Kind.SINGLE || scalarKind == AArch64Kind.DOUBLE : scalarKind;
            AArch64Kind intKind = scalarKind == AArch64Kind.SINGLE ? AArch64Kind.DWORD : AArch64Kind.QWORD;
            AArch64Kind newKind = ASIMDKind.getASIMDKind(intKind, vectorKind.getVectorLength());
            result = getLIRGen().newVariable(LIRKind.value(newKind));
        }
        return result;
    }

    private static boolean isZeroConstant(Value value) {
        if (isConstantValue(value) && asConstant(value) instanceof SimdConstant) {
            SimdConstant simdConstant = (SimdConstant) asConstant(value);
            return simdConstant.isDefaultForKind();
        }
        return false;
    }

    private boolean tryVectorPackedComparisonZero(Condition condition, Variable result, Value vectorA, Value vectorB, boolean unorderedIsTrue) {
        assert isVectorKind(vectorA) : vectorA.getPlatformKind();
        Condition testCondition;
        Value src;
        if (condition.isUnsigned()) {
            /*
             * It is possible to handle these cases comparing against zero, but I don't think it is
             * worth it - imagine this should be handled by canonicalization. Current thoughts:
             *
             * AE - This is always true (unless unorderedIsTrue is unset).
             *
             * AT - This is only true if src != 0
             *
             * BE - This is only true if src == 0
             *
             * BT - This is never true (unless unorderedIsTrue is set).
             *
             */
            return false;
        } else if (isZeroConstant(vectorB)) {
            testCondition = condition;
            src = vectorA;
        } else if (isZeroConstant(vectorA)) {
            testCondition = condition.mirror();
            src = vectorB;
        } else {
            return false;
        }

        if (isVectorNumericInteger(src)) {
            getLIRGen().append(new AArch64Compare.ASIMDCompareZeroOp(testCondition, result, asAllocatable(src)));
        } else {
            getLIRGen().append(new AArch64Compare.ASIMDFloatCompareZeroOp(testCondition, result, asAllocatable(src), unorderedIsTrue));
        }

        return true;
    }

    private Variable emitVectorPackedComparison(Condition condition, Value vectorA, Value vectorB, boolean unorderedIsTrue) {
        assert isVectorKind(vectorA) : "A must be a vactor graph " + vectorA;

        Variable result = getVectorComparisonResult(vectorA);
        /* First try to emit a comparison against zero. */
        if (!tryVectorPackedComparisonZero(condition, result, vectorA, vectorB, unorderedIsTrue)) {
            if (isVectorNumericInteger(vectorA)) {
                getLIRGen().append(new AArch64Compare.ASIMDCompareOp(condition, result, asAllocatable(vectorA), asAllocatable(vectorB)));
            } else {
                getLIRGen().append(AArch64Compare.generateASIMDFloatCompare(getLIRGen(), condition, result, asAllocatable(vectorA), asAllocatable(vectorB), unorderedIsTrue));
            }
        }

        return result;
    }

    /**
     * In AArch64 vector condition results are represented as per-element masks of all ones or
     * zeros. If the result kind has a different element size than the original condition, then the
     * mask must be narrowed/extended accordingly. Similar logic is used within
     * AMD64VectorArithmeticLIRGenerator#adjustMaskForBlend.
     */
    private Value adjustConditionMaskSize(Value mask, LIRKind result) {
        AArch64Kind maskKind = (AArch64Kind) mask.getPlatformKind();
        AArch64Kind resultKind = (AArch64Kind) result.getPlatformKind();
        assert maskKind.getVectorLength() == resultKind.getVectorLength() : maskKind + " " + resultKind;
        int maskBits = maskKind.getScalar().getSizeInBytes() * Byte.SIZE;
        int resultBits = resultKind.getScalar().getSizeInBytes() * Byte.SIZE;
        if (maskBits < resultBits) {
            return emitSignExtend(mask, maskBits, resultBits);
        } else if (maskBits > resultBits) {
            return emitNarrow(mask, resultBits);
        } else {
            // no adjustment needed
            return mask;
        }
    }

    public Variable emitVectorIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        AArch64Kind kind = (AArch64Kind) left.getPlatformKind();
        int vectorLength = kind.getVectorLength();
        if (vectorLength == 1) {
            return null;
        }
        assert isVectorNumericInteger(left) : left.getPlatformKind();

        // Future consideration: handle constants? Should be handled by canonicalization
        /*
         * First issue vector test (cmtst). Note that the behavior of cmtst produces the opposite
         * condition than what is expected by integer test:
         *
         * <code>for i in 0..n-1 do dst[i] = (src1[i] & src2[i]) == 0 ? 0 : -1</code>.
         *
         * This is corrected by switching the false and true values within the blend.
         */
        Value condition = getVectorComparisonResult(left);
        getLIRGen().append(AArch64ArithmeticOp.generateASIMDBinaryInstruction(TST, (Variable) condition, asAllocatable(left), asAllocatable(right)));
        Value tstTrueValue = falseValue;
        Value tstFalseValue = trueValue;
        // next adjust condition to result size
        condition = adjustConditionMaskSize(condition, LIRKind.mergeReferenceInformation(trueValue, falseValue));
        // finally, blend vector to produce result.
        return emitVectorBlend(tstFalseValue, tstTrueValue, condition);
    }

    public Variable emitVectorConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        if (cmpKind.getVectorLength() == 1) {
            return null;
        }
        assert cmpKind == left.getPlatformKind() : cmpKind + " " + left;
        assert left.getPlatformKind() == right.getPlatformKind() : left + " " + right;
        assert trueValue.getPlatformKind() == falseValue.getPlatformKind() : trueValue + " " + falseValue;

        // first issue compare
        Value condition = emitVectorPackedComparison(cond, left, right, unorderedIsTrue);
        // next adjust condition to result size
        condition = adjustConditionMaskSize(condition, LIRKind.mergeReferenceInformation(trueValue, falseValue));
        // finally, blend vector to produce result.
        return emitVectorBlend(falseValue, trueValue, condition);
    }

    @Override
    public Value emitVectorToBitMask(LIRKind resultKind, Value vector) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AArch64ASIMDMove.VectorToBitMask(getLIRGen(), result, asAllocatable(vector)));
        return result;
    }

    @Override
    public Value emitVectorGather(LIRKind resultKind, Value base, Value offsets) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Value emitVectorSimpleConcat(LIRKind resultKind, Value low, Value high) {
        AllocatableValue lowVal = asAllocatable(low);
        AllocatableValue highVal = asAllocatable(high);
        GraalError.guarantee(AArch64ASIMDMove.VectorConcat.isValidConcat(resultKind.getPlatformKind(), highVal, lowVal), "Invalid vector concat");

        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AArch64ASIMDMove.VectorConcat(result, highVal, lowVal));

        return result;
    }

    @Override
    public Variable emitVectorBlend(Value falseValue, Value trueValue, Value mask) {
        assert isVectorKind(falseValue) : falseValue.getPlatformKind();
        PlatformKind trueKind = trueValue.getPlatformKind();
        PlatformKind falseKind = falseValue.getPlatformKind();
        int maskSize = mask.getPlatformKind().getSizeInBytes();
        assert falseKind == trueKind : falseKind + " vs " + trueKind;
        assert trueKind.getSizeInBytes() == maskSize : trueKind.getSizeInBytes() + " " + maskSize;

        Variable result = getLIRGen().newVariable(LIRKind.mergeReferenceInformation(trueValue, falseValue));
        getLIRGen().append(new AArch64ControlFlow.ASIMDCondMoveOp(result, asAllocatable(mask), asAllocatable(trueValue), asAllocatable(falseValue)));
        return result;
    }

    @Override
    public Variable emitVectorBlend(Value falseValue, Value trueValue, boolean[] selector) {
        AArch64Kind baseKind = ((AArch64Kind) falseValue.getPlatformKind()).getScalar();
        int kindIndex = CodeUtil.log2(baseKind.getSizeInBytes());
        JavaKind maskJavaKind = MASK_JAVA_KINDS[kindIndex];
        SimdStamp simdMaskStamp = SimdStamp.broadcast(IntegerStamp.create(maskJavaKind.getBitCount(), -1, 0), selector.length);
        LIRKind simdMaskKind = getLIRGen().getLIRKind(simdMaskStamp);
        Constant selectorConstant = SimdConstant.forBitmaskBlendSelector(selector, maskJavaKind);
        Value selectorValue = getLIRGen().emitConstant(simdMaskKind, selectorConstant);
        return emitVectorBlend(falseValue, trueValue, selectorValue);
    }

    public Variable emitVectorByteSwap(Value input) {
        AArch64Kind kind = (AArch64Kind) input.getPlatformKind();
        if (kind.getVectorLength() == 1) {
            return null;
        }
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AArch64ByteSwap.ASIMDByteSwapOp(result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal, boolean canBeNaN, boolean canOverflow) {
        if (isVectorKind(inputVal)) {
            int length = inputVal.getPlatformKind().getVectorLength();
            PlatformKind resultPlatformKind = ASIMDKind.getASIMDKind(getFloatConvertResultKind(op), length);
            LIRKind resultLIRKind = LIRKind.combine(inputVal).changeType(resultPlatformKind);
            Variable result = getLIRGen().newVariable(resultLIRKind);
            getLIRGen().append(new AArch64Convert.ASIMDFloatConvertOp(op, result, asAllocatable(inputVal)));
            return result;
        } else {
            return super.emitFloatConvert(op, inputVal, canBeNaN, canOverflow);
        }
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        AArch64Kind from = (AArch64Kind) inputVal.getPlatformKind();
        if (from.getVectorLength() > 1) {
            return new CastValue(to, getLIRGen().asAllocatable(inputVal));
        } else {
            return super.emitReinterpret(to, inputVal);
        }
    }

    private static LIRKind getChangedSizeVectorLIRKind(Value input, int resultBitSize) {
        int length = input.getPlatformKind().getVectorLength();
        PlatformKind resultPlatformKind;
        switch (resultBitSize) {
            case 8:
                resultPlatformKind = ASIMDKind.getASIMDKind(AArch64Kind.BYTE, length);
                break;
            case 16:
                resultPlatformKind = ASIMDKind.getASIMDKind(AArch64Kind.WORD, length);
                break;
            case 32:
                resultPlatformKind = ASIMDKind.getASIMDKind(AArch64Kind.DWORD, length);
                break;
            case 64:
                resultPlatformKind = ASIMDKind.getASIMDKind(AArch64Kind.QWORD, length);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(resultBitSize); // ExcludeFromJacocoGeneratedReport
        }
        return LIRKind.combine(input).changeType(resultPlatformKind);
    }

    @Override
    public Value emitNarrow(Value inputVal, int toBits) {
        if (isVectorKind(inputVal)) {
            int fromBits = ((AArch64Kind) inputVal.getPlatformKind()).getScalar().getSizeInBytes() * Byte.SIZE;
            assert fromBits >= toBits : "From " + fromBits + " to " + toBits + " for input " + inputVal;
            if (fromBits == toBits) {
                // No narrowing needed.
                return inputVal;
            }
            LIRKind resultLIRKind = getChangedSizeVectorLIRKind(inputVal, toBits);
            Variable result = getLIRGen().newVariable(resultLIRKind);
            getLIRGen().append(new AArch64Convert.ASIMDNarrowOp(result, asAllocatable(inputVal)));
            return result;
        } else {
            return super.emitNarrow(inputVal, toBits);
        }
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits, boolean requiresExplicitZeroExtend, boolean requiresLIRKindChange) {
        if (isVectorKind(inputVal)) {
            assert fromBits == 8 || fromBits == 16 || fromBits == 32 || fromBits == 64 : fromBits;
            assert toBits >= fromBits && (toBits == 8 || toBits == 16 || toBits == 32 || toBits == 64) : fromBits + " " + toBits;
            if (fromBits == toBits) {
                return inputVal;
            }
            LIRKind resultLIRKind = getChangedSizeVectorLIRKind(inputVal, toBits);
            Variable result = getLIRGen().newVariable(resultLIRKind);
            getLIRGen().append(new AArch64Convert.ASIMDZeroExtendOp(result, asAllocatable(inputVal)));
            return result;
        } else {
            return super.emitZeroExtend(inputVal, fromBits, toBits, requiresExplicitZeroExtend, requiresLIRKindChange);
        }
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        if (isVectorKind(inputVal)) {
            assert fromBits == 8 || fromBits == 16 || fromBits == 32 || fromBits == 64 : fromBits;
            assert toBits >= fromBits && (toBits == 8 || toBits == 16 || toBits == 32 || toBits == 64) : toBits + " " + fromBits;
            if (fromBits == toBits) {
                return inputVal;
            }
            LIRKind resultLIRKind = getChangedSizeVectorLIRKind(inputVal, toBits);
            Variable result = getLIRGen().newVariable(resultLIRKind);
            getLIRGen().append(new AArch64Convert.ASIMDSignExtendOp(result, asAllocatable(inputVal)));
            return result;
        } else {
            return super.emitSignExtend(inputVal, fromBits, toBits);
        }
    }

    @Override
    protected Value emitMultiplyAddSub(AArch64ArithmeticOp op, Value a, Value b, Value c) {
        if (isVectorKind(a)) {
            assert a.getPlatformKind() == b.getPlatformKind() : a.getPlatformKind() + " " + b.getPlatformKind();
            Variable result;
            if (op == SMADDL || op == SMSUBL) {
                AArch64Kind aKind = ((AArch64Kind) a.getPlatformKind()).getScalar();
                AArch64Kind cKind = ((AArch64Kind) c.getPlatformKind()).getScalar();
                assert aKind.getSizeInBytes() * 2 == cKind.getSizeInBytes() : aKind + " bytes=" + aKind.getSizeInBytes() + " " + cKind;
                result = getLIRGen().newVariable(LIRKind.combine(c));
            } else {
                assert a.getPlatformKind() == c.getPlatformKind() : a.getPlatformKind() + " " + c.getPlatformKind();
                result = getLIRGen().newVariable(LIRKind.combine(a, b, c));
            }
            getLIRGen().append(new AArch64ArithmeticOp.ASIMDMultiplyAddSubOp(op, result, (asAllocatable(a)), (asAllocatable(b)), asAllocatable(c)));
            return result;
        } else {
            return super.emitMultiplyAddSub(op, a, b, c);
        }
    }

    @Override
    public Value emitVectorPermute(LIRKind resultKind, Value source, Value indices) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AArch64PermuteOp.ASIMDPermuteOp(getLIRGen(), result, asAllocatable(source), asAllocatable(indices)));
        return result;
    }

    @Override
    public Value emitMoveOpMaskToInteger(LIRKind resultKind, Value mask, int maskLen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Value emitMoveIntegerToOpMask(LIRKind resultKind, Value mask) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Variable emitVectorCompress(LIRKind resultKind, Value source, Value mask) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Variable emitVectorExpand(LIRKind resultKind, Value source, Value mask) {
        throw new UnsupportedOperationException();
    }
}
