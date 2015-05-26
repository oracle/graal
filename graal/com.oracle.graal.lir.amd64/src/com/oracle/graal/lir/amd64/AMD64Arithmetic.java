/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.meta.AllocatableValue;
import com.oracle.jvmci.meta.LIRKind;
import static com.oracle.jvmci.code.ValueUtil.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

public enum AMD64Arithmetic {
    FREM,
    DREM;

    public static class FPDivRemOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<FPDivRemOp> TYPE = LIRInstructionClass.create(FPDivRemOp.class);

        @Opcode private final AMD64Arithmetic opcode;
        @Def protected AllocatableValue result;
        @Use protected AllocatableValue x;
        @Use protected AllocatableValue y;
        @Temp protected AllocatableValue raxTemp;

        public FPDivRemOp(AMD64Arithmetic opcode, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.raxTemp = AMD64.rax.asValue(LIRKind.value(Kind.Int));
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Address tmp = new AMD64Address(AMD64.rsp);
            masm.subq(AMD64.rsp, 8);
            if (opcode == FREM) {
                masm.movflt(tmp, asRegister(y));
                masm.flds(tmp);
                masm.movflt(tmp, asRegister(x));
                masm.flds(tmp);
            } else {
                assert opcode == DREM;
                masm.movdbl(tmp, asRegister(y));
                masm.fldd(tmp);
                masm.movdbl(tmp, asRegister(x));
                masm.fldd(tmp);
            }

            Label label = new Label();
            masm.bind(label);
            masm.fprem();
            masm.fwait();
            masm.fnstswAX();
            masm.testl(AMD64.rax, 0x400);
            masm.jcc(ConditionFlag.NotZero, label);
            masm.fxch(1);
            masm.fpop();

            if (opcode == FREM) {
                masm.fstps(tmp);
                masm.movflt(asRegister(result), tmp);
            } else {
                masm.fstpd(tmp);
                masm.movdbl(asRegister(result), tmp);
            }
            masm.addq(AMD64.rsp, 8);
        }

        @Override
        public void verify() {
            super.verify();
            assert (opcode.name().startsWith("F") && result.getKind() == Kind.Float && x.getKind() == Kind.Float && y.getKind() == Kind.Float) ||
                            (opcode.name().startsWith("D") && result.getKind() == Kind.Double && x.getKind() == Kind.Double && y.getKind() == Kind.Double);
        }
    }
}
