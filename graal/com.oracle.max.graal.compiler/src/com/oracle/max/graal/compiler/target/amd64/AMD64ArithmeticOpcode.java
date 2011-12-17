/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;

public enum AMD64ArithmeticOpcode implements LIROpcode {
    IADD, ISUB, IAND, IOR, IXOR,
    LADD, LSUB, LAND, LOR, LXOR,
    FADD, FSUB, FMUL, FDIV,
    DADD, DSUB, DMUL, DDIV;

    public LIRInstruction create(CiVariable result, CiValue left, CiValue right) {
        assert (name().startsWith("I") && result.kind == CiKind.Int && left.kind.stackKind() == CiKind.Int && right.kind.stackKind() == CiKind.Int)
            || (name().startsWith("L") && result.kind == CiKind.Long && left.kind == CiKind.Long && right.kind == CiKind.Long)
            || (name().startsWith("F") && result.kind == CiKind.Float && left.kind == CiKind.Float && right.kind == CiKind.Float)
            || (name().startsWith("D") && result.kind == CiKind.Double && left.kind == CiKind.Double && right.kind == CiKind.Double);

        CiValue[] inputs = new CiValue[] {left};
        CiValue[] alives = new CiValue[] {right};

        return new AMD64LIRInstruction(this, result, null, inputs, alives, LIRInstruction.NO_OPERANDS) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                CiValue left = input(0);
                CiValue right = alive(0);
                assert !(right instanceof CiRegisterValue) || tasm.asRegister(result()) != tasm.asRegister(right) : "result and right must be different registers";
                AMD64MoveOpcode.move(tasm, masm, result(), left);
                emit(tasm, masm, result(), right);
            }

            @Override
            public boolean inputCanBeMemory(int index) {
                return true;
            }

            @Override
            public CiValue registerHint() {
                return input(0);
            }
        };
    }

    protected void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue leftAndResult, CiValue right) {
        CiRegister dst = tasm.asRegister(leftAndResult);
        if (right.isRegister()) {
            CiRegister rreg = tasm.asRegister(right);
            switch (this) {
                case IADD: masm.addl(dst,  rreg); break;
                case ISUB: masm.subl(dst,  rreg); break;
                case IAND: masm.andl(dst,  rreg); break;
                case IOR:  masm.orl(dst,   rreg); break;
                case IXOR: masm.xorl(dst,  rreg); break;
                case LADD: masm.addq(dst,  rreg); break;
                case LSUB: masm.subq(dst,  rreg); break;
                case LAND: masm.andq(dst,  rreg); break;
                case LOR:  masm.orq(dst,   rreg); break;
                case LXOR: masm.xorq(dst,  rreg); break;
                case FADD: masm.addss(dst, rreg); break;
                case FSUB: masm.subss(dst, rreg); break;
                case FMUL: masm.mulss(dst, rreg); break;
                case FDIV: masm.divss(dst, rreg); break;
                case DADD: masm.addsd(dst, rreg); break;
                case DSUB: masm.subsd(dst, rreg); break;
                case DMUL: masm.mulsd(dst, rreg); break;
                case DDIV: masm.divsd(dst, rreg); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else if (right.isConstant()) {
            switch (this) {
                case IADD: masm.incrementl(dst, tasm.asIntConst(right)); break;
                case ISUB: masm.decrementl(dst, tasm.asIntConst(right)); break;
                case IAND: masm.andl(dst,  tasm.asIntConst(right)); break;
                case IOR:  masm.orl(dst,   tasm.asIntConst(right)); break;
                case IXOR: masm.xorl(dst,  tasm.asIntConst(right)); break;
                case LADD: masm.addq(dst,  tasm.asIntConst(right)); break;
                case LSUB: masm.subq(dst,  tasm.asIntConst(right)); break;
                case LAND: masm.andq(dst,  tasm.asIntConst(right)); break;
                case LOR:  masm.orq(dst,   tasm.asIntConst(right)); break;
                case LXOR: masm.xorq(dst,  tasm.asIntConst(right)); break;
                case FADD: masm.addss(dst, tasm.asFloatConstRef(right)); break;
                case FSUB: masm.subss(dst, tasm.asFloatConstRef(right)); break;
                case FMUL: masm.mulss(dst, tasm.asFloatConstRef(right)); break;
                case FDIV: masm.divss(dst, tasm.asFloatConstRef(right)); break;
                case DADD: masm.addsd(dst, tasm.asDoubleConstRef(right)); break;
                case DSUB: masm.subsd(dst, tasm.asDoubleConstRef(right)); break;
                case DMUL: masm.mulsd(dst, tasm.asDoubleConstRef(right)); break;
                case DDIV: masm.divsd(dst, tasm.asDoubleConstRef(right)); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else {
            CiAddress raddr = tasm.asAddress(right);
            switch (this) {
                case IADD: masm.addl(dst,  raddr); break;
                case ISUB: masm.subl(dst,  raddr); break;
                case IAND: masm.andl(dst,  raddr); break;
                case IOR:  masm.orl(dst,   raddr); break;
                case IXOR: masm.xorl(dst,  raddr); break;
                case LADD: masm.addq(dst,  raddr); break;
                case LSUB: masm.subq(dst,  raddr); break;
                case LAND: masm.andq(dst,  raddr); break;
                case LOR:  masm.orq(dst,   raddr); break;
                case LXOR: masm.xorq(dst,  raddr); break;
                case FADD: masm.addss(dst, raddr); break;
                case FSUB: masm.subss(dst, raddr); break;
                case FMUL: masm.mulss(dst, raddr); break;
                case FDIV: masm.divss(dst, raddr); break;
                case DADD: masm.addsd(dst, raddr); break;
                case DSUB: masm.subsd(dst, raddr); break;
                case DMUL: masm.mulsd(dst, raddr); break;
                case DDIV: masm.divsd(dst, raddr); break;
                default:  throw Util.shouldNotReachHere();
            }
        }
    }
}
