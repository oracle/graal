/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.amd64;

import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.AND;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.CMP;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.OR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SUB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.NEG;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.NOT;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.BSF;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.BSR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.LZCNT;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOV;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSX;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOVZX;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOVZXB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.POPCNT;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.TEST;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.TESTB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.TZCNT;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64Shift.ROL;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64Shift.ROR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64Shift.SAR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64Shift.SHL;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64Shift.SHR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEOp.MOVSD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEOp.MOVSS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VADDSD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VADDSS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VDIVSD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VDIVSS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VFMADD231SD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VFMADD231SS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMULSD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMULSS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VORPD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VORPS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBSD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBSS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMROp.VBLENDVPD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMROp.VBLENDVPS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMROp.VPBLENDVB;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.BYTE;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PS;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.SD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.SS;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.WORD;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static org.graalvm.compiler.lir.amd64.AMD64Arithmetic.DREM;
import static org.graalvm.compiler.lir.amd64.AMD64Arithmetic.FREM;
import static org.graalvm.compiler.lir.amd64.vector.AMD64VectorUnary.FloatPointClassTestOp.NEG_INF;
import static org.graalvm.compiler.lir.amd64.vector.AMD64VectorUnary.FloatPointClassTestOp.POS_INF;

import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MROp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64Shift;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEMROp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexGeneralPurposeRMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexGeneralPurposeRVMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMROp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.memory.MemoryExtendKind;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64Arithmetic.FPDivRemOp;
import org.graalvm.compiler.lir.amd64.AMD64ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.amd64.AMD64Binary;
import org.graalvm.compiler.lir.amd64.AMD64BinaryConsumer;
import org.graalvm.compiler.lir.amd64.AMD64BitSwapOp;
import org.graalvm.compiler.lir.amd64.AMD64ClearRegisterOp;
import org.graalvm.compiler.lir.amd64.AMD64FloatToHalfFloatOp;
import org.graalvm.compiler.lir.amd64.AMD64HalfFloatToFloatOp;
import org.graalvm.compiler.lir.amd64.AMD64MathCopySignOp;
import org.graalvm.compiler.lir.amd64.AMD64MathCosOp;
import org.graalvm.compiler.lir.amd64.AMD64MathExpOp;
import org.graalvm.compiler.lir.amd64.AMD64MathLog10Op;
import org.graalvm.compiler.lir.amd64.AMD64MathLogOp;
import org.graalvm.compiler.lir.amd64.AMD64MathPowOp;
import org.graalvm.compiler.lir.amd64.AMD64MathSignumOp;
import org.graalvm.compiler.lir.amd64.AMD64MathSinOp;
import org.graalvm.compiler.lir.amd64.AMD64MathTanOp;
import org.graalvm.compiler.lir.amd64.AMD64Move;
import org.graalvm.compiler.lir.amd64.AMD64MulDivOp;
import org.graalvm.compiler.lir.amd64.AMD64NormalizedUnsignedCompareOp;
import org.graalvm.compiler.lir.amd64.AMD64RoundFloatToIntegerOp;
import org.graalvm.compiler.lir.amd64.AMD64ShiftOp;
import org.graalvm.compiler.lir.amd64.AMD64SignExtendOp;
import org.graalvm.compiler.lir.amd64.AMD64Ternary;
import org.graalvm.compiler.lir.amd64.AMD64Unary;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorBinary;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorBinary.AVXBinaryConstFloatOp;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorBinary.AVXBinaryOp;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorBlend;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorFloatCompareOp;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorUnary;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.MemoryBarriers;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * This class implements the AMD64 specific portion of the LIR generator.
 */
public class AMD64ArithmeticLIRGenerator extends ArithmeticLIRGenerator implements AMD64ArithmeticLIRGeneratorTool {

    private static final RegisterValue RCX_I = AMD64.rcx.asValue(LIRKind.value(AMD64Kind.DWORD));

    public AMD64ArithmeticLIRGenerator(AllocatableValue nullRegisterValue) {
        this.nullRegisterValue = nullRegisterValue;
    }

    private final AllocatableValue nullRegisterValue;

    @Override
    public Variable emitNegate(Value inputVal, boolean setFlags) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        boolean isAvx = supportAVX();
        switch ((AMD64Kind) input.getPlatformKind()) {
            case DWORD:
                getLIRGen().append(new AMD64Unary.MOp(NEG, DWORD, result, input));
                break;
            case QWORD:
                getLIRGen().append(new AMD64Unary.MOp(NEG, QWORD, result, input));
                break;
            case SINGLE:
                JavaConstant floatMask = JavaConstant.forFloat(Float.intBitsToFloat(0x80000000));
                if (isAvx) {
                    getLIRGen().append(new AVXBinaryOp(VXORPS, getRegisterSize(result), result, asAllocatable(input), asAllocatable(getLIRGen().emitJavaConstant(floatMask))));
                } else {
                    getLIRGen().append(new AMD64Binary.DataTwoOp(SSEOp.XOR, PS, result, input, floatMask, PS.getBytes()));
                }
                break;
            case DOUBLE:
                JavaConstant doubleMask = JavaConstant.forDouble(Double.longBitsToDouble(0x8000000000000000L));
                if (isAvx) {
                    getLIRGen().append(new AVXBinaryOp(VXORPD, getRegisterSize(result), result, asAllocatable(input), asAllocatable(getLIRGen().emitJavaConstant(doubleMask))));
                } else {
                    getLIRGen().append(new AMD64Binary.DataTwoOp(SSEOp.XOR, PD, result, input, doubleMask, PD.getBytes()));
                }
                break;
            default:
                throw GraalError.shouldNotReachHere(input.getPlatformKind().toString()); // ExcludeFromJacocoGeneratedReport
        }
        return result;
    }

    @Override
    public Variable emitNot(Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case DWORD:
                getLIRGen().append(new AMD64Unary.MOp(NOT, DWORD, result, input));
                break;
            case QWORD:
                getLIRGen().append(new AMD64Unary.MOp(NOT, QWORD, result, input));
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(input.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
        return result;
    }

    private Variable emitBinary(LIRKind resultKind, AMD64BinaryArithmetic op, OperandSize size, boolean commutative, Value a, Value b, boolean setFlags) {
        if (isJavaConstant(b)) {
            return emitBinaryConst(resultKind, op, size, commutative, asAllocatable(a), asConstantValue(b), setFlags);
        } else if (commutative && isJavaConstant(a)) {
            return emitBinaryConst(resultKind, op, size, commutative, asAllocatable(b), asConstantValue(a), setFlags);
        } else {
            return emitBinaryVar(resultKind, op.getRMOpcode(size), size, commutative, asAllocatable(a), asAllocatable(b));
        }
    }

    private Variable emitBinary(LIRKind resultKind, AMD64RMOp op, OperandSize size, boolean commutative, Value a, Value b) {
        if (isJavaConstant(b)) {
            return emitBinaryConst(resultKind, op, size, asAllocatable(a), asJavaConstant(b));
        } else if (commutative && isJavaConstant(a)) {
            return emitBinaryConst(resultKind, op, size, asAllocatable(b), asJavaConstant(a));
        } else {
            return emitBinaryVar(resultKind, op, size, commutative, asAllocatable(a), asAllocatable(b));
        }
    }

    private Variable emitBinaryConst(LIRKind resultKind, AMD64BinaryArithmetic op, OperandSize size, boolean commutative, AllocatableValue a, ConstantValue b, boolean setFlags) {
        long value = b.getJavaConstant().asLong();
        if (NumUtil.isInt(value)) {
            Variable result = getLIRGen().newVariable(resultKind);
            int constant = (int) value;

            if (!setFlags) {
                AMD64MOp mop = getMOp(op, size, constant);
                if (mop != null) {
                    getLIRGen().append(new AMD64Unary.MOp(mop, size, result, a));
                    return result;
                }
            }

            getLIRGen().append(new AMD64Binary.ConstOp(op, size, result, a, constant));
            return result;
        } else {
            return emitBinaryVar(resultKind, op.getRMOpcode(size), size, commutative, a, asAllocatable(b));
        }
    }

    public static AMD64MOp getMOp(AMD64BinaryArithmetic op, OperandSize size, int constant) {
        AMD64MOp result = null;
        if (constant == 1) {
            if (op.equals(AMD64BinaryArithmetic.ADD)) {
                result = AMD64MOp.INC;
            }
            if (op.equals(AMD64BinaryArithmetic.SUB)) {
                result = AMD64MOp.DEC;
            }
        } else if (constant == -1) {
            if (op.equals(AMD64BinaryArithmetic.ADD)) {
                result = AMD64MOp.DEC;
            }
            if (op.equals(AMD64BinaryArithmetic.SUB)) {
                result = AMD64MOp.INC;
            }
        }
        if (result != null && size == OperandSize.BYTE) {
            result = (result == AMD64MOp.INC ? AMD64MOp.INCB : AMD64MOp.DECB);
        }
        return result;
    }

    private Variable emitBinaryConst(LIRKind resultKind, AMD64RMOp op, OperandSize size, AllocatableValue a, JavaConstant b) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AMD64Binary.DataTwoOp(op, size, result, a, b, size.getBytes()));
        return result;
    }

    private Variable emitBinaryVar(LIRKind resultKind, AMD64RMOp op, OperandSize size, boolean commutative, AllocatableValue a, AllocatableValue b) {
        Variable result = getLIRGen().newVariable(resultKind);
        if (commutative) {
            getLIRGen().append(new AMD64Binary.CommutativeTwoOp(op, size, result, a, b));
        } else {
            getLIRGen().append(new AMD64Binary.TwoOp(op, size, result, a, b));
        }
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        return ((AMD64Kind) kind).isInteger();
    }

    private Variable emitBaseOffsetLea(LIRKind resultKind, Value base, int offset, OperandSize size) {
        Variable result = getLIRGen().newVariable(resultKind);
        AMD64AddressValue address = new AMD64AddressValue(resultKind, asAllocatable(base), offset);
        getLIRGen().append(new AMD64Move.LeaOp(result, address, size));
        return result;
    }

    @Override
    public Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        boolean isAvx = supportAVX();
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                if (isJavaConstant(b) && !setFlags) {
                    long displacement = asJavaConstant(b).asLong();
                    if (NumUtil.isInt(displacement) && displacement != 1 && displacement != -1) {
                        return emitBaseOffsetLea(resultKind, a, (int) displacement, OperandSize.DWORD);
                    }
                }
                return emitBinary(resultKind, ADD, DWORD, true, a, b, setFlags);
            case QWORD:
                if (isJavaConstant(b) && !setFlags) {
                    long displacement = asJavaConstant(b).asLong();
                    if (NumUtil.isInt(displacement) && displacement != 1 && displacement != -1) {
                        return emitBaseOffsetLea(resultKind, a, (int) displacement, OperandSize.QWORD);
                    }
                }
                return emitBinary(resultKind, ADD, QWORD, true, a, b, setFlags);
            case SINGLE:
                if (isAvx) {
                    return emitBinary(resultKind, VADDSS, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.ADD, SS, true, a, b);
                }
            case DOUBLE:
                if (isAvx) {
                    return emitBinary(resultKind, VADDSD, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.ADD, SD, true, a, b);
                }
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        boolean isAvx = supportAVX();
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, SUB, DWORD, false, a, b, setFlags);
            case QWORD:
                return emitBinary(resultKind, SUB, QWORD, false, a, b, setFlags);
            case SINGLE:
                if (isAvx) {
                    return emitBinary(resultKind, VSUBSS, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.SUB, SS, false, a, b);
                }
            case DOUBLE:
                if (isAvx) {
                    return emitBinary(resultKind, VSUBSD, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.SUB, SD, false, a, b);
                }
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private Variable emitIMULConst(OperandSize size, AllocatableValue a, ConstantValue b) {
        long value = b.getJavaConstant().asLong();
        if (NumUtil.isInt(value)) {
            int imm = (int) value;
            AMD64RMIOp op;
            if (NumUtil.isByte(imm)) {
                op = AMD64RMIOp.IMUL_SX;
            } else {
                op = AMD64RMIOp.IMUL;
            }

            Variable ret = getLIRGen().newVariable(LIRKind.combine(a, b));
            getLIRGen().append(new AMD64Binary.RMIOp(op, size, ret, a, imm));
            return ret;
        } else {
            return emitBinaryVar(LIRKind.combine(a, b), AMD64RMOp.IMUL, size, true, a, asAllocatable(b));
        }
    }

    private Variable emitIMUL(OperandSize size, Value a, Value b) {
        if (isJavaConstant(b)) {
            return emitIMULConst(size, asAllocatable(a), asConstantValue(b));
        } else if (isJavaConstant(a)) {
            return emitIMULConst(size, asAllocatable(b), asConstantValue(a));
        } else {
            return emitBinaryVar(LIRKind.combine(a, b), AMD64RMOp.IMUL, size, true, asAllocatable(a), asAllocatable(b));
        }
    }

    @Override
    public Variable emitMul(Value a, Value b, boolean setFlags) {
        boolean isAvx = supportAVX();
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitIMUL(DWORD, a, b);
            case QWORD:
                return emitIMUL(QWORD, a, b);
            case SINGLE:
                if (isAvx) {
                    return emitBinary(resultKind, VMULSS, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.MUL, SS, true, a, b);
                }
            case DOUBLE:
                if (isAvx) {
                    return emitBinary(resultKind, VMULSD, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.MUL, SD, true, a, b);
                }
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private RegisterValue moveToReg(Register reg, Value v) {
        RegisterValue ret = reg.asValue(v.getValueKind());
        getLIRGen().emitMove(ret, v);
        return ret;
    }

    private Value emitMulHigh(AMD64MOp opcode, OperandSize size, Value a, Value b) {
        AMD64MulDivOp mulHigh = getLIRGen().append(new AMD64MulDivOp(opcode, size, LIRKind.combine(a, b), moveToReg(AMD64.rax, a), asAllocatable(b)));
        return getLIRGen().emitMove(mulHigh.getHighResult());
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitMulHigh(AMD64MOp.IMUL, DWORD, a, b);
            case QWORD:
                return emitMulHigh(AMD64MOp.IMUL, QWORD, a, b);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitMulHigh(AMD64MOp.MUL, DWORD, a, b);
            case QWORD:
                return emitMulHigh(AMD64MOp.MUL, QWORD, a, b);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    public Value emitBinaryMemory(VexRVMOp op, OperandSize size, AllocatableValue a, AMD64AddressValue location, LIRFrameState state) {
        assert (size.isXmmType() && supportAVX());
        Variable result = getLIRGen().newVariable(LIRKind.combine(a));
        getLIRGen().append(new AMD64VectorBinary.AVXBinaryMemoryOp(op, getRegisterSize(result), result, a, location, state));
        return result;
    }

    public Value emitBinaryMemory(AMD64RMOp op, OperandSize size, AllocatableValue a, AMD64AddressValue location, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a));
        getLIRGen().append(new AMD64Binary.MemoryTwoOp(op, size, result, a, location, state));
        return result;
    }

    protected Value emitConvertMemoryOp(PlatformKind kind, AMD64RMOp op, OperandSize size, AMD64AddressValue address, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(LIRKind.value(kind));
        getLIRGen().append(new AMD64Unary.MemoryOp(op, size, result, address, state));
        return result;
    }

    protected Value emitZeroExtendMemory(AMD64Kind memoryKind, int resultBits, AMD64AddressValue address, LIRFrameState state) {
        // Issue a zero extending load of the proper bit size and set the result to
        // the proper kind.
        Variable result = getLIRGen().newVariable(LIRKind.value(resultBits <= 32 ? AMD64Kind.DWORD : AMD64Kind.QWORD));
        switch (memoryKind) {
            case BYTE:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOVZXB, DWORD, result, address, state));
                break;
            case WORD:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOVZX, DWORD, result, address, state));
                break;
            case DWORD:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOV, DWORD, result, address, state));
                break;
            case QWORD:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOV, QWORD, result, address, state));
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(memoryKind); // ExcludeFromJacocoGeneratedReport
        }
        return result;
    }

    private AMD64MulDivOp emitIDIV(OperandSize size, Value a, Value b, LIRFrameState state) {
        LIRKind kind = LIRKind.combine(a, b);

        AMD64SignExtendOp sx = getLIRGen().append(new AMD64SignExtendOp(size, kind, moveToReg(AMD64.rax, a)));
        return getLIRGen().append(new AMD64MulDivOp(AMD64MOp.IDIV, size, kind, sx.getHighResult(), sx.getLowResult(), asAllocatable(b), state));
    }

    private AMD64MulDivOp emitDIV(OperandSize size, Value a, Value b, LIRFrameState state) {
        LIRKind kind = LIRKind.combine(a, b);

        RegisterValue rax = moveToReg(AMD64.rax, a);
        RegisterValue rdx = AMD64.rdx.asValue(kind);
        getLIRGen().append(new AMD64ClearRegisterOp(size, rdx));
        return getLIRGen().append(new AMD64MulDivOp(AMD64MOp.DIV, size, kind, rdx, rax, asAllocatable(b), state));
    }

    public Value[] emitSignedDivRem(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                op = emitIDIV(DWORD, a, b, state);
                break;
            case QWORD:
                op = emitIDIV(QWORD, a, b, state);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
        return new Value[]{getLIRGen().emitMove(op.getQuotient()), getLIRGen().emitMove(op.getRemainder())};
    }

    public Value[] emitUnsignedDivRem(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                op = emitDIV(DWORD, a, b, state);
                break;
            case QWORD:
                op = emitDIV(QWORD, a, b, state);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
        return new Value[]{getLIRGen().emitMove(op.getQuotient()), getLIRGen().emitMove(op.getRemainder())};
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        boolean isAvx = supportAVX();
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                AMD64MulDivOp op = emitIDIV(DWORD, a, b, state);
                return getLIRGen().emitMove(op.getQuotient());
            case QWORD:
                AMD64MulDivOp lop = emitIDIV(QWORD, a, b, state);
                return getLIRGen().emitMove(lop.getQuotient());
            case SINGLE:
                if (isAvx) {
                    return emitBinary(resultKind, VDIVSS, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.DIV, SS, false, a, b);
                }
            case DOUBLE:
                if (isAvx) {
                    return emitBinary(resultKind, VDIVSD, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.DIV, SD, false, a, b);
                }
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                AMD64MulDivOp op = emitIDIV(DWORD, a, b, state);
                return getLIRGen().emitMove(op.getRemainder());
            case QWORD:
                AMD64MulDivOp lop = emitIDIV(QWORD, a, b, state);
                return getLIRGen().emitMove(lop.getRemainder());
            case SINGLE: {
                Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
                getLIRGen().append(new FPDivRemOp(FREM, result, getLIRGen().asAllocatable(a), getLIRGen().asAllocatable(b)));
                return result;
            }
            case DOUBLE: {
                Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
                getLIRGen().append(new FPDivRemOp(DREM, result, getLIRGen().asAllocatable(a), getLIRGen().asAllocatable(b)));
                return result;
            }
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Variable emitUDiv(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                op = emitDIV(DWORD, a, b, state);
                break;
            case QWORD:
                op = emitDIV(QWORD, a, b, state);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
        return getLIRGen().emitMove(op.getQuotient());
    }

    @Override
    public Variable emitURem(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                op = emitDIV(DWORD, a, b, state);
                break;
            case QWORD:
                op = emitDIV(QWORD, a, b, state);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
        return getLIRGen().emitMove(op.getRemainder());
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, AND, DWORD, true, a, b, false);
            case QWORD:
                return emitBinary(resultKind, AND, QWORD, true, a, b, false);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.AND, PS, true, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.AND, PD, true, a, b);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        boolean isAvx = supportAVX();
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, OR, DWORD, true, a, b, false);
            case QWORD:
                return emitBinary(resultKind, OR, QWORD, true, a, b, false);
            case SINGLE:
                if (isAvx) {
                    return emitBinary(resultKind, VORPS, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.OR, PS, true, a, b);
                }
            case DOUBLE:
                if (isAvx) {
                    return emitBinary(resultKind, VORPD, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.OR, PD, true, a, b);
                }
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        boolean isAvx = supportAVX();
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, XOR, DWORD, true, a, b, false);
            case QWORD:
                return emitBinary(resultKind, XOR, QWORD, true, a, b, false);
            case SINGLE:
                if (isAvx) {
                    return emitBinary(resultKind, VXORPS, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.XOR, PS, true, a, b);
                }
            case DOUBLE:
                if (isAvx) {
                    return emitBinary(resultKind, VXORPD, a, b);
                } else {
                    return emitBinary(resultKind, SSEOp.XOR, PD, true, a, b);
                }
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Value emitXorFP(Value a, Value b) {
        return emitXor(a, b);
    }

    private Variable emitShift(AMD64Shift op, OperandSize size, Value a, Value b) {
        if (isJavaConstant(b)) {
            return emitShiftConst(op, size, a, asJavaConstant(b));
        }
        Variable result = getLIRGen().newVariable(LIRKind.combine(a, b).changeType(a.getPlatformKind()));
        AllocatableValue input = asAllocatable(a);
        getLIRGen().emitMove(RCX_I, b);
        getLIRGen().append(new AMD64ShiftOp(op.mcOp, size, result, input, RCX_I));
        return result;
    }

    public Variable emitShiftConst(AMD64Shift op, OperandSize size, Value a, JavaConstant b) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a).changeType(a.getPlatformKind()));
        AllocatableValue input = asAllocatable(a);
        if (b.asLong() == 1) {
            getLIRGen().append(new AMD64Unary.MOp(op.m1Op, size, result, input));
        } else {
            /*
             * c needs to be masked here, because shifts with immediate expect a byte.
             */
            getLIRGen().append(new AMD64Binary.ConstOp(op.miOp, size, result, input, (byte) b.asLong()));
        }
        return result;
    }

    @Override
    public Variable emitShl(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(SHL, DWORD, a, b);
            case QWORD:
                return emitShift(SHL, QWORD, a, b);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(SAR, DWORD, a, b);
            case QWORD:
                return emitShift(SAR, QWORD, a, b);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(SHR, DWORD, a, b);
            case QWORD:
                return emitShift(SHR, QWORD, a, b);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    public Variable emitRol(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(ROL, DWORD, a, b);
            case QWORD:
                return emitShift(ROL, QWORD, a, b);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Variable emitRor(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(ROR, DWORD, a, b);
            case QWORD:
                return emitShift(ROR, QWORD, a, b);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(a.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private AllocatableValue emitConvertOp(LIRKind kind, AMD64RMOp op, OperandSize size, Value input) {
        Variable result = getLIRGen().newVariable(kind);
        getLIRGen().append(new AMD64Unary.RMOp(op, size, result, asAllocatable(input)));
        return result;
    }

    private AllocatableValue emitConvertOp(LIRKind kind, AMD64MROp op, OperandSize size, Value input) {
        Variable result = getLIRGen().newVariable(kind);
        getLIRGen().append(new AMD64Unary.MROp(op, size, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        ValueKind<?> from = inputVal.getValueKind();
        if (to.equals(from)) {
            return inputVal;
        }

        AllocatableValue input = asAllocatable(inputVal);
        /*
         * Conversions between integer to floating point types require moves between CPU and FPU
         * registers.
         */
        AMD64Kind fromKind = scalarKind((AMD64Kind) from.getPlatformKind());
        AMD64Kind toKind = scalarKind((AMD64Kind) to.getPlatformKind());
        switch (toKind) {
            case DWORD:
                switch (fromKind) {
                    case SINGLE:
                        return emitConvertOp(to, SSEMROp.MOVD, DWORD, input);
                }
                break;
            case QWORD:
                switch (fromKind) {
                    case DOUBLE:
                        return emitConvertOp(to, SSEMROp.MOVQ, QWORD, input);
                }
                break;
            case SINGLE:
                switch (fromKind) {
                    case DWORD:
                        return emitConvertOp(to, SSEOp.MOVD, DWORD, input);
                }
                break;
            case DOUBLE:
                switch (fromKind) {
                    case QWORD:
                        return emitConvertOp(to, SSEOp.MOVQ, QWORD, input);
                }
                break;
        }
        throw GraalError.shouldNotReachHere(toKind + " " + fromKind); // ExcludeFromJacocoGeneratedReport
    }

    private static AMD64Kind scalarKind(AMD64Kind kind) {
        AMD64Kind resultKind = kind;
        if (kind.isXMM() && kind.getVectorLength() > 1) {
            if (kind.getSizeInBytes() == AMD64Kind.SINGLE.getSizeInBytes()) {
                resultKind = AMD64Kind.SINGLE;
            } else if (kind.getSizeInBytes() == AMD64Kind.DOUBLE.getSizeInBytes()) {
                resultKind = AMD64Kind.DOUBLE;
            } else {
                GraalError.shouldNotReachHere("no equal size scalar kind for " + kind); // ExcludeFromJacocoGeneratedReport
            }
        }
        return resultKind;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value input) {
        switch (op) {
            case D2F:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.SINGLE), SSEOp.CVTSD2SS, SD, input);
            case D2I:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DWORD), SSEOp.CVTTSD2SI, DWORD, input);
            case D2L:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.QWORD), SSEOp.CVTTSD2SI, QWORD, input);
            case F2D:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DOUBLE), SSEOp.CVTSS2SD, SS, input);
            case F2I:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DWORD), SSEOp.CVTTSS2SI, DWORD, input);
            case F2L:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.QWORD), SSEOp.CVTTSS2SI, QWORD, input);
            case I2D:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DOUBLE), SSEOp.CVTSI2SD, DWORD, input);
            case I2F:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.SINGLE), SSEOp.CVTSI2SS, DWORD, input);
            case L2D:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.DOUBLE), SSEOp.CVTSI2SD, QWORD, input);
            case L2F:
                return emitConvertOp(LIRKind.combine(input).changeType(AMD64Kind.SINGLE), SSEOp.CVTSI2SS, QWORD, input);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getPlatformKind() == AMD64Kind.QWORD && bits <= 32) {
            // TODO make it possible to reinterpret Long as Int in LIR without move
            return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.DWORD), AMD64RMOp.MOV, DWORD, inputVal);
        } else {
            return inputVal;
        }
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        } else if (toBits > 32) {
            // sign extend to 64 bits
            switch (fromBits) {
                case 8:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.QWORD), MOVSXB, QWORD, inputVal);
                case 16:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.QWORD), MOVSX, QWORD, inputVal);
                case 32:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.QWORD), MOVSXD, QWORD, inputVal);
                default:
                    throw GraalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)"); // ExcludeFromJacocoGeneratedReport
            }
        } else {
            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.DWORD), MOVSXB, DWORD, inputVal);
                case 16:
                    return emitConvertOp(LIRKind.combine(inputVal).changeType(AMD64Kind.DWORD), MOVSX, DWORD, inputVal);
                case 32:
                    return inputVal;
                default:
                    throw GraalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)"); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        } else if (fromBits > 32) {
            assert inputVal.getPlatformKind() == AMD64Kind.QWORD;
            Variable result = getLIRGen().newVariable(LIRKind.combine(inputVal));
            long mask = CodeUtil.mask(fromBits);
            getLIRGen().append(new AMD64Binary.DataTwoOp(AND.getRMOpcode(QWORD), QWORD, result, asAllocatable(inputVal), JavaConstant.forLong(mask)));
            return result;
        } else {
            LIRKind resultKind = LIRKind.combine(inputVal);
            if (toBits > 32) {
                resultKind = resultKind.changeType(AMD64Kind.QWORD);
            } else {
                resultKind = resultKind.changeType(AMD64Kind.DWORD);
            }

            /*
             * Always emit DWORD operations, even if the resultKind is Long. On AMD64, all DWORD
             * operations implicitly set the upper half of the register to 0, which is what we want
             * anyway. Compared to the QWORD oparations, the encoding of the DWORD operations is
             * sometimes one byte shorter.
             */
            switch (fromBits) {
                case 8:
                    return emitConvertOp(resultKind, MOVZXB, DWORD, inputVal);
                case 16:
                    return emitConvertOp(resultKind, MOVZX, DWORD, inputVal);
                case 32:
                    return emitConvertOp(resultKind, MOV, DWORD, inputVal);
            }

            // odd bit count, fall back on manual masking
            Variable result = getLIRGen().newVariable(resultKind);
            JavaConstant mask;
            if (toBits > 32) {
                mask = JavaConstant.forLong(CodeUtil.mask(fromBits));
            } else {
                mask = JavaConstant.forInt((int) CodeUtil.mask(fromBits));
            }
            getLIRGen().append(new AMD64Binary.DataTwoOp(AND.getRMOpcode(DWORD), DWORD, result, asAllocatable(inputVal), mask));
            return result;
        }
    }

    @Override
    public Variable emitBitCount(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64Unary.RMOp(POPCNT, QWORD, result, asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64Unary.RMOp(POPCNT, DWORD, result, asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Variable emitBitScanForward(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        getLIRGen().append(new AMD64Unary.RMOp(BSF, QWORD, result, asAllocatable(value)));
        return result;
    }

    @Override
    public Variable emitBitScanReverse(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64Unary.RMOp(BSR, QWORD, result, asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64Unary.RMOp(BSR, DWORD, result, asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Variable emitFusedMultiplyAdd(Value a, Value b, Value c) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a, b, c));
        assert ((AMD64Kind) a.getPlatformKind()).isXMM() && ((AMD64Kind) b.getPlatformKind()).isXMM() && ((AMD64Kind) c.getPlatformKind()).isXMM();
        assert a.getPlatformKind().equals(b.getPlatformKind());
        assert b.getPlatformKind().equals(c.getPlatformKind());

        if (a.getPlatformKind() == AMD64Kind.DOUBLE) {
            getLIRGen().append(new AMD64Ternary.ThreeOp(VFMADD231SD, AVXSize.XMM, result, asAllocatable(c), asAllocatable(a), asAllocatable(b)));
        } else {
            assert a.getPlatformKind() == AMD64Kind.SINGLE;
            getLIRGen().append(new AMD64Ternary.ThreeOp(VFMADD231SS, AVXSize.XMM, result, asAllocatable(c), asAllocatable(a), asAllocatable(b)));
        }
        return result;
    }

    @Override
    public Value emitCountLeadingZeros(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64Unary.RMOp(LZCNT, QWORD, result, asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64Unary.RMOp(LZCNT, DWORD, result, asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Value emitCountTrailingZeros(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64Unary.RMOp(TZCNT, QWORD, result, asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64Unary.RMOp(TZCNT, DWORD, result, asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Value emitLogicalAndNot(Value value1, Value value2) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value1, value2));

        if (value1.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(VexGeneralPurposeRVMOp.ANDN, AVXSize.QWORD, result, asAllocatable(value1), asAllocatable(value2)));
        } else {
            getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(VexGeneralPurposeRVMOp.ANDN, AVXSize.DWORD, result, asAllocatable(value1), asAllocatable(value2)));
        }
        return result;
    }

    @Override
    public Value emitLowestSetIsolatedBit(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value));

        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64VectorUnary.AVXUnaryOp(VexGeneralPurposeRMOp.BLSI, AVXSize.QWORD, result, asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64VectorUnary.AVXUnaryOp(VexGeneralPurposeRMOp.BLSI, AVXSize.DWORD, result, asAllocatable(value)));
        }

        return result;
    }

    @Override
    public Value emitGetMaskUpToLowestSetBit(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value));

        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64VectorUnary.AVXUnaryOp(VexGeneralPurposeRMOp.BLSMSK, AVXSize.QWORD, result, asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64VectorUnary.AVXUnaryOp(VexGeneralPurposeRMOp.BLSMSK, AVXSize.DWORD, result, asAllocatable(value)));
        }

        return result;
    }

    @Override
    public Value emitResetLowestSetBit(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value));

        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64VectorUnary.AVXUnaryOp(VexGeneralPurposeRMOp.BLSR, AVXSize.QWORD, result, asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64VectorUnary.AVXUnaryOp(VexGeneralPurposeRMOp.BLSR, AVXSize.DWORD, result, asAllocatable(value)));
        }

        return result;
    }

    private Value emitMathAbsForInteger(AllocatableValue tmp, Value input, OperandSize size) {
        Variable sign = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AMD64Binary.ConstOp(SAR.miOp, size, sign, asAllocatable(input), size.getBytes() * Byte.SIZE - 1));
        getLIRGen().emitMove(tmp, input);
        return emitSub(emitXor(tmp, sign), sign, false);
    }

    @Override
    public Value emitMathAbs(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case DWORD:
                return emitMathAbsForInteger(result, input, DWORD);
            case QWORD:
                return emitMathAbsForInteger(result, input, QWORD);
            case SINGLE:
                getLIRGen().append(new AMD64Binary.DataTwoOp(SSEOp.AND, PS, result, asAllocatable(input), JavaConstant.forFloat(Float.intBitsToFloat(0x7FFFFFFF)), 16));
                break;
            case DOUBLE:
                getLIRGen().append(new AMD64Binary.DataTwoOp(SSEOp.AND, PD, result, asAllocatable(input), JavaConstant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)), 16));
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(input.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
        return result;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case SINGLE:
                getLIRGen().append(new AMD64Unary.RMOp(SSEOp.SQRT, SS, result, asAllocatable(input)));
                break;
            case DOUBLE:
                getLIRGen().append(new AMD64Unary.RMOp(SSEOp.SQRT, SD, result, asAllocatable(input)));
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(input.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
        return result;
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        if (base10) {
            return new AMD64MathLog10Op().emitLIRWrapper(getLIRGen(), input);
        } else {
            return new AMD64MathLogOp().emitLIRWrapper(getLIRGen(), input);
        }
    }

    @Override
    public Value emitMathCos(Value input) {
        return new AMD64MathCosOp().emitLIRWrapper(getLIRGen(), input);
    }

    @Override
    public Value emitMathSin(Value input) {
        return new AMD64MathSinOp().emitLIRWrapper(getLIRGen(), input);
    }

    @Override
    public Value emitMathTan(Value input) {
        return new AMD64MathTanOp().emitLIRWrapper(getLIRGen(), input);
    }

    @Override
    public Value emitMathExp(Value input) {
        return new AMD64MathExpOp().emitLIRWrapper(getLIRGen(), input);
    }

    @Override
    public Value emitMathPow(Value x, Value y) {
        return new AMD64MathPowOp().emitLIRWrapper(getLIRGen(), x, y);
    }

    @Override
    public Value emitMathSignum(Value input) {
        Variable result = getLIRGen().newVariable(input.getValueKind());
        getLIRGen().append(new AMD64MathSignumOp(getLIRGen(), result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathCopySign(Value magnitude, Value sign) {
        Variable result = getLIRGen().newVariable(magnitude.getValueKind());
        getLIRGen().append(new AMD64MathCopySignOp(getLIRGen(), result, asAllocatable(magnitude), asAllocatable(sign)));
        return result;
    }

    protected AMD64LIRGenerator getAMD64LIRGen() {
        return (AMD64LIRGenerator) getLIRGen();
    }

    @Override
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state, MemoryOrderMode memoryOrder, MemoryExtendKind extendKind) {
        assert extendKind.isNotExtended();
        /*
         * AMD64's consistency model does not require any fences for loads. Volatile store->load
         * ordering requirements are enforced at the stores.
         */
        AMD64AddressValue loadAddress = getAMD64LIRGen().asAddressValue(address);
        Variable result = getLIRGen().newVariable(getLIRGen().toRegisterKind(kind));
        switch ((AMD64Kind) kind.getPlatformKind()) {
            case BYTE:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOVSXB, DWORD, result, loadAddress, state));
                break;
            case WORD:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOVSX, DWORD, result, loadAddress, state));
                break;
            case DWORD:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOV, DWORD, result, loadAddress, state));
                break;
            case QWORD:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOV, QWORD, result, loadAddress, state));
                break;
            case SINGLE:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOVSS, SS, result, loadAddress, state));
                break;
            case DOUBLE:
                getLIRGen().append(new AMD64Unary.MemoryOp(MOVSD, SD, result, loadAddress, state));
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(kind.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
        return result;
    }

    protected void emitStoreConst(AMD64Kind kind, AMD64AddressValue address, ConstantValue value, LIRFrameState state) {
        Constant c = value.getConstant();
        if (JavaConstant.isNull(c)) {
            assert kind == AMD64Kind.DWORD || kind == AMD64Kind.QWORD;
            OperandSize size = kind == AMD64Kind.DWORD ? DWORD : QWORD;
            getLIRGen().append(new AMD64BinaryConsumer.MemoryConstOp(AMD64MIOp.MOV, size, address, 0, state));
            return;
        } else if (c instanceof VMConstant) {
            // only 32-bit constants can be patched
            if (kind == AMD64Kind.DWORD) {
                if (getLIRGen().target().inlineObjects || !(c instanceof JavaConstant)) {
                    // if c is a JavaConstant, it's an oop, otherwise it's a metaspace constant
                    assert !(c instanceof JavaConstant) || ((JavaConstant) c).getJavaKind() == JavaKind.Object;
                    getLIRGen().append(new AMD64BinaryConsumer.MemoryVMConstOp(AMD64MIOp.MOV, address, (VMConstant) c, state));
                    return;
                }
            }
        } else {
            JavaConstant jc = (JavaConstant) c;
            assert jc.getJavaKind().isPrimitive();

            AMD64MIOp op = AMD64MIOp.MOV;
            OperandSize size;
            long imm;

            switch (kind) {
                case BYTE:
                    op = AMD64MIOp.MOVB;
                    size = BYTE;
                    imm = jc.asInt();
                    break;
                case WORD:
                    size = WORD;
                    imm = jc.asInt();
                    break;
                case DWORD:
                    size = DWORD;
                    imm = jc.asInt();
                    break;
                case QWORD:
                    size = QWORD;
                    imm = jc.asLong();
                    break;
                case SINGLE:
                    size = DWORD;
                    imm = Float.floatToRawIntBits(jc.asFloat());
                    break;
                case DOUBLE:
                    size = QWORD;
                    imm = Double.doubleToRawLongBits(jc.asDouble());
                    break;
                default:
                    throw GraalError.shouldNotReachHere("unexpected kind " + kind); // ExcludeFromJacocoGeneratedReport
            }

            if (NumUtil.isInt(imm)) {
                getLIRGen().append(new AMD64BinaryConsumer.MemoryConstOp(op, size, address, (int) imm, state));
                return;
            }
        }

        // fallback: load, then store
        emitStore(kind, address, asAllocatable(value), state);
    }

    protected void emitStore(AMD64Kind kind, AMD64AddressValue address, AllocatableValue value, LIRFrameState state) {
        switch (kind) {
            case BYTE:
                getLIRGen().append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOVB, BYTE, address, value, state));
                break;
            case WORD:
                getLIRGen().append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOV, WORD, address, value, state));
                break;
            case DWORD:
                getLIRGen().append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOV, DWORD, address, value, state));
                break;
            case QWORD:
                getLIRGen().append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOV, QWORD, address, value, state));
                break;
            case SINGLE:
                getLIRGen().append(new AMD64BinaryConsumer.MemoryMROp(SSEMROp.MOVSS, SS, address, value, state));
                break;
            case DOUBLE:
                getLIRGen().append(new AMD64BinaryConsumer.MemoryMROp(SSEMROp.MOVSD, SD, address, value, state));
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(kind); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public void emitStore(ValueKind<?> lirKind, Value address, Value input, LIRFrameState state, MemoryOrderMode memoryOrder) {
        AMD64AddressValue storeAddress = getAMD64LIRGen().asAddressValue(address);
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();
        if (isConstantValue(input)) {
            emitStoreConst(kind, storeAddress, asConstantValue(input), state);
        } else {
            emitStore(kind, storeAddress, asAllocatable(input), state);
        }
        /*
         * Need a fence after volatile stores to ensure a volatile load cannot execute before this
         * operation.
         */
        if (memoryOrder == MemoryOrderMode.VOLATILE) {
            getLIRGen().emitMembar(MemoryBarriers.STORE_LOAD);
        }
    }

    public boolean mustReplaceNullWithNullRegister(Constant nullConstant) {
        /* Uncompressed null pointers only */
        return nullRegisterValue != null && JavaConstant.NULL_POINTER.equals(nullConstant);
    }

    public AllocatableValue getNullRegisterValue() {
        return nullRegisterValue;
    }

    @Override
    public void emitCompareOp(AMD64Kind cmpKind, AllocatableValue left, Value right) {
        OperandSize size;
        switch (cmpKind) {
            case BYTE:
                size = BYTE;
                break;
            case WORD:
                size = WORD;
                break;
            case DWORD:
                size = DWORD;
                break;
            case QWORD:
                size = QWORD;
                break;
            case SINGLE:
                getLIRGen().append(new AMD64BinaryConsumer.Op(SSEOp.UCOMIS, PS, left, asAllocatable(right)));
                return;
            case DOUBLE:
                getLIRGen().append(new AMD64BinaryConsumer.Op(SSEOp.UCOMIS, PD, left, asAllocatable(right)));
                return;
            default:
                throw GraalError.shouldNotReachHere("unexpected kind: " + cmpKind); // ExcludeFromJacocoGeneratedReport
        }

        if (isConstantValue(right)) {
            Constant c = LIRValueUtil.asConstant(right);
            if (JavaConstant.isNull(c)) {
                if (mustReplaceNullWithNullRegister(c)) {
                    getLIRGen().append(new AMD64BinaryConsumer.Op(AMD64RMOp.CMP, size, left, nullRegisterValue));
                } else {
                    getLIRGen().append(new AMD64BinaryConsumer.Op(TEST, size, left, left));
                }
                return;
            } else if (c instanceof VMConstant) {
                VMConstant vc = (VMConstant) c;
                if (size == DWORD && getLIRGen().target().inlineObjects) {
                    getLIRGen().append(new AMD64BinaryConsumer.VMConstOp(CMP.getMIOpcode(DWORD, false), left, vc));
                } else {
                    getLIRGen().append(new AMD64BinaryConsumer.DataOp(CMP.getRMOpcode(size), size, left, vc));
                }
                return;
            } else if (c instanceof JavaConstant) {
                JavaConstant jc = (JavaConstant) c;
                if (jc.isDefaultForKind()) {
                    AMD64RMOp op = size == BYTE ? TESTB : TEST;
                    getLIRGen().append(new AMD64BinaryConsumer.Op(op, size, left, left));
                    return;
                } else if (NumUtil.is32bit(jc.asLong())) {
                    getLIRGen().append(new AMD64BinaryConsumer.ConstOp(CMP, size, left, (int) jc.asLong()));
                    return;
                }
            }
        }

        // fallback: load, then compare
        getLIRGen().append(new AMD64BinaryConsumer.Op(CMP.getRMOpcode(size), size, left, asAllocatable(right)));
    }

    @Override
    public Value emitRound(Value value, ArithmeticLIRGeneratorTool.RoundingMode mode) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value));
        assert ((AMD64Kind) value.getPlatformKind()).isXMM();
        if (value.getPlatformKind() == AMD64Kind.SINGLE) {
            getLIRGen().append(new AMD64Binary.RMIOp(AMD64RMIOp.ROUNDSS, OperandSize.PD, result, asAllocatable(value), mode.encoding));
        } else {
            getLIRGen().append(new AMD64Binary.RMIOp(AMD64RMIOp.ROUNDSD, OperandSize.PD, result, asAllocatable(value), mode.encoding));
        }
        return result;
    }

    @Override
    public Value emitRoundFloatToInteger(Value value) {
        PlatformKind valuePlatformKind = value.getPlatformKind();
        assert valuePlatformKind == AMD64Kind.SINGLE || valuePlatformKind == AMD64Kind.DOUBLE;
        Variable result = getLIRGen().newVariable(LIRKind.value(value.getPlatformKind() == AMD64Kind.SINGLE ? AMD64Kind.DWORD : AMD64Kind.QWORD));
        getLIRGen().append(new AMD64RoundFloatToIntegerOp(getLIRGen(), result, asAllocatable(value)));
        return result;
    }

    @Override
    public Value emitIntegerCompress(Value value, Value mask) {
        AMD64Kind kind = (AMD64Kind) value.getPlatformKind();
        GraalError.guarantee(kind == AMD64Kind.QWORD || kind == AMD64Kind.DWORD, "Unsupported value kind");
        Variable result = getLIRGen().newVariable(value.getValueKind());
        getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(VexGeneralPurposeRVMOp.PEXT, kind == AMD64Kind.QWORD ? AVXSize.QWORD : AVXSize.DWORD, result, asAllocatable(value), asAllocatable(mask)));
        return result;
    }

    @Override
    public Value emitIntegerExpand(Value value, Value mask) {
        AMD64Kind kind = (AMD64Kind) value.getPlatformKind();
        GraalError.guarantee(kind == AMD64Kind.QWORD || kind == AMD64Kind.DWORD, "Unsupported value kind");
        Variable result = getLIRGen().newVariable(value.getValueKind());
        getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(VexGeneralPurposeRVMOp.PDEP, kind == AMD64Kind.QWORD ? AVXSize.QWORD : AVXSize.DWORD, result, asAllocatable(value), asAllocatable(mask)));
        return result;
    }

    @Override
    public Value emitFloatIsInfinite(Value input) {
        AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
        GraalError.guarantee(kind == AMD64Kind.DOUBLE || kind == AMD64Kind.SINGLE, "Unsupported value kind %s", input.getPlatformKind());
        Variable result = getLIRGen().newVariable(LIRKind.value(AMD64Kind.DWORD));
        getLIRGen().append(new AMD64VectorUnary.FloatPointClassTestOp(getLIRGen(), kind == AMD64Kind.DOUBLE ? OperandSize.SD : OperandSize.SS, POS_INF | NEG_INF, result, asAllocatable(input)));
        return result;
    }

    public boolean supportAVX() {
        TargetDescription target = getLIRGen().target();
        return ((AMD64) target.arch).getFeatures().contains(CPUFeature.AVX);
    }

    private static AVXSize getRegisterSize(Value a) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.isXMM()) {
            return AVXKind.getRegisterSize(kind);
        } else {
            return AVXSize.XMM;
        }
    }

    protected Variable emitBinary(LIRKind resultKind, VexRVMOp op, Value a, Value b) {
        Variable result = getLIRGen().newVariable(resultKind);
        if (b instanceof ConstantValue && (b.getPlatformKind() == AMD64Kind.SINGLE || b.getPlatformKind() == AMD64Kind.DOUBLE)) {
            getLIRGen().append(new AVXBinaryConstFloatOp(op, getRegisterSize(result), result, asAllocatable(a), (ConstantValue) b));
        } else {
            getLIRGen().append(new AVXBinaryOp(op, getRegisterSize(result), result, asAllocatable(a), asAllocatable(b)));
        }
        return result;
    }

    protected Variable emitVectorBlend(Value zeroValue, Value oneValue, Value mask) {
        AVXSize size = getRegisterSize(zeroValue);
        assert size == AVXSize.XMM || size == AVXSize.YMM : size;
        AMD64Kind inputKind = ((AMD64Kind) zeroValue.getPlatformKind()).getScalar();
        Variable result = getLIRGen().newVariable(zeroValue.getValueKind());
        // AVX/AVX2 blend
        VexRVMROp blend;
        switch (inputKind) {
            case SINGLE:
                blend = VBLENDVPS;
                break;
            case DOUBLE:
                blend = VBLENDVPD;
                break;
            default:
                blend = VPBLENDVB;
                break;
        }

        getLIRGen().append(new AMD64VectorBlend.VexBlendOp(blend, size, getLIRGen().asAllocatable(result), getLIRGen().asAllocatable(zeroValue), getLIRGen().asAllocatable(oneValue),
                        getLIRGen().asAllocatable(mask)));
        return result;
    }

    @Override
    public Value emitMathMax(Value x, Value y) {
        return emitMathMinMax(x, y, AMD64MathMinMaxFloatOp.Max);
    }

    @Override
    public Value emitMathMin(Value x, Value y) {
        return emitMathMinMax(x, y, AMD64MathMinMaxFloatOp.Min);
    }

    protected enum AMD64MathMinMaxFloatOp {
        Min(VexRVMOp.VMINSS, VexRVMOp.VMINSD, VexRVMOp.VMINPS, VexRVMOp.VMINPD),
        Max(VexRVMOp.VMAXSS, VexRVMOp.VMAXSD, VexRVMOp.VMAXPS, VexRVMOp.VMAXPD);

        private final VexRVMOp scalarSingleOp;
        private final VexRVMOp scalarDoubleOp;
        private final VexRVMOp vectorSingleOp;
        private final VexRVMOp vectorDoubleOp;

        AMD64MathMinMaxFloatOp(VexRVMOp scalarSingleOp, VexRVMOp scalarDoubleOp, VexRVMOp vectorSingleOp, VexRVMOp vectorDoubleOp) {
            this.scalarSingleOp = scalarSingleOp;
            this.scalarDoubleOp = scalarDoubleOp;
            this.vectorSingleOp = vectorSingleOp;
            this.vectorDoubleOp = vectorDoubleOp;
        }

        public VexRVMOp getAVXOp(AMD64Kind kind) {
            switch (kind.getScalar()) {
                case SINGLE:
                    return kind.getVectorLength() > 1 ? vectorSingleOp : scalarSingleOp;
                case DOUBLE:
                    return kind.getVectorLength() > 1 ? vectorDoubleOp : scalarDoubleOp;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            }
        }

        /**
         * Returns true if the constant is 0.0 of the sign that is preferred in case of ambiguity
         * (i.e., if one of the inputs is -0.0 and the other is +0.0).
         */
        public boolean isPreferredZeroConst(JavaConstant constant) {
            JavaKind kind = constant.getJavaKind();
            assert kind == JavaKind.Double || kind == JavaKind.Float : kind;
            switch (this) {
                case Max:
                    if ((kind == JavaKind.Double && Double.doubleToRawLongBits(constant.asDouble()) == Double.doubleToRawLongBits(0.0)) ||
                                    (kind == JavaKind.Float && Float.floatToRawIntBits(constant.asFloat()) == Float.floatToRawIntBits(0.0f))) {
                        return true;
                    }
                    break;
                case Min:
                    if ((kind == JavaKind.Double && Double.doubleToRawLongBits(constant.asDouble()) == Double.doubleToRawLongBits(-0.0)) ||
                                    (kind == JavaKind.Float && Float.floatToRawIntBits(constant.asFloat()) == Float.floatToRawIntBits(-0.0f))) {
                        return true;
                    }
                    break;
            }
            return false;
        }
    }

    /**
     * Emits code for a branchless floating-point Math.min/Math.max operation. Requires AVX.
     *
     * Supports (scalarReg, scalarReg) and (scalarReg, scalarConst) operands.
     *
     * @see Math#max(double, double)
     * @see Math#max(float, float)
     * @see Math#min(double, double)
     * @see Math#min(float, float)
     */
    protected Value emitMathMinMax(Value a, Value b, AMD64MathMinMaxFloatOp minmaxop) {
        assert supportAVX();
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();

        // vmin*/vmax*: if the values being compared are both 0.0 (of either sign), dst = src2.
        // hence, if one argument is +0.0 and the other is -0.0, to get the correct result, we
        // have to reorder the source registers such that -0.0 (min) / +0.0 (max) is in src2.
        // Therefore, if a (min) / b (max) is negative (most significant bit is 1), swap a and b,
        // so that if one is -0.0 and the other is +0.0, the correct result is in b', i.e.:
        // min: a' = +0.0, b' = -0.0 (negative values in a are moved to b').
        // max: a' = -0.0, b' = +0.0 (negative values in b are moved to a').
        AllocatableValue atmp;
        AllocatableValue btmp;
        boolean checkAndMergeNaN = true;
        if (kind.getVectorLength() == 1 && isJavaConstant(b)) {
            // Optimized case for scalar constants (only).
            JavaConstant bConst = asJavaConstant(b);
            if (kind == AMD64Kind.DOUBLE && Double.isNaN(bConst.asDouble()) ||
                            kind == AMD64Kind.SINGLE && Float.isNaN(bConst.asFloat())) {
                // The result is always NaN.
                return b;
            }

            // Order registers so that if bConst is -0.0 (min) / +0.0 (max) and a is +/-0.0,
            // the correct result is always in src2.
            if (minmaxop.isPreferredZeroConst(bConst)) {
                atmp = asAllocatable(a);
                btmp = asAllocatable(b);
            } else {
                atmp = asAllocatable(b);
                btmp = asAllocatable(a);
                // Because a is in src2, and src1 (b) is known to not be NaN, the result is already
                // correct (NaN) if a is NaN, so we can skip the NaN check.
                checkAndMergeNaN = false;
            }
        } else {
            AllocatableValue signMask = asAllocatable(minmaxop == AMD64MathMinMaxFloatOp.Min ? a : b);
            atmp = emitVectorBlend(a, b, signMask);
            btmp = emitVectorBlend(b, a, signMask);
        }

        // vmaxps/vmaxpd/vminps/vminpd result, a', b'
        LIRKind resultKind = LIRKind.combine(atmp, btmp);
        AllocatableValue result = emitBinary(resultKind, minmaxop.getAVXOp(kind), atmp, btmp);

        if (checkAndMergeNaN) {
            // move NaN elements in a to result (result' = isNaN(a) ? a : result)
            // maskNaN = vcmpunordps/vcmpunordpd a', a'
            AllocatableValue maskNaN = emitVectorFloatCompare(atmp, atmp, VexFloatCompareOp.Predicate.UNORD_Q);
            // vblendvps/vblendvpd result', result, atmp, maskNaN
            result = emitVectorBlend(result, atmp, maskNaN);
        }
        return result;
    }

    protected AllocatableValue emitVectorFloatCompare(AllocatableValue a, AllocatableValue b, VexFloatCompareOp.Predicate predicate) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        AVXSize size = AVXKind.getRegisterSize(kind);
        VexFloatCompareOp vcmpp;
        switch (kind.getScalar()) {
            case SINGLE:
                if (kind.getVectorLength() > 1) {
                    vcmpp = VexFloatCompareOp.VCMPPS;
                } else {
                    vcmpp = VexFloatCompareOp.VCMPSS;
                }
                break;
            case DOUBLE:
                if (kind.getVectorLength() > 1) {
                    vcmpp = VexFloatCompareOp.VCMPPD;
                } else {
                    vcmpp = VexFloatCompareOp.VCMPSD;
                }
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
        }
        AllocatableValue result = getLIRGen().newVariable(LIRKind.combine(a, b));
        getLIRGen().append(new AMD64VectorFloatCompareOp(vcmpp, size, result, a, b, predicate));
        return result;
    }

    @Override
    public Variable emitReverseBits(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AMD64BitSwapOp(getLIRGen(), result, asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitHalfFloatToFloat(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.value(AMD64Kind.SINGLE));
        getLIRGen().append(new AMD64HalfFloatToFloatOp(result, asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitFloatToHalfFloat(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.value(AMD64Kind.DWORD));
        getLIRGen().append(new AMD64FloatToHalfFloatOp(getLIRGen(), result, asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitNormalizedUnsignedCompare(LIRKind compareKind, Value x, Value y) {
        Variable result = getLIRGen().newVariable(LIRKind.value(AMD64Kind.DWORD));
        getLIRGen().append(new AMD64NormalizedUnsignedCompareOp(result, compareKind, asAllocatable(x), asAllocatable(y)));
        return result;
    }
}
