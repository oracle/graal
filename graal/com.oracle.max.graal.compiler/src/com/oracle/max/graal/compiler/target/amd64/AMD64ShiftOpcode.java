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

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;

public enum AMD64ShiftOpcode implements LIROpcode {
    ISHL, ISHR, IUSHR,
    LSHL, LSHR, LUSHR;

    public LIRInstruction create(CiValue result, CiValue x, CiValue y) {
        CiValue[] inputs = new CiValue[] {x};
        CiValue[] alives = new CiValue[] {y};
        CiValue[] outputs = new CiValue[] {result};

        return new AMD64LIRInstruction(this, outputs, null, inputs, alives, LIRInstruction.NO_OPERANDS) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                emit(tasm, masm, output(0), input(0), alive(0));
            }

            @Override
            public EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
                if (mode == OperandMode.Input && index == 0) {
                    return EnumSet.of(OperandFlag.Register, OperandFlag.Stack, OperandFlag.Constant);
                } else if (mode == OperandMode.Alive && index == 0) {
                    return EnumSet.of(OperandFlag.Register, OperandFlag.Constant);
                } else if (mode == OperandMode.Output && index == 0) {
                    return EnumSet.of(OperandFlag.Register, OperandFlag.RegisterHint);
                }
                return super.flagsFor(mode, index);
            }
        };
    }

    protected void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue x, CiValue y) {
        assert sameRegister(x, y) || differentRegisters(result, y);
        AMD64MoveOpcode.move(tasm, masm, result, x);

        CiRegister dst = asRegister(result);
        if (isRegister(y)) {
            assert asRegister(y) == AMD64.rcx;
            switch (this) {
                case ISHL:  masm.shll(dst); break;
                case ISHR:  masm.sarl(dst); break;
                case IUSHR: masm.shrl(dst); break;
                case LSHL:  masm.shlq(dst); break;
                case LSHR:  masm.sarq(dst); break;
                case LUSHR: masm.shrq(dst); break;
                default:    throw Util.shouldNotReachHere();
            }
        } else if (isConstant(y)) {
            switch (this) {
                case ISHL:  masm.shll(dst, tasm.asIntConst(y) & 31); break;
                case ISHR:  masm.sarl(dst, tasm.asIntConst(y) & 31); break;
                case IUSHR: masm.shrl(dst, tasm.asIntConst(y) & 31); break;
                case LSHL:  masm.shlq(dst, tasm.asIntConst(y) & 63); break;
                case LSHR:  masm.sarq(dst, tasm.asIntConst(y) & 63); break;
                case LUSHR: masm.shrq(dst, tasm.asIntConst(y) & 63); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else {
            throw Util.shouldNotReachHere();
        }
    }
}
