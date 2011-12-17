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

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;

public enum AMD64DivOpcode implements LIROpcode {
    IDIV, IREM, UIDIV, UIREM,
    LDIV, LREM, ULDIV, ULREM;

    public LIRInstruction create(CiRegisterValue result, LIRDebugInfo info, CiRegisterValue left, CiVariable right) {
        CiValue[] inputs = new CiValue[] {left};
        CiValue[] alives = new CiValue[] {right};
        CiValue[] temps = new CiValue[] {AMD64.rdx.asValue()};

        return new AMD64LIRInstruction(this, result, info, inputs, alives, temps) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                CiValue left = input(0);
                CiValue right = alive(0);
                emit(tasm, masm, tasm.asRegister(result()), info, tasm.asRegister(left), tasm.asRegister(right));
            }
        };
    }

    protected void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiRegister result, LIRDebugInfo info, CiRegister left, CiRegister right) {
        // left input in rax, right input in any register but rax and rdx, result quotient in rax, result remainder in rdx
        assert left == AMD64.rax;
        assert right != AMD64.rax && right != AMD64.rdx;
        assert (name().endsWith("DIV") && result == AMD64.rax) || (name().endsWith("REM") && result == AMD64.rdx);

        int exceptionOffset;
        switch (this) {
            case IDIV:
            case IREM:
                masm.cdql();
                exceptionOffset = masm.codeBuffer.position();
                masm.idivl(right);
                break;

            case LDIV:
            case LREM:
                Label continuation = new Label();
                if (this == LDIV) {
                    // check for special case of Long.MIN_VALUE / -1
                    Label normalCase = new Label();
                    masm.movq(AMD64.rdx, java.lang.Long.MIN_VALUE);
                    masm.cmpq(AMD64.rax, AMD64.rdx);
                    masm.jcc(ConditionFlag.notEqual, normalCase);
                    masm.cmpl(right, -1);
                    masm.jcc(ConditionFlag.equal, continuation);
                    masm.bind(normalCase);
                }

                masm.cdqq();
                exceptionOffset = masm.codeBuffer.position();
                masm.idivq(right);
                masm.bind(continuation);
                break;

            case UIDIV:
            case UIREM:
                // Must zero the high 64-bit word (in RDX) of the dividend
                masm.xorq(AMD64.rdx, AMD64.rdx);
                exceptionOffset = masm.codeBuffer.position();
                masm.divl(right);
                break;

            case ULDIV:
            case ULREM:
                // Must zero the high 64-bit word (in RDX) of the dividend
                masm.xorq(AMD64.rdx, AMD64.rdx);
                exceptionOffset = masm.codeBuffer.position();
                masm.divq(right);
                break;

            default:
                throw Util.shouldNotReachHere();
        }
        tasm.recordImplicitException(exceptionOffset, info);
    }
}
