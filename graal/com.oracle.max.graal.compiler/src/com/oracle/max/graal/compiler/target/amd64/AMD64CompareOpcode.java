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

public enum AMD64CompareOpcode implements LIROpcode {
    ICMP, LCMP, ACMP, FCMP, DCMP;

    public LIRInstruction create(CiVariable left, CiValue right) {
        assert (name().startsWith("I") && left.kind == CiKind.Int && right.kind.stackKind() == CiKind.Int)
            || (name().startsWith("I") && left.kind == CiKind.Jsr && right.kind == CiKind.Jsr)
            || (name().startsWith("L") && left.kind == CiKind.Long && right.kind == CiKind.Long)
            || (name().startsWith("A") && left.kind == CiKind.Object && right.kind == CiKind.Object)
            || (name().startsWith("F") && left.kind == CiKind.Float && right.kind == CiKind.Float)
            || (name().startsWith("D") && left.kind == CiKind.Double && right.kind == CiKind.Double) : "left.kind=" + left.kind + ", right.kind=" + right.kind;
        CiValue[] inputs = new CiValue[] {left, right};

        return new AMD64LIRInstruction(this, CiValue.IllegalValue, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                CiValue left = input(0);
                CiValue right = input(1);
                emit(tasm, masm, left, right);
            }

            @Override
            public boolean inputCanBeMemory(int index) {
                return index == 1;
            }
        };
    }

    protected void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue left, CiValue right) {
        CiRegister lreg = tasm.asRegister(left);
        if (right.isRegister()) {
            CiRegister rreg = tasm.asRegister(right);
            switch (this) {
                case ICMP: masm.cmpl(lreg, rreg); break;
                case LCMP: masm.cmpq(lreg, rreg); break;
                case ACMP: masm.cmpptr(lreg, rreg); break;
                case FCMP: masm.ucomiss(lreg, rreg); break;
                case DCMP: masm.ucomisd(lreg, rreg); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else if (right.isConstant()) {
            switch (this) {
                case ICMP: masm.cmpl(lreg, tasm.asIntConst(right)); break;
                case LCMP: masm.cmpq(lreg, tasm.asIntConst(right)); break;
                case ACMP:
                    if (((CiConstant) right).isNull()) {
                        masm.cmpq(lreg, 0); break;
                    } else {
                        throw Util.shouldNotReachHere("Only null object constants are allowed in comparisons");
                    }
                case FCMP: masm.ucomiss(lreg, tasm.asFloatConstRef(right)); break;
                case DCMP: masm.ucomisd(lreg, tasm.asDoubleConstRef(right)); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else {
            CiAddress raddr = tasm.asAddress(right);
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
