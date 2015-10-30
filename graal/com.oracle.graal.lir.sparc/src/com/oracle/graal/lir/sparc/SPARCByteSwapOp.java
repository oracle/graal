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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.HINT;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.STACK;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.UNINITIALIZED;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.sparc.SPARCKind;

import com.oracle.graal.asm.sparc.SPARCAddress;
import com.oracle.graal.asm.sparc.SPARCAssembler.Asi;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.gen.LIRGeneratorTool;

@Opcode("BSWAP")
public final class SPARCByteSwapOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
    public static final LIRInstructionClass<SPARCByteSwapOp> TYPE = LIRInstructionClass.create(SPARCByteSwapOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(3);
    @Def({REG, HINT}) protected Value result;
    @Use({REG}) protected Value input;
    @Temp({REG}) protected Value tempIndex;
    @Use({STACK, UNINITIALIZED}) protected AllocatableValue tmpSlot;

    public SPARCByteSwapOp(LIRGeneratorTool tool, Value result, Value input) {
        super(TYPE, SIZE);
        this.result = result;
        this.input = input;
        this.tmpSlot = tool.getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(XWORD));
        this.tempIndex = tool.newVariable(LIRKind.value(XWORD));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        SPARCAddress addr = (SPARCAddress) crb.asAddress(tmpSlot);
        SPARCMove.emitStore(input, addr, result.getPlatformKind(), SPARCDelayedControlTransfer.DUMMY, null, crb, masm);
        if (addr.getIndex().equals(Register.None)) {
            Register tempReg = ValueUtil.asRegister(tempIndex, XWORD);
            masm.setx(addr.getDisplacement(), tempReg, false);
            addr = new SPARCAddress(addr.getBase(), tempReg);
        }
        getDelayedControlTransfer().emitControlTransfer(crb, masm);
        switch ((SPARCKind) input.getPlatformKind()) {
            case WORD:
                masm.lduwa(addr.getBase(), addr.getIndex(), asRegister(result, WORD), Asi.ASI_PRIMARY_LITTLE);
                break;
            case XWORD:
                masm.ldxa(addr.getBase(), addr.getIndex(), asRegister(result, XWORD), Asi.ASI_PRIMARY_LITTLE);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }
}
