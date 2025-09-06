/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMILPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPSHUFD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VFMADD231PD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VFMADD231PS;
import static jdk.graal.compiler.lir.LIRValueUtil.asConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isConstantValue;
import static jdk.graal.compiler.vector.lir.amd64.AMD64VectorNodeMatchRules.getRegisterSize;

import java.util.function.BiFunction;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.amd64.AMD64ArithmeticLIRGenerator;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil.Signedness;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64ConvertFloatToIntegerOp;
import jdk.graal.compiler.lir.amd64.AMD64Ternary;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorBinary.AVXBinaryConstOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorMove;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorMove.AVXMoveToIntOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorShuffle;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary.AVXConvertToFloatOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary.AVXUnaryOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary.AVXUnaryRVMOp;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Common base class for generating LIR for AMD64 vector operations. Basic arithmetic is handled
 * here, operations with masks are handled by subclasses.
 *
 * When adding or changing the instructions used in an operation, it might be necessary to adapt the
 * instruction set tables in jdk.graal.compiler.vector.architecture.amd64.VectorAMD64 as well.
 */
public abstract class AMD64VectorArithmeticLIRGenerator extends AMD64ArithmeticLIRGenerator implements VectorLIRGeneratorTool {

    public static AMD64VectorArithmeticLIRGenerator create(AllocatableValue nullRegisterValue, Architecture arch) {
        return AMD64BaseAssembler.supportsFullAVX512(((AMD64) arch).getFeatures()) ? new AMD64AVX512ArithmeticLIRGenerator(nullRegisterValue)
                        : new AMD64SSEAVXArithmeticLIRGenerator(nullRegisterValue);
    }

    protected AMD64VectorArithmeticLIRGenerator(AllocatableValue nullRegisterValue) {
        super(nullRegisterValue);
    }

    public AMD64SIMDInstructionEncoding getSimdEncoding() {
        return simdEncoding;
    }

    protected abstract Value emitIntegerMinMax(Value a, Value b, AMD64MathMinMaxFloatOp minmaxop, Signedness signedness);

    public abstract Variable emitVectorOpMaskTestMove(Value left, boolean negateLeft, Value right, Value trueValue, Value falseValue);

    public abstract Variable emitVectorOpMaskOrTestMove(Value left, Value right, boolean allZeros, Value trueValue, Value falseValue);

    /**
     * Emit a vectorized integer test move operation. Returns non-{@code null} if the operation was
     * emitted, {@code null} if the values to be processed are not vector values. In this case, it
     * is the caller's responsibility to emit scalar code.
     */
    public abstract Variable emitVectorIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue);

    /**
     * Emit a vectorized or floating-point conditional move operation. Returns non-{@code null} if
     * the operation was emitted, {@code null} if the values to be processed are not vector or
     * floating-point values. In this case, it is the caller's responsibility to emit scalar code.
     */
    public abstract Variable emitVectorConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    /**
     * Lift an integer conditional move operation to floating point values. The
     * {@code scalarSelectOperation} must be an integer-valued function
     *
     * <pre>
     * f(x, y) = (someCondition ? x : y)
     * </pre>
     * <p>
     * which this method uses to implement
     *
     * <pre>
     * (someCondition ? trueValue : falseValue)
     * </pre>
     * <p>
     * where {@code trueValue} and {@code falseValue} are scalar floating-point values.
     * </p>
     * <p>
     * The base AMD64 arithmetic generator doesn't handle conditional moves of floating-point values
     * because there is no instruction for these (and they are never generated in the graph). When
     * vectorizing, we can get these operations in the tail consumer, so we must treat them here.
     */
    public abstract Variable emitFloatingPointConditionalMove(BiFunction<Value, Value, Variable> scalarSelectOperation, Value trueValue, Value falseValue);

    public abstract Value emitConstShuffleBytes(LIRKind resultKind, Value vector, byte[] selector);

    protected abstract void emitVectorBroadcast(AMD64Kind elementKind, Variable result, Value input);

    protected abstract VexMoveOp getScalarFloatLoad(AMD64Kind kind);

    protected Variable emitUnary(VexRMOp op, Value a) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a));
        getLIRGen().append(new AVXUnaryOp(op, getRegisterSize(result), result, asAllocatable(a)));
        return result;
    }

    protected Variable emitUnary(VexRVMOp op, Value a) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a));
        getLIRGen().append(new AVXUnaryRVMOp(op, getRegisterSize(result), result, asAllocatable(a), simdEncoding));
        return result;
    }

    protected Variable emitVectorBinary(VexRVMOp op, Value a, Value b) {
        return emitVectorBinary(LIRKind.combine(a, b), op, a, b);
    }

    protected Variable emitVectorBinary(LIRKind resultKind, VexRVMOp op, Value a, Value b) {
        if (b instanceof ConstantValue && ((ConstantValue) b).getConstant() instanceof SimdConstant) {
            SimdConstant simd = (SimdConstant) ((ConstantValue) b).getConstant();

            if (simd.isDefaultForKind()) {
                Variable bInRegister = getLIRGen().newVariable(b.getValueKind());
                getLIRGen().append(new AVXClearVectorConstant(bInRegister, simd));
                return super.emitBinary(resultKind, op, a, bInRegister);
            } else if (SimdConstant.isAllOnes(simd)) {
                Variable bInRegister = getLIRGen().newVariable(b.getValueKind());
                getLIRGen().append(new AVXAllOnesOp(bInRegister, simd));
                return super.emitBinary(resultKind, op, a, bInRegister);
            } else {
                Variable result = getLIRGen().newVariable(resultKind);
                getLIRGen().append(new AVXBinarySimdConstantVectorOp(op, getRegisterSize(result), result, asAllocatable(a), (ConstantValue) b));
                return result;
            }
        }
        return super.emitBinary(resultKind, op, a, b);
    }

    protected AMD64 getArchitecture() {
        return (AMD64) getLIRGen().target().arch;
    }

    public boolean supports(CPUFeature feature) {
        TargetDescription target = getLIRGen().target();
        return ((AMD64) target.arch).getFeatures().contains(feature);
    }

    protected Variable emitShift(VexShiftOp op, Value a, Value b) {
        if (isConstantValue(b) && asConstant(b) instanceof PrimitiveConstant) {
            int bInt = ((PrimitiveConstant) asConstant(b)).asInt();
            assert (bInt & 0xFF) == bInt : bInt;
            Variable result = getLIRGen().newVariable(LIRKind.combine(a));
            getLIRGen().append(new AVXBinaryConstOp(op, getRegisterSize(result), result, asAllocatable(a), bInt));
            return result;
        } else {
            Variable bInXmm = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V128_DWORD));
            getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(bInXmm, getLIRGen().asAllocatable(b), simdEncoding));
            return emitVectorBinary(op, a, bInXmm);
        }
    }

    protected AllocatableValue emitConvertOp(LIRKind kind, VexRMOp op, Value a, boolean narrow) {
        Variable result = getLIRGen().newVariable(kind);

        /*
         * If the convert is a narrowing convert (e.g. D2F), we have to encode with the argument
         * size instead of the result size.
         */
        AVXSize size = narrow ? getRegisterSize(a) : getRegisterSize(result);
        getLIRGen().append(new AVXUnaryOp(op, size, result, asAllocatable(a)));
        return result;
    }

    protected AllocatableValue emitConvertToIntOp(LIRKind kind, VexMoveOp op, Value a) {
        Variable result = getLIRGen().newVariable(kind);
        getLIRGen().append(new AVXMoveToIntOp(op, result, asAllocatable(a)));
        return result;
    }

    protected Value emitConvertOp(PlatformKind kind, VexRMOp opcode, Value inputVal) {
        return emitConvertOp(kind, opcode, inputVal, false);
    }

    protected Value emitConvertOp(PlatformKind kind, VexRMOp opcode, Value inputVal, boolean narrow) {
        return emitConvertOp(LIRKind.combine(inputVal).changeType(kind), opcode, inputVal, narrow);
    }

    protected Value emitConvertOp(PlatformKind kind, VexRVMConvertOp opcode, Value inputVal, Signedness signedness) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(inputVal).changeType(kind));
        getLIRGen().append(new AVXConvertToFloatOp(opcode, result, asAllocatable(inputVal), simdEncoding, signedness));
        return result;
    }

    private Variable emitVectorLoad(LIRKind kind, Value address, LIRFrameState state) {
        AMD64Kind avxKind = (AMD64Kind) kind.getPlatformKind();
        AMD64AddressValue loadAddress = getAMD64LIRGen().asAddressValue(address);
        Variable result = getLIRGen().newVariable(kind);
        VexMoveOp op = AMD64VectorMove.getVectorMemMoveOp(avxKind, simdEncoding);
        getLIRGen().append(new AMD64VectorMove.VectorLoadOp(AVXKind.getRegisterSize(avxKind), op, result, loadAddress, state));
        return result;
    }

    @Override
    public Variable emitLoad(LIRKind lirKind, Value address, LIRFrameState state, MemoryOrderMode memoryOrder, MemoryExtendKind extendKind) {
        assert extendKind.isNotExtended() : "Must not be extended " + extendKind;
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            assert !MemoryOrderMode.ordersMemoryAccesses(memoryOrder) : memoryOrder + " " + address + " " + state;
            return emitVectorLoad(lirKind, address, state);
        } else if (kind.isXMM()) {
            AMD64AddressValue loadAddress = getAMD64LIRGen().asAddressValue(address);

            VexMoveOp op = getScalarFloatLoad(kind);

            Variable result = getLIRGen().newVariable(lirKind);
            getLIRGen().append(new AMD64VectorMove.VectorLoadOp(AVXSize.XMM, op, result, loadAddress, state));
            return result;
        }

        return super.emitLoad(lirKind, address, state, memoryOrder, extendKind);
    }

    @Override
    protected void emitStoreConst(AMD64Kind kind, AMD64AddressValue address, ConstantValue value, LIRFrameState state) {
        if (kind.isXMM()) {
            emitStore(kind, address, asAllocatable(value), state);
        } else {
            super.emitStoreConst(kind, address, value, state);
        }
    }

    @Override
    public Variable emitFusedMultiplyAdd(Value a, Value b, Value c) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            Variable result = getLIRGen().newVariable(LIRKind.combine(a, b, c));
            switch (kind.getScalar()) {
                case SINGLE -> getLIRGen().append(new AMD64Ternary.ThreeOp(VFMADD231PS.encoding(simdEncoding), getRegisterSize(a), result, asAllocatable(c), asAllocatable(a), asAllocatable(b)));
                case DOUBLE -> getLIRGen().append(new AMD64Ternary.ThreeOp(VFMADD231PD.encoding(simdEncoding), getRegisterSize(a), result, asAllocatable(c), asAllocatable(a), asAllocatable(b)));
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar());
            }
            return result;
        } else {
            return super.emitFusedMultiplyAdd(a, b, c);
        }
    }

    protected AllocatableValue moveIntegerToVectorRegister(AMD64Kind kind, ValueKind<?> singleValueKind, AllocatableValue value, boolean forceXmmResult) {
        AllocatableValue vector;
        switch (kind.getScalar()) {
            case BYTE -> {
                vector = getLIRGen().newVariable(forceXmmResult ? singleValueKind.changeType(AMD64Kind.V128_BYTE) : singleValueKind.changeType(kind));
                getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(vector, value, simdEncoding));
            }
            case WORD -> {
                vector = getLIRGen().newVariable(forceXmmResult ? singleValueKind.changeType(AMD64Kind.V128_WORD) : singleValueKind.changeType(kind));
                getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(vector, value, simdEncoding));
            }
            case DWORD -> {
                vector = getLIRGen().newVariable(forceXmmResult ? singleValueKind.changeType(AMD64Kind.V128_DWORD) : singleValueKind.changeType(kind));
                getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(vector, value, simdEncoding));
            }
            case QWORD -> {
                vector = getLIRGen().newVariable(forceXmmResult ? singleValueKind.changeType(AMD64Kind.V128_QWORD) : singleValueKind.changeType(kind));
                getLIRGen().append(new AMD64VectorShuffle.LongToVectorOp(vector, value, simdEncoding));
            }
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
        }
        return vector;
    }

    @Override
    public Value emitSimdFromScalar(LIRKind lirKind, Value input) {
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();
        AllocatableValue value = asAllocatable(input);
        ValueKind<?> singleValueKind = input.getValueKind();

        // Move the scalar to a vector register.
        return switch (kind.getScalar()) {
            case BYTE, WORD, DWORD, QWORD ->
                // Integer moves zero the rest of the register, which is the semantics we want.
                moveIntegerToVectorRegister(kind, singleValueKind, value, false);

            case SINGLE, DOUBLE ->
                // We could zero a register and blend it with the input value.
                throw GraalError.unimplemented("single float to vector"); // ExcludeFromJacocoGeneratedReport

            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public Value emitVectorCut(int start, int length, Value vector) {
        AMD64Kind oldKind = (AMD64Kind) vector.getPlatformKind();
        assert 0 <= start && (start + length) <= oldKind.getVectorLength() : "vector cut out of bounds: " + oldKind + "[" + start + ".." + (start + length - 1) + "]";

        AMD64Kind elementKind = oldKind.getScalar();
        if (length == 1 && elementKind.isInteger()) {
            AMD64Kind resultKind = elementKind;
            if (resultKind == AMD64Kind.BYTE || resultKind == AMD64Kind.WORD) {
                resultKind = AMD64Kind.DWORD;
            }
            ValueKind<?> resultValueKind = getLIRGen().toRegisterKind(vector.getValueKind(LIRKind.class).changeType(resultKind));
            AllocatableValue result = getLIRGen().newVariable(resultValueKind);
            AllocatableValue source = getLIRGen().asAllocatable(vector);

            GraalError.guarantee(start < 16 / elementKind.getSizeInBytes(), "unsupported vector cut operation %d", start);
            switch (elementKind) {
                case BYTE -> getLIRGen().append(new AMD64VectorShuffle.ExtractByteOp(result, source, start, simdEncoding));
                case WORD -> getLIRGen().append(new AMD64VectorShuffle.ExtractShortOp(result, source, start, simdEncoding));
                case DWORD -> getLIRGen().append(new AMD64VectorShuffle.ExtractIntOp(result, source, start, simdEncoding));
                case QWORD -> getLIRGen().append(new AMD64VectorShuffle.ExtractLongOp(result, source, start, simdEncoding));
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(elementKind); // ExcludeFromJacocoGeneratedReport
            }
            return result;
        } else {
            AMD64Kind newKind = AVXKind.getAVXKind(oldKind.getScalar(), length);
            if (oldKind == newKind && start == 0) {
                return vector;
            }
            ValueKind<?> resultKind = vector.getValueKind().changeType(newKind);
            if (start == 0) {
                return new CastValue(resultKind, getLIRGen().asAllocatable(vector));
            }
            Variable result = getLIRGen().newVariable(resultKind);

            int resultBits = newKind.getSizeInBytes() * Byte.SIZE;
            int bitOffset = start * elementKind.getSizeInBytes() * Byte.SIZE;
            assert start % length == 0 : start + " " + length;
            switch (resultBits) {
                case 32 -> {
                    if (bitOffset < 128) {
                        AMD64Assembler.VexRMIOp op = elementKind.isInteger() ? VPSHUFD : VPERMILPS;
                        getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(op.encoding(simdEncoding), result, asAllocatable(vector), bitOffset / 32));
                    } else if (bitOffset % 128 == 0) {
                        getLIRGen().append(new AMD64VectorShuffle.Extract128Op(result, asAllocatable(vector), bitOffset / 128, simdEncoding));
                    } else {
                        GraalError.guarantee(bitOffset == 192 && supports(CPUFeature.AVX2), "");
                        AMD64Assembler.VexRMIOp op = elementKind.isInteger() ? VPERMQ : VPERMPD;
                        getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(op.encoding(simdEncoding), result, asAllocatable(vector), bitOffset / 64));
                    }
                }
                case 64 -> {
                    if (bitOffset == 64) {
                        AMD64Assembler.VexRMIOp op = elementKind.isInteger() ? VPSHUFD : VPERMILPS;
                        getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(op.encoding(simdEncoding), result, asAllocatable(vector), 0b1110));
                    } else if (bitOffset % 128 == 0) {
                        getLIRGen().append(new AMD64VectorShuffle.Extract128Op(result, asAllocatable(vector), bitOffset / 128, simdEncoding));
                    } else {
                        GraalError.guarantee(bitOffset == 192 && supports(CPUFeature.AVX2), "");
                        AMD64Assembler.VexRMIOp op = elementKind.isInteger() ? VPERMQ : VPERMPD;
                        getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(op.encoding(simdEncoding), result, asAllocatable(vector), bitOffset / 64));
                    }
                }
                case 128 -> getLIRGen().append(new AMD64VectorShuffle.Extract128Op(result, getLIRGen().asAllocatable(vector), bitOffset / 128, simdEncoding));
                case 256 -> getLIRGen().append(new AMD64VectorShuffle.Extract256Op(result, getLIRGen().asAllocatable(vector), bitOffset / 256));
                default -> throw GraalError.shouldNotReachHere("unsupported vector cut: old kind " + oldKind + ", new kind " + newKind + ", offset " + start + ", length " + length); // ExcludeFromJacocoGeneratedReport
            }
            return result;
        }
    }

    @Override
    public Value emitVectorInsert(int offset, Value vector, Value val) {
        Variable result = getLIRGen().newVariable(vector.getValueKind());
        getLIRGen().append(new AMD64VectorShuffle.InsertOp(result, asAllocatable(vector), asAllocatable(val), offset, simdEncoding));
        return result;
    }

    @Override
    public Value emitVectorSimpleConcat(LIRKind resultKind, Value low, Value high) {
        AllocatableValue result = getLIRGen().asAllocatable(getLIRGen().newVariable(resultKind));

        Value opX = getLIRGen().asAllocatable(low);
        Value opY = getLIRGen().asAllocatable(high);

        int xBytes = opX.getPlatformKind().getSizeInBytes();
        int yBytes = opY.getPlatformKind().getSizeInBytes();

        if (xBytes == 16 && yBytes == 16) {
            getLIRGen().append(new AMD64VectorShuffle.Insert128Op(result, getLIRGen().asAllocatable(opX), getLIRGen().asAllocatable(opY), 1, simdEncoding));
        } else if (supports(CPUFeature.AVX512F) && xBytes == 32 && yBytes == 32) {
            getLIRGen().append(new AMD64VectorShuffle.Insert256Op(result, getLIRGen().asAllocatable(opX), getLIRGen().asAllocatable(opY), 1));
        } else {
            throw GraalError.unimplemented("concatenation not yet supported " + opX + " " + opY); // ExcludeFromJacocoGeneratedReport
        }

        return result;
    }

    /**
     * Emit a scalar floating point to integer conversion that needs fixup code to adjust the result
     * to Java semantics.
     */
    protected AllocatableValue emitFloatConvertWithFixup(AMD64Kind kind, VexRMOp op, Value input, boolean canBeNaN, boolean canOverflow, boolean narrow, Signedness signedness) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input).changeType(kind));
        /*
         * If the convert is a narrowing convert (e.g. D2F), we have to encode with the argument
         * size instead of the result size.
         */
        AVXSize size = narrow ? getRegisterSize(input) : getRegisterSize(result);
        AMD64ConvertFloatToIntegerOp.OpcodeEmitter emitter = (crb, masm, dst, src) -> op.emit(masm, size, dst, src);
        getLIRGen().append(new AMD64ConvertFloatToIntegerOp(getLIRGen(), emitter, result, input, canBeNaN, canOverflow, signedness));
        return result;
    }

    @Override
    public Value emitMathMax(Value a, Value b) {
        return emitMathMinMax(a, b, AMD64MathMinMaxFloatOp.Max);
    }

    @Override
    public Value emitMathMin(Value a, Value b) {
        return emitMathMinMax(a, b, AMD64MathMinMaxFloatOp.Min);
    }

    @Override
    public Value emitMathUnsignedMax(Value a, Value b) {
        return emitIntegerMinMax(a, b, AMD64MathMinMaxFloatOp.Max, Signedness.UNSIGNED);
    }

    @Override
    public Value emitMathUnsignedMin(Value a, Value b) {
        return emitIntegerMinMax(a, b, AMD64MathMinMaxFloatOp.Min, Signedness.UNSIGNED);
    }

    public Variable emitReverseBytes(Value input) {
        AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
        assert kind.getVectorLength() > 1 : "expected vector to reverse";

        int vectorSize = kind.getSizeInBytes();
        int registerSize = AVXKind.getRegisterSize(kind).getBytes();
        assert vectorSize <= registerSize : "trying to fit " + registerSize + " byte vector into " + AVXKind.getRegisterSize(kind) + " register";

        // build selector for shuffle operation
        byte[] selector = new byte[registerSize];
        // reverse bytes per element
        int selectorIdx = 0;
        int elementSize = kind.getScalar().getSizeInBytes();
        for (int i = 0; i < vectorSize / elementSize; i++) {
            for (int b = elementSize - 1; b >= 0; --b) {
                selector[selectorIdx++] = (byte) (b + (i * elementSize));
            }
        }
        // fill the remaining bytes with -1 to fit the register size
        for (; selectorIdx < selector.length; selectorIdx++) {
            selector[selectorIdx] = -1;
        }

        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(asAllocatable(result), asAllocatable(input), simdEncoding, selector));
        return result;
    }

    @Override
    public Value emitVectorPermute(LIRKind resultKind, Value source, Value indices) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(AMD64VectorShuffle.PermuteOp.create(getAMD64LIRGen(), result, asAllocatable(source), asAllocatable(indices), getSimdEncoding()));
        return result;
    }

    /**
     * Do a slice operation, see
     * {@code jdk.incubator.vector.Vector<E>::slice(int, jdk.incubator.vector.Vector<E>)}.
     */
    public Value emitVectorSlice(LIRKind resultKind, Value src1, Value src2, int origin) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AMD64VectorShuffle.SliceOp(getAMD64LIRGen(), result, asAllocatable(src1), asAllocatable(src2), origin, getSimdEncoding()));
        return result;
    }
}
