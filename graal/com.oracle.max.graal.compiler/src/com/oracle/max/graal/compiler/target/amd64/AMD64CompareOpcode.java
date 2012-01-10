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

public enum AMD64CompareOpcode implements LIROpcode {
    ICMP, LCMP, ACMP, FCMP, DCMP;

    public LIRInstruction create(CiValue x, CiValue y) {
        assert (name().startsWith("I") && x.kind == CiKind.Int && y.kind.stackKind() == CiKind.Int)
            || (name().startsWith("I") && x.kind == CiKind.Jsr && y.kind == CiKind.Jsr)
            || (name().startsWith("L") && x.kind == CiKind.Long && y.kind == CiKind.Long)
            || (name().startsWith("A") && x.kind == CiKind.Object && y.kind == CiKind.Object)
            || (name().startsWith("F") && x.kind == CiKind.Float && y.kind == CiKind.Float)
            || (name().startsWith("D") && x.kind == CiKind.Double && y.kind == CiKind.Double);
        CiValue[] inputs = new CiValue[] {x, y};

        return new AMD64LIRInstruction(this, LIRInstruction.NO_OPERANDS, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                emit(tasm, masm, input(0), input(1));
            }

            @Override
            public EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
                if (mode == OperandMode.Input && index == 1) {
                    return EnumSet.of(OperandFlag.Register, OperandFlag.Stack, OperandFlag.Constant);
                }
                return super.flagsFor(mode, index);
            }
        };
    }

    protected void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue x, CiValue y) {
        CiRegister lreg = asRegister(x);
        if (isRegister(y)) {
            CiRegister rreg = asRegister(y);
            switch (this) {
                case ICMP: masm.cmpl(lreg, rreg); break;
                case LCMP: masm.cmpq(lreg, rreg); break;
                case ACMP: masm.cmpptr(lreg, rreg); break;
                case FCMP: masm.ucomiss(lreg, rreg); break;
                case DCMP: masm.ucomisd(lreg, rreg); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else if (isConstant(y)) {
            switch (this) {
                case ICMP: masm.cmpl(lreg, tasm.asIntConst(y)); break;
                case LCMP: masm.cmpq(lreg, tasm.asIntConst(y)); break;
                case ACMP:
                    if (((CiConstant) y).isNull()) {
                        masm.cmpq(lreg, 0); break;
                    } else {
                        throw Util.shouldNotReachHere("Only null object constants are allowed in comparisons");
                    }
                case FCMP: masm.ucomiss(lreg, tasm.asFloatConstRef(y)); break;
                case DCMP: masm.ucomisd(lreg, tasm.asDoubleConstRef(y)); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else {
            CiAddress raddr = tasm.asAddress(y);
            switch (this) {
                case ICMP: masm.cmpl(lreg, raddr); break;
                case LCMP: masm.cmpq(lreg, raddr); break;
                case ACMP: masm.cmpptr(lreg, raddr); break;
                case FCMP: masm.ucomiss(lreg, raddr); break;
                case DCMP: masm.ucomisd(lreg, raddr); break;
                default:  throw Util.shouldNotReachHere();
            }
        }
    }
}
