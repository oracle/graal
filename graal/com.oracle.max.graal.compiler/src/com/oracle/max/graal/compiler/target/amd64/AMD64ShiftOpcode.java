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

public enum AMD64ShiftOpcode implements LIROpcode {
    ISHL, ISHR, UISHR,
    LSHL, LSHR, ULSHR;

    public LIRInstruction create(CiVariable result, CiValue left, CiValue right) {
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
            assert tasm.asRegister(right) == AMD64.rcx;
            switch (this) {
                case ISHL:  masm.shll(dst); break;
                case ISHR:  masm.sarl(dst); break;
                case UISHR: masm.shrl(dst); break;
                case LSHL:  masm.shlq(dst); break;
                case LSHR:  masm.sarq(dst); break;
                case ULSHR: masm.shrq(dst); break;
                default:    throw Util.shouldNotReachHere();
            }
        } else {
            switch (this) {
                case ISHL:  masm.shll(dst, tasm.asIntConst(right) & 31); break;
                case ISHR:  masm.sarl(dst, tasm.asIntConst(right) & 31); break;
                case UISHR: masm.shrl(dst, tasm.asIntConst(right) & 31); break;
                case LSHL:  masm.shlq(dst, tasm.asIntConst(right) & 63); break;
                case LSHR:  masm.sarq(dst, tasm.asIntConst(right) & 63); break;
                case ULSHR: masm.shrq(dst, tasm.asIntConst(right) & 63); break;
                default:   throw Util.shouldNotReachHere();
            }
        }
    }
}
