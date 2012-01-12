/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.target.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;

public enum AMD64ConvertOpcode implements LIROpcode {
    I2L, L2I, I2B, I2C, I2S,
    F2D, D2F,
    I2F, I2D, F2I, D2I,
    L2F, L2D, F2L, D2L,
    MOV_I2F, MOV_L2D, MOV_F2I, MOV_D2L;

    public LIRInstruction create(CiValue result, CiValue x) {
        CiValue[] inputs = new CiValue[] {x};
        CiValue[] outputs = new CiValue[] {result};

        return new AMD64LIRInstruction(this, outputs, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                emit(tasm, masm, output(0), input(0));
            }

            @Override
            protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
                if (mode == OperandMode.Output && index == 0) {
                    return EnumSet.of(OperandFlag.Register, OperandFlag.RegisterHint);
                }
                return super.flagsFor(mode, index);
            }
        };
    }

    private void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue x) {
        switch (this) {
            case L2I:
                AMD64MoveOpcode.move(tasm, masm, result, x);
                masm.andl(asIntReg(result), 0xFFFFFFFF);
                break;
            case I2B:
                AMD64MoveOpcode.move(tasm, masm, result, x);
                masm.signExtendByte(asIntReg(result));
                break;
            case I2C:
                AMD64MoveOpcode.move(tasm, masm, result, x);
                masm.andl(asIntReg(result), 0xFFFF);
                break;
            case I2S:
                AMD64MoveOpcode.move(tasm, masm, result, x);
                masm.signExtendShort(asIntReg(result));
                break;
            case I2L: masm.movslq(asLongReg(result), asIntReg(x)); break;
            case F2D: masm.cvtss2sd(asDoubleReg(result), asFloatReg(x)); break;
            case D2F: masm.cvtsd2ss(asFloatReg(result), asDoubleReg(x)); break;
            case I2F: masm.cvtsi2ssl(asFloatReg(result), asIntReg(x)); break;
            case I2D: masm.cvtsi2sdl(asDoubleReg(result), asIntReg(x)); break;
            case L2F: masm.cvtsi2ssq(asFloatReg(result), asLongReg(x)); break;
            case L2D: masm.cvtsi2sdq(asDoubleReg(result), asLongReg(x)); break;
            case F2I:
                masm.cvttss2sil(asIntReg(result), asFloatReg(x));
                emitFixup(tasm, masm, result, x);
                break;
            case D2I:
                masm.cvttsd2sil(asIntReg(result), asDoubleReg(x));
                emitFixup(tasm, masm, result, x);
                break;
            case F2L:
                masm.cvttss2siq(asLongReg(result), asFloatReg(x));
                emitFixup(tasm, masm, result, x);
                break;
            case D2L:
                masm.cvttsd2siq(asLongReg(result), asDoubleReg(x));
                emitFixup(tasm, masm, result, x);
                break;
            case MOV_I2F: masm.movdl(asFloatReg(result), asIntReg(x)); break;
            case MOV_L2D: masm.movdq(asDoubleReg(result), asLongReg(x)); break;
            case MOV_F2I: masm.movdl(asIntReg(result), asFloatReg(x)); break;
            case MOV_D2L: masm.movdq(asLongReg(result), asDoubleReg(x)); break;
            default: throw Util.shouldNotReachHere();
        }
    }

    private static void emitFixup(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue x) {
        FixupSlowPath slowPath = new FixupSlowPath(result, x);
        tasm.slowPaths.add(slowPath);
        switch (result.kind) {
            case Int:  masm.cmpl(asIntReg(result),  Integer.MIN_VALUE); break;
            case Long: masm.cmpq(asLongReg(result), tasm.asLongConstRef(CiConstant.forLong(java.lang.Long.MIN_VALUE))); break;
            default:   throw Util.shouldNotReachHere();
        }
        masm.jcc(ConditionFlag.equal, slowPath.start);
        masm.bind(slowPath.continuation);
    }

    private static class FixupSlowPath extends AMD64SlowPath {
        public final Label start = new Label();
        public final Label continuation = new Label();
        private final CiValue result;
        private final CiValue x;

        public FixupSlowPath(CiValue result, CiValue x) {
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            masm.bind(start);
            switch (x.kind) {
                case Float:  masm.ucomiss(asFloatReg(x),  tasm.asFloatConstRef(CiConstant.FLOAT_0)); break;
                case Double: masm.ucomisd(asDoubleReg(x), tasm.asDoubleConstRef(CiConstant.DOUBLE_0)); break;
                default:     throw Util.shouldNotReachHere();
            }
            Label nan = new Label();
            masm.jcc(ConditionFlag.parity, nan);
            masm.jcc(ConditionFlag.below, continuation);

            // input is > 0 -> return maxInt
            // result register already contains 0x80000000, so subtracting 1 gives 0x7fffffff
            switch (result.kind) {
                case Int:  masm.decrementl(asIntReg(result),  1); break;
                case Long: masm.decrementq(asLongReg(result), 1); break;
                default:   throw Util.shouldNotReachHere();
            }
            masm.jmp(continuation);

            // input is NaN -> return 0
            masm.bind(nan);
            masm.xorptr(asRegister(result), asRegister(result));
            masm.jmp(continuation);
        }
    }
}
