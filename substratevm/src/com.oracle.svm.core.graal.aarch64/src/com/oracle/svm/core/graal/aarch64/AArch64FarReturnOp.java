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
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.aarch64.AArch64BlockEndOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;

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
        if (SubstrateOptions.PreserveFramePointer.getValue()) {
            /*
             * We need to properly restore the frame pointer to the value that matches the frame of
             * the new stack pointer. Two options: 1) When sp is not changing, we are jumping within
             * the same frame -> no adjustment of fp is necessary. 2) We jump to a frame earlier in
             * the stack -> the corresponding fp value was spilled to the stack by the callee.
             */
            Label done = new Label();
            masm.cmp(64, AArch64.sp, asRegister(sp));
            masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, done);
            /*
             * The callee pushes two word-sized values: first the return address, then the saved
             * frame pointer. The stack grows downwards, so the offset is negative relative to the
             * new stack pointer.
             */
            AArch64Address fpAddress = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED, asRegister(sp),
                            -2 * FrameAccess.wordSize());
            masm.ldr(64, fp, fpAddress);
            masm.bind(done);
        }

        masm.mov(sp.getPlatformKind().getSizeInBytes() * Byte.SIZE, AArch64.sp, asRegister(sp));

        if (fromMethodWithCalleeSavedRegisters) {
            /*
             * Restoring the callee saved registers may overwrite the register that holds the new
             * instruction pointer (ip). We therefore leverage a scratch register.
             */
            try (AArch64MacroAssembler.ScratchRegister scratch = masm.getScratchRegister()) {
                Register scratchReg = scratch.getRegister();
                masm.mov(64, scratchReg, asRegister(ip));

                AArch64CalleeSavedRegisters.singleton().emitRestore(masm, 0, asRegister(result));
                masm.ret(scratchReg);
            }
        } else {
            masm.ret(asRegister(ip));
        }
    }
}
