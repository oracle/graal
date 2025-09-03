/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.graal.aarch64;

import static com.oracle.svm.core.graal.aarch64.SubstrateAArch64RegisterConfig.fp;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.aarch64.AArch64BlockEndOp;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Opcode("FAR_RETURN")
public final class AArch64FarReturnOp extends AArch64BlockEndOp {
    public static final LIRInstructionClass<AArch64FarReturnOp> TYPE = LIRInstructionClass.create(AArch64FarReturnOp.class);

    @Use({REG, ILLEGAL}) AllocatableValue result;
    @Use(REG) AllocatableValue sp;
    @Use(REG) AllocatableValue ip;
    private final boolean fromMethodWithCalleeSavedRegisters;

    public AArch64FarReturnOp(AllocatableValue result, AllocatableValue sp, AllocatableValue ip, boolean fromMethodWithCalleeSavedRegisters) {
        super(TYPE);
        this.result = result;
        this.sp = sp;
        this.ip = ip;
        this.fromMethodWithCalleeSavedRegisters = fromMethodWithCalleeSavedRegisters;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        assert sp.getPlatformKind().getSizeInBytes() == FrameAccess.wordSize() && FrameAccess.wordSize() == Long.BYTES : Assertions.errorMessage(sp.getPlatformKind().getSizeInBytes(),
                        FrameAccess.wordSize());

        if (!SubstrateOptions.PreserveFramePointer.getValue() && !fromMethodWithCalleeSavedRegisters) {
            /* No need to restore anything in the frame of the new stack pointer. */
            masm.mov(64, AArch64.sp, asRegister(sp));
            returnTo(asRegister(ip), masm);
            return;
        }

        /*
         * Check whether we are staying within the same frame.
         */
        Label notSameFrame = new Label();
        masm.cmp(64, AArch64.sp, asRegister(sp));
        masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, notSameFrame);

        /*
         * If within the same frame then no additional action is needed.
         */
        returnTo(asRegister(ip), masm);

        /*
         * Otherwise we first switch the stack pointer to point to the value of the lowest value
         * which must be restored.
         */
        masm.bind(notSameFrame);

        /*
         * The callee frame will always reserve space for the return address and frame pointer.
         */
        int minCalleeFrameSize = FrameAccess.wordSize() * 2;
        /*
         * Restoring the callee saved registers may overwrite the register that holds the new
         * instruction pointer (ip). We therefore leverage a scratch register.
         */
        try (AArch64MacroAssembler.ScratchRegister scratch1 = masm.getScratchRegister()) {
            Register ipScratch = scratch1.getRegister();
            Register ipRegister;
            if (fromMethodWithCalleeSavedRegisters) {
                /*
                 * First move sp to the bottom of the callee save area.
                 *
                 * We also reserve a scratch register for the intermediate SP states as the save
                 * area size can be large.
                 */
                int calleeFrameSize = minCalleeFrameSize + CalleeSavedRegisters.singleton().getSaveAreaSize();
                try (AArch64MacroAssembler.ScratchRegister scratch2 = masm.getScratchRegister()) {
                    Register arithScratch = scratch2.getRegister();
                    masm.sub(64, AArch64.sp, asRegister(sp), calleeFrameSize, arithScratch);
                }

                masm.mov(64, ipScratch, asRegister(ip));
                ipRegister = ipScratch;

                AArch64CalleeSavedRegisters.singleton().emitRestore(masm, calleeFrameSize, asRegister(result));

                /*
                 * After restore move sp to the minCalleeFrameSize.
                 */
                try (AArch64MacroAssembler.ScratchRegister scratch2 = masm.getScratchRegister()) {
                    Register arithScratch = scratch2.getRegister();
                    masm.add(64, AArch64.sp, AArch64.sp, CalleeSavedRegisters.singleton().getSaveAreaSize(), arithScratch);
                }
            } else {
                masm.sub(64, AArch64.sp, asRegister(sp), minCalleeFrameSize);
                ipRegister = asRegister(ip);
            }

            masm.ldr(64, fp, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, AArch64.sp, minCalleeFrameSize));
            returnTo(ipRegister, masm);
        }
    }

    private static void returnTo(Register ipRegister, AArch64MacroAssembler masm) {
        /*
         * Set lr to the new instruction pointer like a regular return does.
         *
         * For dispatching an exception into a frame pending deoptimization, we farReturn into a
         * deopt stub which will do a regular enter and thus push lr. This keeps the stack walkable
         * and the frame can be recognized as pending deoptimization due to the return address from
         * lr matching the deopt stub entry point.
         */
        masm.mov(64, lr, ipRegister);
        masm.ret(lr);
    }
}
