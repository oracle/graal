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

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;

public enum AMD64DivOpcode implements LIROpcode {
    IDIV, IREM, IUDIV, IUREM,
    LDIV, LREM, LUDIV, LUREM;

    public LIRInstruction create(CiValue result, LIRDebugInfo info, CiValue x, CiValue y) {
        CiValue[] inputs = new CiValue[] {x};
        CiValue[] alives = new CiValue[] {y};
        CiValue[] temps = new CiValue[] {asRegister(result) == AMD64.rax ? AMD64.rdx.asValue(result.kind) : AMD64.rax.asValue(result.kind)};
        CiValue[] outputs = new CiValue[] {result};

        return new AMD64LIRInstruction(this, outputs, info, inputs, alives, temps) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                emit(tasm, masm, output(0), info, input(0), alive(0));
            }
        };
    }

    protected void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, LIRDebugInfo info, CiValue x, CiValue y) {
        // left input in rax, right input in any register but rax and rdx, result quotient in rax, result remainder in rdx
        assert asRegister(x) == AMD64.rax;
        assert differentRegisters(y, AMD64.rax.asValue(), AMD64.rdx.asValue());
        assert (name().endsWith("DIV") && asRegister(result) == AMD64.rax) || (name().endsWith("REM") && asRegister(result) == AMD64.rdx);

        int exceptionOffset;
        switch (this) {
            case IDIV:
            case IREM:
                masm.cdql();
                exceptionOffset = masm.codeBuffer.position();
                masm.idivl(asRegister(y));
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
                    masm.cmpl(asRegister(y), -1);
                    masm.jcc(ConditionFlag.equal, continuation);
                    masm.bind(normalCase);
                }

                masm.cdqq();
                exceptionOffset = masm.codeBuffer.position();
                masm.idivq(asRegister(y));
                masm.bind(continuation);
                break;

            case IUDIV:
            case IUREM:
                // Must zero the high 64-bit word (in RDX) of the dividend
                masm.xorq(AMD64.rdx, AMD64.rdx);
                exceptionOffset = masm.codeBuffer.position();
                masm.divl(asRegister(y));
                break;

            case LUDIV:
            case LUREM:
                // Must zero the high 64-bit word (in RDX) of the dividend
                masm.xorq(AMD64.rdx, AMD64.rdx);
                exceptionOffset = masm.codeBuffer.position();
                masm.divq(asRegister(y));
                break;

            default:
                throw Util.shouldNotReachHere();
        }
        tasm.recordImplicitException(exceptionOffset, info);
    }
}
