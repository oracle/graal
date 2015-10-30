/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.graal.compiler.amd64;

import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.AND;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.OR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SUB;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MOp.NEG;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MOp.NOT;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.BSF;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.BSR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.LZCNT;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOV;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSX;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXB;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVZX;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVZXB;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.POPCNT;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.TZCNT;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.ROL;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.ROR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.SAR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.SHL;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.SHR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.DWORD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.PD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.PS;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.QWORD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.SD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.SS;
import static com.oracle.graal.lir.LIRValueUtil.asConstantValue;
import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static com.oracle.graal.lir.amd64.AMD64Arithmetic.DREM;
import static com.oracle.graal.lir.amd64.AMD64Arithmetic.FREM;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.COS;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.LOG;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.LOG10;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.SIN;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.TAN;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MROp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMIOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift;
import com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize;
import com.oracle.graal.asm.amd64.AMD64Assembler.SSEOp;
import com.oracle.graal.compiler.common.calc.FloatConvert;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.amd64.AMD64AddressValue;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.FPDivRemOp;
import com.oracle.graal.lir.amd64.AMD64ArithmeticLIRGeneratorTool;
import com.oracle.graal.lir.amd64.AMD64Binary;
import com.oracle.graal.lir.amd64.AMD64ClearRegisterOp;
import com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp;
import com.oracle.graal.lir.amd64.AMD64MulDivOp;
import com.oracle.graal.lir.amd64.AMD64ShiftOp;
import com.oracle.graal.lir.amd64.AMD64SignExtendOp;
import com.oracle.graal.lir.amd64.AMD64Unary;
import com.oracle.graal.lir.gen.ArithmeticLIRGenerator;

/**
 * This class implements the AMD64 specific portion of the LIR generator.
 */
public class AMD64ArithmeticLIRGenerator extends ArithmeticLIRGenerator implements AMD64ArithmeticLIRGeneratorTool {

    private static final RegisterValue RCX_I = AMD64.rcx.asValue(LIRKind.value(AMD64Kind.DWORD));

    @Override
    public Variable emitNegate(Value inputVal) {
        AllocatableValue input = getLIRGen().asAllocatable(inputVal);
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case DWORD:
                getLIRGen().append(new AMD64Unary.MOp(NEG, DWORD, result, input));
                break;
            case QWORD:
                getLIRGen().append(new AMD64Unary.MOp(NEG, QWORD, result, input));
                break;
            case SINGLE:
                getLIRGen().append(new AMD64Binary.DataOp(SSEOp.XOR, PS, result, input, JavaConstant.forFloat(Float.intBitsToFloat(0x80000000)), 16));
                break;
            case DOUBLE:
                getLIRGen().append(new AMD64Binary.DataOp(SSEOp.XOR, PD, result, input, JavaConstant.forDouble(Double.longBitsToDouble(0x8000000000000000L)), 16));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitNot(Value inputVal) {
        AllocatableValue input = getLIRGen().asAllocatable(inputVal);
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case DWORD:
                getLIRGen().append(new AMD64Unary.MOp(NOT, DWORD, result, input));
                break;
            case QWORD:
                getLIRGen().append(new AMD64Unary.MOp(NOT, QWORD, result, input));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    private Variable emitBinary(LIRKind resultKind, AMD64BinaryArithmetic op, OperandSize size, boolean commutative, Value a, Value b, boolean setFlags) {
        if (isJavaConstant(b)) {
            return emitBinaryConst(resultKind, op, size, commutative, getLIRGen().asAllocatable(a), asConstantValue(b), setFlags);
        } else if (commutative && isJavaConstant(a)) {
            return emitBinaryConst(resultKind, op, size, commutative, getLIRGen().asAllocatable(b), asConstantValue(a), setFlags);
        } else {
            return emitBinaryVar(resultKind, op.getRMOpcode(size), size, commutative, getLIRGen().asAllocatable(a), getLIRGen().asAllocatable(b));
        }
    }

    private Variable emitBinary(LIRKind resultKind, AMD64RMOp op, OperandSize size, boolean commutative, Value a, Value b) {
        if (isJavaConstant(b)) {
            return emitBinaryConst(resultKind, op, size, getLIRGen().asAllocatable(a), asJavaConstant(b));
        } else if (commutative && isJavaConstant(a)) {
            return emitBinaryConst(resultKind, op, size, getLIRGen().asAllocatable(b), asJavaConstant(a));
        } else {
            return emitBinaryVar(resultKind, op, size, commutative, getLIRGen().asAllocatable(a), getLIRGen().asAllocatable(b));
        }
    }

    private Variable emitBinaryConst(LIRKind resultKind, AMD64BinaryArithmetic op, OperandSize size, boolean commutative, AllocatableValue a, ConstantValue b, boolean setFlags) {
        long value = b.getJavaConstant().asLong();
        if (NumUtil.isInt(value)) {
            Variable result = getLIRGen().newVariable(resultKind);
            int constant = (int) value;

            if (!setFlags) {
                AMD64MOp mop = getMOp(op, constant);
                if (mop != null) {
                    getLIRGen().append(new AMD64Unary.MOp(mop, size, result, a));
                    return result;
                }
            }

            getLIRGen().append(new AMD64Binary.ConstOp(op, size, result, a, constant));
            return result;
        } else {
            return emitBinaryVar(resultKind, op.getRMOpcode(size), size, commutative, a, getLIRGen().asAllocatable(b));
        }
    }

    private static AMD64MOp getMOp(AMD64BinaryArithmetic op, int constant) {
        if (constant == 1) {
            if (op.equals(AMD64BinaryArithmetic.ADD)) {
                return AMD64MOp.INC;
            }
            if (op.equals(AMD64BinaryArithmetic.SUB)) {
                return AMD64MOp.DEC;
            }
        } else if (constant == -1) {
            if (op.equals(AMD64BinaryArithmetic.ADD)) {
                return AMD64MOp.DEC;
            }
            if (op.equals(AMD64BinaryArithmetic.SUB)) {
                return AMD64MOp.INC;
            }
        }
        return null;
    }

    private Variable emitBinaryConst(LIRKind resultKind, AMD64RMOp op, OperandSize size, AllocatableValue a, JavaConstant b) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AMD64Binary.DataOp(op, size, result, a, b));
        return result;
    }

    private Variable emitBinaryVar(LIRKind resultKind, AMD64RMOp op, OperandSize size, boolean commutative, AllocatableValue a, AllocatableValue b) {
        Variable result = getLIRGen().newVariable(resultKind);
        if (commutative) {
            getLIRGen().append(new AMD64Binary.CommutativeOp(op, size, result, a, b));
        } else {
            getLIRGen().append(new AMD64Binary.Op(op, size, result, a, b));
        }
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        return ((AMD64Kind) kind).isInteger();
    }

    @Override
    public Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, ADD, DWORD, true, a, b, setFlags);
            case QWORD:
                return emitBinary(resultKind, ADD, QWORD, true, a, b, setFlags);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.ADD, SS, true, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.ADD, SD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, SUB, DWORD, false, a, b, setFlags);
            case QWORD:
                return emitBinary(resultKind, SUB, QWORD, false, a, b, setFlags);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.SUB, SS, false, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.SUB, SD, false, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
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
            return emitBinaryVar(LIRKind.combine(a, b), AMD64RMOp.IMUL, size, true, a, getLIRGen().asAllocatable(b));
        }
    }

    private Variable emitIMUL(OperandSize size, Value a, Value b) {
        if (isJavaConstant(b)) {
            return emitIMULConst(size, getLIRGen().asAllocatable(a), asConstantValue(b));
        } else if (isJavaConstant(a)) {
            return emitIMULConst(size, getLIRGen().asAllocatable(b), asConstantValue(a));
        } else {
            return emitBinaryVar(LIRKind.combine(a, b), AMD64RMOp.IMUL, size, true, getLIRGen().asAllocatable(a), getLIRGen().asAllocatable(b));
        }
    }

    @Override
    public Variable emitMul(Value a, Value b, boolean setFlags) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitIMUL(DWORD, a, b);
            case QWORD:
                return emitIMUL(QWORD, a, b);
            case SINGLE:
                return emitBinary(LIRKind.combine(a, b), SSEOp.MUL, SS, true, a, b);
            case DOUBLE:
                return emitBinary(LIRKind.combine(a, b), SSEOp.MUL, SD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private RegisterValue moveToReg(Register reg, Value v) {
        RegisterValue ret = reg.asValue(v.getLIRKind());
        getLIRGen().emitMove(ret, v);
        return ret;
    }

    private Value emitMulHigh(AMD64MOp opcode, OperandSize size, Value a, Value b) {
        AMD64MulDivOp mulHigh = getLIRGen().append(new AMD64MulDivOp(opcode, size, LIRKind.combine(a, b), moveToReg(AMD64.rax, a), getLIRGen().asAllocatable(b)));
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
                throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public Value emitBinaryMemory(AMD64RMOp op, OperandSize size, AllocatableValue a, AMD64AddressValue location, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a));
        getLIRGen().append(new AMD64Binary.MemoryOp(op, size, result, a, location, state));
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
        Variable result = getLIRGen().newVariable(LIRKind.value(resultBits == 32 ? AMD64Kind.DWORD : AMD64Kind.QWORD));
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
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    private AMD64MulDivOp emitIDIV(OperandSize size, Value a, Value b, LIRFrameState state) {
        LIRKind kind = LIRKind.combine(a, b);

        AMD64SignExtendOp sx = getLIRGen().append(new AMD64SignExtendOp(size, kind, moveToReg(AMD64.rax, a)));
        return getLIRGen().append(new AMD64MulDivOp(AMD64MOp.IDIV, size, kind, sx.getHighResult(), sx.getLowResult(), getLIRGen().asAllocatable(b), state));
    }

    private AMD64MulDivOp emitDIV(OperandSize size, Value a, Value b, LIRFrameState state) {
        LIRKind kind = LIRKind.combine(a, b);

        RegisterValue rax = moveToReg(AMD64.rax, a);
        RegisterValue rdx = AMD64.rdx.asValue(kind);
        getLIRGen().append(new AMD64ClearRegisterOp(size, rdx));
        return getLIRGen().append(new AMD64MulDivOp(AMD64MOp.DIV, size, kind, rdx, rax, getLIRGen().asAllocatable(b), state));
    }

    public Value[] emitIntegerDivRem(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                op = emitIDIV(DWORD, a, b, state);
                break;
            case QWORD:
                op = emitIDIV(QWORD, a, b, state);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return new Value[]{getLIRGen().emitMove(op.getQuotient()), getLIRGen().emitMove(op.getRemainder())};
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                AMD64MulDivOp op = emitIDIV(DWORD, a, b, state);
                return getLIRGen().emitMove(op.getQuotient());
            case QWORD:
                AMD64MulDivOp lop = emitIDIV(QWORD, a, b, state);
                return getLIRGen().emitMove(lop.getQuotient());
            case SINGLE:
                return emitBinary(LIRKind.combine(a, b), SSEOp.DIV, SS, false, a, b);
            case DOUBLE:
                return emitBinary(LIRKind.combine(a, b), SSEOp.DIV, SD, false, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
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
                getLIRGen().append(new FPDivRemOp(FREM, result, getLIRGen().load(a), getLIRGen().load(b)));
                return result;
            }
            case DOUBLE: {
                Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
                getLIRGen().append(new FPDivRemOp(DREM, result, getLIRGen().load(a), getLIRGen().load(b)));
                return result;
            }
            default:
                throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, OR, DWORD, true, a, b, false);
            case QWORD:
                return emitBinary(resultKind, OR, QWORD, true, a, b, false);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.OR, PS, true, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.OR, PD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitBinary(resultKind, XOR, DWORD, true, a, b, false);
            case QWORD:
                return emitBinary(resultKind, XOR, QWORD, true, a, b, false);
            case SINGLE:
                return emitBinary(resultKind, SSEOp.XOR, PS, true, a, b);
            case DOUBLE:
                return emitBinary(resultKind, SSEOp.XOR, PD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private Variable emitShift(AMD64Shift op, OperandSize size, Value a, Value b) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a, b).changeType(a.getPlatformKind()));
        AllocatableValue input = getLIRGen().asAllocatable(a);
        if (isJavaConstant(b)) {
            JavaConstant c = asJavaConstant(b);
            if (c.asLong() == 1) {
                getLIRGen().append(new AMD64Unary.MOp(op.m1Op, size, result, input));
            } else {
                /*
                 * c is implicitly masked to 5 or 6 bits by the CPU, so casting it to (int) is
                 * always correct, even without the NumUtil.is32bit() test.
                 */
                getLIRGen().append(new AMD64Binary.ConstOp(op.miOp, size, result, input, (int) c.asLong()));
            }
        } else {
            getLIRGen().emitMove(RCX_I, b);
            getLIRGen().append(new AMD64ShiftOp(op.mcOp, size, result, input, RCX_I));
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
                throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public Variable emitRol(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(ROL, DWORD, a, b);
            case QWORD:
                return emitShift(ROL, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public Variable emitRor(Value a, Value b) {
        switch ((AMD64Kind) a.getPlatformKind()) {
            case DWORD:
                return emitShift(ROR, DWORD, a, b);
            case QWORD:
                return emitShift(ROR, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private AllocatableValue emitConvertOp(LIRKind kind, AMD64RMOp op, OperandSize size, Value input) {
        Variable result = getLIRGen().newVariable(kind);
        getLIRGen().append(new AMD64Unary.RMOp(op, size, result, getLIRGen().asAllocatable(input)));
        return result;
    }

    private AllocatableValue emitConvertOp(LIRKind kind, AMD64MROp op, OperandSize size, Value input) {
        Variable result = getLIRGen().newVariable(kind);
        getLIRGen().append(new AMD64Unary.MROp(op, size, result, getLIRGen().asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        LIRKind from = inputVal.getLIRKind();
        if (to.equals(from)) {
            return inputVal;
        }

        AllocatableValue input = getLIRGen().asAllocatable(inputVal);
        /*
         * Conversions between integer to floating point types require moves between CPU and FPU
         * registers.
         */
        AMD64Kind fromKind = (AMD64Kind) from.getPlatformKind();
        switch ((AMD64Kind) to.getPlatformKind()) {
            case DWORD:
                switch (fromKind) {
                    case SINGLE:
                        return emitConvertOp(to, AMD64MROp.MOVD, DWORD, input);
                }
                break;
            case QWORD:
                switch (fromKind) {
                    case DOUBLE:
                        return emitConvertOp(to, AMD64MROp.MOVQ, QWORD, input);
                }
                break;
            case SINGLE:
                switch (fromKind) {
                    case DWORD:
                        return emitConvertOp(to, AMD64RMOp.MOVD, DWORD, input);
                }
                break;
            case DOUBLE:
                switch (fromKind) {
                    case QWORD:
                        return emitConvertOp(to, AMD64RMOp.MOVQ, QWORD, input);
                }
                break;
        }
        throw JVMCIError.shouldNotReachHere();
    }

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
                throw JVMCIError.shouldNotReachHere();
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
                    throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
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
                    throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
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
            getLIRGen().append(new AMD64Binary.DataOp(AND.getRMOpcode(QWORD), QWORD, result, getLIRGen().asAllocatable(inputVal), JavaConstant.forLong(mask)));
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
            getLIRGen().append(new AMD64Binary.DataOp(AND.getRMOpcode(DWORD), DWORD, result, getLIRGen().asAllocatable(inputVal), mask));
            return result;
        }
    }

    @Override
    public Variable emitBitCount(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64Unary.RMOp(POPCNT, QWORD, result, getLIRGen().asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64Unary.RMOp(POPCNT, DWORD, result, getLIRGen().asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Variable emitBitScanForward(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        getLIRGen().append(new AMD64Unary.RMOp(BSF, QWORD, result, getLIRGen().asAllocatable(value)));
        return result;
    }

    @Override
    public Variable emitBitScanReverse(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64Unary.RMOp(BSR, QWORD, result, getLIRGen().asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64Unary.RMOp(BSR, DWORD, result, getLIRGen().asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Value emitCountLeadingZeros(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64Unary.RMOp(LZCNT, QWORD, result, getLIRGen().asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64Unary.RMOp(LZCNT, DWORD, result, getLIRGen().asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Value emitCountTrailingZeros(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AMD64Kind.DWORD));
        assert ((AMD64Kind) value.getPlatformKind()).isInteger();
        if (value.getPlatformKind() == AMD64Kind.QWORD) {
            getLIRGen().append(new AMD64Unary.RMOp(TZCNT, QWORD, result, getLIRGen().asAllocatable(value)));
        } else {
            getLIRGen().append(new AMD64Unary.RMOp(TZCNT, DWORD, result, getLIRGen().asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Value emitMathAbs(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case SINGLE:
                getLIRGen().append(new AMD64Binary.DataOp(SSEOp.AND, PS, result, getLIRGen().asAllocatable(input), JavaConstant.forFloat(Float.intBitsToFloat(0x7FFFFFFF)), 16));
                break;
            case DOUBLE:
                getLIRGen().append(new AMD64Binary.DataOp(SSEOp.AND, PD, result, getLIRGen().asAllocatable(input), JavaConstant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)), 16));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        switch ((AMD64Kind) input.getPlatformKind()) {
            case SINGLE:
                getLIRGen().append(new AMD64Unary.RMOp(SSEOp.SQRT, SS, result, getLIRGen().asAllocatable(input)));
                break;
            case DOUBLE:
                getLIRGen().append(new AMD64Unary.RMOp(SSEOp.SQRT, SD, result, getLIRGen().asAllocatable(input)));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AMD64MathIntrinsicOp(base10 ? LOG10 : LOG, result, getLIRGen().asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathCos(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AMD64MathIntrinsicOp(COS, result, getLIRGen().asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathSin(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AMD64MathIntrinsicOp(SIN, result, getLIRGen().asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathTan(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AMD64MathIntrinsicOp(TAN, result, getLIRGen().asAllocatable(input)));
        return result;
    }
}
