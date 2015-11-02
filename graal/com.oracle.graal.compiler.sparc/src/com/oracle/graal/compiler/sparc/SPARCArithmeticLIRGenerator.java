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

package com.oracle.graal.compiler.sparc;

import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Add;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Addcc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.And;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Mulx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Sdivx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Sllx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Sra;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Srax;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Srl;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Sub;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Subcc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Udivx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Xnor;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Faddd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fadds;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fdivd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fdivs;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fdtos;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fitod;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fitos;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fmuld;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fmuls;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fnegd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fnegs;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fstod;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fxtod;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.UMulxhi;
import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static com.oracle.graal.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.BSF;
import static com.oracle.graal.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.IBSR;
import static com.oracle.graal.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.LBSR;
import static jdk.vm.ci.code.CodeUtil.mask;
import static jdk.vm.ci.meta.JavaConstant.forLong;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARCKind.DOUBLE;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;
import static jdk.vm.ci.sparc.SPARCKind.SINGLE;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARC.CPUFeature;
import jdk.vm.ci.sparc.SPARCKind;

import com.oracle.graal.asm.sparc.SPARCAssembler.Op3s;
import com.oracle.graal.asm.sparc.SPARCAssembler.Opfs;
import com.oracle.graal.compiler.common.calc.FloatConvert;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.ArithmeticLIRGenerator;
import com.oracle.graal.lir.sparc.SPARCArithmetic;
import com.oracle.graal.lir.sparc.SPARCArithmetic.FloatConvertOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.MulHighOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.MulHighOp.MulHigh;
import com.oracle.graal.lir.sparc.SPARCArithmetic.RemOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.RemOp.Rem;
import com.oracle.graal.lir.sparc.SPARCArithmetic.SPARCIMulccOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.SPARCLMulccOp;
import com.oracle.graal.lir.sparc.SPARCBitManipulationOp;
import com.oracle.graal.lir.sparc.SPARCMove.MoveFpGp;
import com.oracle.graal.lir.sparc.SPARCOP3Op;
import com.oracle.graal.lir.sparc.SPARCOPFOp;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public class SPARCArithmeticLIRGenerator extends ArithmeticLIRGenerator {

    @Override
    public SPARCLIRGenerator getLIRGen() {
        return (SPARCLIRGenerator) super.getLIRGen();
    }

    @Override
    public Variable emitBitCount(Value operand) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(operand).changeType(SPARCKind.WORD));
        Value usedOperand = operand;
        if (operand.getPlatformKind() == SPARCKind.WORD) { // Zero extend
            usedOperand = getLIRGen().newVariable(operand.getLIRKind());
            getLIRGen().append(new SPARCOP3Op(Op3s.Srl, operand, SPARC.g0.asValue(), usedOperand));
        }
        getLIRGen().append(new SPARCOP3Op(Op3s.Popc, SPARC.g0.asValue(), usedOperand, result));
        return result;
    }

    @Override
    public Variable emitBitScanForward(Value operand) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(operand).changeType(SPARCKind.WORD));
        getLIRGen().append(new SPARCBitManipulationOp(BSF, result, getLIRGen().asAllocatable(operand), getLIRGen()));
        return result;
    }

    @Override
    public Variable emitBitScanReverse(Value operand) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(operand).changeType(SPARCKind.WORD));
        if (operand.getPlatformKind() == SPARCKind.XWORD) {
            getLIRGen().append(new SPARCBitManipulationOp(LBSR, result, getLIRGen().asAllocatable(operand), getLIRGen()));
        } else {
            getLIRGen().append(new SPARCBitManipulationOp(IBSR, result, getLIRGen().asAllocatable(operand), getLIRGen()));
        }
        return result;
    }

    @Override
    public Value emitMathAbs(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        SPARCKind kind = (SPARCKind) input.getPlatformKind();
        Opfs opf;
        switch (kind) {
            case SINGLE:
                opf = Opfs.Fabss;
                break;
            case DOUBLE:
                opf = Opfs.Fabsd;
                break;
            default:
                throw JVMCIError.shouldNotReachHere("Input kind: " + kind);
        }
        getLIRGen().append(new SPARCOPFOp(opf, g0.asValue(), input, result));
        return result;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        SPARCKind kind = (SPARCKind) input.getPlatformKind();
        Opfs opf;
        switch (kind) {
            case SINGLE:
                opf = Opfs.Fsqrts;
                break;
            case DOUBLE:
                opf = Opfs.Fsqrtd;
                break;
            default:
                throw JVMCIError.shouldNotReachHere("Input kind: " + kind);
        }
        getLIRGen().append(new SPARCOPFOp(opf, g0.asValue(), input, result));
        return result;
    }

    @Override
    public Value emitNegate(Value input) {
        PlatformKind inputKind = input.getPlatformKind();
        if (isNumericInteger(inputKind)) {
            return emitUnary(Sub, input);
        } else {
            return emitUnary(inputKind.equals(DOUBLE) ? Fnegd : Fnegs, input);
        }
    }

    @Override
    public Value emitNot(Value input) {
        return emitUnary(Xnor, input);
    }

    private Variable emitUnary(Opfs opf, Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new SPARCOPFOp(opf, g0.asValue(), input, result));
        return result;
    }

    private Variable emitUnary(Op3s op3, Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(SPARCOP3Op.newUnary(op3, input, result));
        return result;
    }

    private Variable emitBinary(LIRKind resultKind, Opfs opf, Value a, Value b) {
        return emitBinary(resultKind, opf, a, b, null);
    }

    private Variable emitBinary(LIRKind resultKind, Opfs opf, Value a, Value b, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(resultKind);
        if (opf.isCommutative() && isJavaConstant(a) && getLIRGen().getMoveFactory().canInlineConstant(asJavaConstant(a))) {
            getLIRGen().append(new SPARCOPFOp(opf, b, a, result, state));
        } else {
            getLIRGen().append(new SPARCOPFOp(opf, a, b, result, state));
        }
        return result;
    }

    private Variable emitBinary(LIRKind resultKind, Op3s op3, Value a, int b) {
        return emitBinary(resultKind, op3, a, new ConstantValue(LIRKind.value(WORD), JavaConstant.forInt(b)));
    }

    private Variable emitBinary(LIRKind resultKind, Op3s op3, Value a, Value b) {
        return emitBinary(resultKind, op3, a, b, null);
    }

    private Variable emitBinary(LIRKind resultKind, Op3s op3, Value a, Value b, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(resultKind);
        if (op3.isCommutative() && isJavaConstant(a) && getLIRGen().getMoveFactory().canInlineConstant(asJavaConstant(a))) {
            getLIRGen().append(new SPARCOP3Op(op3, getLIRGen().load(b), a, result, state));
        } else {
            getLIRGen().append(new SPARCOP3Op(op3, getLIRGen().load(a), b, result, state));
        }
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        return ((SPARCKind) kind).isInteger();
    }

    @Override
    public Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            return emitBinary(resultKind, setFlags ? Addcc : Add, a, b);
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Faddd : Fadds, a, b);
        }
    }

    @Override
    public Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            return emitBinary(resultKind, setFlags ? Subcc : Sub, a, b);
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Opfs.Fsubd : Opfs.Fsubs, a, b);
        }
    }

    @Override
    public Variable emitMul(Value a, Value b, boolean setFlags) {
        LIRKind resultKind = LIRKind.combine(a, b);
        PlatformKind aKind = a.getPlatformKind();
        if (isNumericInteger(aKind)) {
            if (setFlags) {
                Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
                if (aKind == XWORD) {
                    getLIRGen().append(new SPARCLMulccOp(result, getLIRGen().load(a), getLIRGen().load(b), getLIRGen()));
                } else if (aKind == WORD) {
                    getLIRGen().append(new SPARCIMulccOp(result, getLIRGen().load(a), getLIRGen().load(b)));
                } else {
                    throw JVMCIError.shouldNotReachHere();
                }
                return result;
            } else {
                return emitBinary(resultKind, setFlags ? Op3s.Mulscc : Op3s.Mulx, a, b);
            }
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Fmuld : Fmuls, a, b);
        }
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        MulHigh opcode;
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                opcode = MulHigh.IMUL;
                break;
            case XWORD:
                opcode = MulHigh.LMUL;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitMulHigh(opcode, a, b);
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                Value aExtended = emitBinary(LIRKind.combine(a), Srl, a, 0);
                Value bExtended = emitBinary(LIRKind.combine(b), Srl, b, 0);
                Value result = emitBinary(LIRKind.combine(a, b), Mulx, aExtended, bExtended);
                return emitBinary(LIRKind.combine(a, b), Srax, result, WORD.getSizeInBits());
            case XWORD:
                return emitBinary(LIRKind.combine(a, b), UMulxhi, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private Value emitMulHigh(MulHigh opcode, Value a, Value b) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
        MulHighOp mulHigh = new MulHighOp(opcode, getLIRGen().load(a), getLIRGen().load(b), result, getLIRGen().newVariable(LIRKind.combine(a, b)));
        getLIRGen().append(mulHigh);
        return result;
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        LIRKind resultKind = LIRKind.combine(a, b);
        PlatformKind aKind = a.getPlatformKind();
        PlatformKind bKind = b.getPlatformKind();
        if (isJavaConstant(b) && asJavaConstant(b).isDefaultForKind()) { // Div by zero
            Value zero = SPARC.g0.asValue(LIRKind.value(SPARCKind.WORD));
            return emitBinary(resultKind, Op3s.Sdivx, zero, zero, state);
        } else if (isNumericInteger(aKind)) {
            Value fixedA = emitSignExtend(a, aKind.getSizeInBytes() * 8, 64);
            Value fixedB = emitSignExtend(b, bKind.getSizeInBytes() * 8, 64);
            return emitBinary(resultKind, Op3s.Sdivx, fixedA, fixedB, state);
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Opfs.Fdivd : Opfs.Fdivs, a, b, state);
        }
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
        Value aLoaded;
        Value bLoaded;
        Variable q1; // Intermediate values
        Variable q2;
        Variable q3;
        Variable q4;
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        switch (aKind) {
            case WORD:
                q1 = emitBinary(result.getLIRKind(), Sra, a, g0.asValue(LIRKind.value(WORD)));
                q2 = emitBinary(q1.getLIRKind(), Sdivx, q1, b, state);
                q3 = emitBinary(q2.getLIRKind(), Op3s.Mulx, q2, b);
                result = emitSub(q1, q3, false);
                break;
            case XWORD:
                aLoaded = getLIRGen().load(a); // Reuse the loaded value
                q1 = emitBinary(result.getLIRKind(), Sdivx, aLoaded, b, state);
                q2 = emitBinary(result.getLIRKind(), Mulx, q1, b);
                result = emitSub(aLoaded, q2, false);
                break;
            case SINGLE:
                aLoaded = getLIRGen().load(a);
                bLoaded = getLIRGen().load(b);
                q1 = emitBinary(result.getLIRKind(), Fdivs, aLoaded, bLoaded, state);
                q2 = getLIRGen().newVariable(LIRKind.value(aKind));
                getLIRGen().append(new FloatConvertOp(FloatConvertOp.FloatConvert.F2I, q1, q2));
                q3 = emitUnary(Fitos, q2);
                q4 = emitBinary(LIRKind.value(aKind), Fmuls, q3, bLoaded);
                result = emitSub(aLoaded, q4, false);
                break;
            case DOUBLE:
                aLoaded = getLIRGen().load(a);
                bLoaded = getLIRGen().load(b);
                q1 = emitBinary(result.getLIRKind(), Fdivd, aLoaded, bLoaded, state);
                q2 = getLIRGen().newVariable(LIRKind.value(aKind));
                getLIRGen().append(new FloatConvertOp(FloatConvertOp.FloatConvert.D2L, q1, q2));
                q3 = emitUnary(Fxtod, q2);
                q4 = emitBinary(result.getLIRKind(), Fmuld, q3, bLoaded);
                result = emitSub(aLoaded, q4, false);
                break;
            default:
                throw JVMCIError.shouldNotReachHere("missing: " + a.getPlatformKind());
        }
        return result;
    }

    @Override
    public Value emitURem(Value a, Value b, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
        Variable scratch1 = getLIRGen().newVariable(LIRKind.combine(a, b));
        Variable scratch2 = getLIRGen().newVariable(LIRKind.combine(a, b));
        Rem opcode;
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                opcode = Rem.IUREM;
                break;
            case XWORD:
                opcode = Rem.LUREM;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        getLIRGen().append(new RemOp(opcode, result, getLIRGen().load(a), getLIRGen().load(b), scratch1, scratch2, state));
        return result;

    }

    @Override
    public Value emitUDiv(Value a, Value b, LIRFrameState state) {
        Value actualA = a;
        Value actualB = b;
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                actualA = emitZeroExtend(actualA, 32, 64);
                actualB = emitZeroExtend(actualB, 32, 64);
                break;
            case XWORD:
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitBinary(LIRKind.combine(actualA, actualB), Udivx, actualA, actualB, state);
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitBinary(resultKind, Op3s.And, a, b);
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitBinary(resultKind, Op3s.Or, a, b);
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitBinary(resultKind, Op3s.Xor, a, b);
    }

    @Override
    public Variable emitShl(Value a, Value b) {
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        LIRKind resultKind = LIRKind.combine(a, b).changeType(aKind);
        Op3s op;
        switch (aKind) {
            case WORD:
                op = Op3s.Sll;
                break;
            case XWORD:
                op = Op3s.Sllx;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitBinary(resultKind, op, a, b);
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        LIRKind resultKind = LIRKind.combine(a, b).changeType(aKind);
        Op3s op;
        switch (aKind) {
            case WORD:
                op = Op3s.Sra;
                break;
            case XWORD:
                op = Op3s.Srax;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitBinary(resultKind, op, a, b);
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        LIRKind resultKind = LIRKind.combine(a, b).changeType(aKind);
        Op3s op;
        switch (aKind) {
            case WORD:
                op = Op3s.Srl;
                break;
            case XWORD:
                op = Op3s.Srlx;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitBinary(resultKind, op, a, b);
    }

    private AllocatableValue emitConvertMove(LIRKind kind, AllocatableValue input) {
        Variable result = getLIRGen().newVariable(kind);
        getLIRGen().emitMove(result, input);
        return result;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        AllocatableValue input = getLIRGen().asAllocatable(inputVal);
        Value result;
        switch (op) {
            case D2F:
                result = getLIRGen().newVariable(LIRKind.combine(inputVal).changeType(SINGLE));
                getLIRGen().append(new SPARCOPFOp(Fdtos, inputVal, result));
                break;
            case F2D:
                result = getLIRGen().newVariable(LIRKind.combine(inputVal).changeType(DOUBLE));
                getLIRGen().append(new SPARCOPFOp(Fstod, inputVal, result));
                break;
            case I2F: {
                AllocatableValue intEncodedFloatReg = getLIRGen().newVariable(LIRKind.combine(input).changeType(SINGLE));
                result = getLIRGen().newVariable(intEncodedFloatReg.getLIRKind());
                moveBetweenFpGp(intEncodedFloatReg, input);
                getLIRGen().append(new SPARCOPFOp(Fitos, intEncodedFloatReg, result));
                break;
            }
            case I2D: {
                // Unfortunately we must do int -> float -> double because fitod has float
                // and double encoding in one instruction
                AllocatableValue convertedFloatReg = getLIRGen().newVariable(LIRKind.combine(input).changeType(SINGLE));
                result = getLIRGen().newVariable(LIRKind.combine(input).changeType(DOUBLE));
                moveBetweenFpGp(convertedFloatReg, input);
                getLIRGen().append(new SPARCOPFOp(Fitod, convertedFloatReg, result));
                break;
            }
            case L2D: {
                AllocatableValue longEncodedDoubleReg = getLIRGen().newVariable(LIRKind.combine(input).changeType(DOUBLE));
                moveBetweenFpGp(longEncodedDoubleReg, input);
                AllocatableValue convertedDoubleReg = getLIRGen().newVariable(longEncodedDoubleReg.getLIRKind());
                getLIRGen().append(new SPARCOPFOp(Fxtod, longEncodedDoubleReg, convertedDoubleReg));
                result = convertedDoubleReg;
                break;
            }
            case D2I: {
                AllocatableValue convertedFloatReg = getLIRGen().newVariable(LIRKind.combine(input).changeType(SINGLE));
                getLIRGen().append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.D2I, input, convertedFloatReg));
                AllocatableValue convertedIntReg = getLIRGen().newVariable(LIRKind.combine(convertedFloatReg).changeType(WORD));
                moveBetweenFpGp(convertedIntReg, convertedFloatReg);
                result = convertedIntReg;
                break;
            }
            case F2L: {
                AllocatableValue convertedDoubleReg = getLIRGen().newVariable(LIRKind.combine(input).changeType(DOUBLE));
                getLIRGen().append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.F2L, input, convertedDoubleReg));
                AllocatableValue convertedLongReg = getLIRGen().newVariable(LIRKind.combine(convertedDoubleReg).changeType(XWORD));
                moveBetweenFpGp(convertedLongReg, convertedDoubleReg);
                result = convertedLongReg;
                break;
            }
            case F2I: {
                AllocatableValue convertedFloatReg = getLIRGen().newVariable(LIRKind.combine(input).changeType(SINGLE));
                getLIRGen().append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.F2I, input, convertedFloatReg));
                AllocatableValue convertedIntReg = getLIRGen().newVariable(LIRKind.combine(convertedFloatReg).changeType(WORD));
                moveBetweenFpGp(convertedIntReg, convertedFloatReg);
                result = convertedIntReg;
                break;
            }
            case D2L: {
                AllocatableValue convertedDoubleReg = getLIRGen().newVariable(LIRKind.combine(input).changeType(DOUBLE));
                getLIRGen().append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.D2L, input, convertedDoubleReg));
                AllocatableValue convertedLongReg = getLIRGen().newVariable(LIRKind.combine(convertedDoubleReg).changeType(XWORD));
                moveBetweenFpGp(convertedLongReg, convertedDoubleReg);
                result = convertedLongReg;
                break;
            }
            case L2F: {
                AllocatableValue convertedDoubleReg = getLIRGen().newVariable(LIRKind.combine(input).changeType(DOUBLE));
                result = getLIRGen().newVariable(LIRKind.combine(input).changeType(SINGLE));
                moveBetweenFpGp(convertedDoubleReg, input);
                getLIRGen().append(new SPARCOPFOp(Opfs.Fxtos, convertedDoubleReg, result));
                break;
            }
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    private void moveBetweenFpGp(AllocatableValue dst, AllocatableValue src) {
        AllocatableValue tempSlot;
        if (getLIRGen().getArchitecture().getFeatures().contains(CPUFeature.VIS3)) {
            tempSlot = AllocatableValue.ILLEGAL;
        } else {
            tempSlot = getLIRGen().getTempSlot(LIRKind.value(XWORD));
        }
        getLIRGen().append(new MoveFpGp(dst, src, tempSlot));
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getPlatformKind() == XWORD && bits <= 32) {
            LIRKind resultKind = LIRKind.combine(inputVal).changeType(WORD);
            Variable result = getLIRGen().newVariable(resultKind);
            getLIRGen().emitMove(result, inputVal);
            return result;
        } else {
            return inputVal;
        }
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= XWORD.getSizeInBits();
        LIRKind shiftKind = LIRKind.value(WORD);
        LIRKind resultKind = LIRKind.combine(inputVal).changeType(toBits > 32 ? XWORD : WORD);
        Value result;
        int shiftCount = XWORD.getSizeInBits() - fromBits;
        if (fromBits == toBits) {
            result = inputVal;
        } else if (isJavaConstant(inputVal)) {
            JavaConstant javaConstant = asJavaConstant(inputVal);
            long constant;
            if (javaConstant.isNull()) {
                constant = 0;
            } else {
                constant = javaConstant.asLong();
            }
            return new ConstantValue(resultKind, JavaConstant.forLong((constant << shiftCount) >> shiftCount));
        } else if (fromBits == WORD.getSizeInBits() && toBits == XWORD.getSizeInBits()) {
            result = getLIRGen().newVariable(resultKind);
            getLIRGen().append(new SPARCOP3Op(Sra, inputVal, SPARC.g0.asValue(LIRKind.value(WORD)), result));
        } else {
            Variable tmp = getLIRGen().newVariable(resultKind.changeType(XWORD));
            result = getLIRGen().newVariable(resultKind);
            getLIRGen().append(new SPARCOP3Op(Sllx, inputVal, new ConstantValue(shiftKind, JavaConstant.forInt(shiftCount)), tmp));
            getLIRGen().append(new SPARCOP3Op(Srax, tmp, new ConstantValue(shiftKind, JavaConstant.forInt(shiftCount)), result));
        }
        return result;
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        }
        Variable result = getLIRGen().newVariable(LIRKind.combine(inputVal).changeType(toBits > WORD.getSizeInBits() ? XWORD : WORD));
        if (fromBits == 32) {
            getLIRGen().append(new SPARCOP3Op(Srl, inputVal, g0.asValue(), result));
        } else {
            Value mask = getLIRGen().emitConstant(LIRKind.value(XWORD), forLong(mask(fromBits)));
            getLIRGen().append(new SPARCOP3Op(And, inputVal, mask, result));
        }
        return result;
    }

    @Override
    public AllocatableValue emitReinterpret(LIRKind to, Value inputVal) {
        SPARCKind fromKind = (SPARCKind) inputVal.getPlatformKind();
        SPARCKind toKind = (SPARCKind) to.getPlatformKind();
        AllocatableValue input = getLIRGen().asAllocatable(inputVal);
        Variable result = getLIRGen().newVariable(to);
        // These cases require a move between CPU and FPU registers:
        if (fromKind.isFloat() != toKind.isFloat()) {
            moveBetweenFpGp(result, input);
            return result;
        } else {
            // Otherwise, just emit an ordinary move instruction.
            // Instructions that move or generate 32-bit register values also set the upper 32
            // bits of the register to zero.
            // Consequently, there is no need for a special zero-extension move.
            return emitConvertMove(to, input);
        }
    }
}
