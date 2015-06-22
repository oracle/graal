/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static jdk.internal.jvmci.code.ValueUtil.*;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;

@Opcode("BSWAP")
public final class SPARCByteSwapOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
    public static final LIRInstructionClass<SPARCByteSwapOp> TYPE = LIRInstructionClass.create(SPARCByteSwapOp.class);

    @Def({REG, HINT}) protected Value result;
    @Use({REG}) protected Value input;
    @Temp({REG}) protected Value tempIndex;
    @Use({STACK}) protected StackSlotValue tmpSlot;

    public SPARCByteSwapOp(LIRGeneratorTool tool, Value result, Value input) {
        super(TYPE);
        this.result = result;
        this.input = input;
        this.tmpSlot = tool.getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(Kind.Long));
        this.tempIndex = tool.newVariable(LIRKind.value(Kind.Long));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        SPARCMove.move(crb, masm, tmpSlot, input, SPARCDelayedControlTransfer.DUMMY);
        SPARCAddress addr = (SPARCAddress) crb.asAddress(tmpSlot);
        if (addr.getIndex().equals(Register.None)) {
            Register tempReg = ValueUtil.asLongReg(tempIndex);
            new SPARCMacroAssembler.Setx(addr.getDisplacement(), tempReg, false).emit(masm);
            addr = new SPARCAddress(addr.getBase(), tempReg);
        }
        delayedControlTransfer.emitControlTransfer(crb, masm);
        switch (input.getKind()) {
            case Int:
                masm.lduwa(addr.getBase(), addr.getIndex(), asIntReg(result), Asi.ASI_PRIMARY_LITTLE);
                break;
            case Long:
                masm.ldxa(addr.getBase(), addr.getIndex(), asLongReg(result), Asi.ASI_PRIMARY_LITTLE);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }
}
