/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MIOp;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

public class AMD64HotSpotCompareConstOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotCompareConstOp> TYPE = LIRInstructionClass.create(AMD64HotSpotCompareConstOp.class);

    @Opcode private final AMD64MIOp opcode;

    @Use({REG, STACK}) protected AllocatableValue x;
    protected HotSpotConstant y;

    public AMD64HotSpotCompareConstOp(AMD64MIOp opcode, AllocatableValue x, HotSpotConstant y) {
        super(TYPE);
        this.opcode = opcode;
        this.x = x;
        this.y = y;

        assert y.isCompressed();
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        int imm32;
        if (y instanceof HotSpotMetaspaceConstant) {
            imm32 = (int) ((HotSpotMetaspaceConstant) y).rawValue();
        } else {
            assert y instanceof HotSpotObjectConstant;
            imm32 = 0xDEADDEAD;
        }

        crb.recordInlineDataInCode(y);
        if (isRegister(x)) {
            opcode.emit(masm, DWORD, asRegister(x), imm32);
        } else {
            assert isStackSlot(x);
            opcode.emit(masm, DWORD, (AMD64Address) crb.asAddress(x), imm32);
        }
    }
}
