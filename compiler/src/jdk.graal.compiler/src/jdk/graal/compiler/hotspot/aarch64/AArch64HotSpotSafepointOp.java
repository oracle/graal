/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotHostBackend;
import jdk.graal.compiler.hotspot.HotSpotMarkId;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.aarch64.AArch64Call;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Emits a safepoint poll.
 */
@Opcode("SAFEPOINT")
public class AArch64HotSpotSafepointOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotSafepointOp> TYPE = LIRInstructionClass.create(AArch64HotSpotSafepointOp.class);

    @State protected LIRFrameState state;
    @Temp protected AllocatableValue scratchValue;

    private final GraalHotSpotVMConfig config;
    private final Register thread;

    public AArch64HotSpotSafepointOp(LIRFrameState state, GraalHotSpotVMConfig config, Register thread, AllocatableValue scratch) {
        super(TYPE);
        this.state = state;
        this.config = config;
        this.thread = thread;
        this.scratchValue = scratch;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register scratch = asRegister(scratchValue);
        emitCode(crb, masm, config, false, thread, scratch, state);
    }

    public static void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm, GraalHotSpotVMConfig config, boolean atReturn, Register thread, Register scratch, LIRFrameState state) {
        assert config.threadPollingPageOffset >= 0 : config.threadPollingPageOffset;
        if (config.threadPollingWordOffset != -1 && atReturn && config.pollingPageReturnHandler != 0) {
            // HotSpot uses this strategy even if the selected GC doesn't require any concurrent
            // stack cleaning.
            Label entryPoint = new Label();
            Label poll = new Label();
            masm.bind(poll);
            crb.recordMark(HotSpotMarkId.POLL_RETURN_FAR);
            masm.ldr(64, scratch, masm.makeAddress(64, thread, config.threadPollingWordOffset, scratch));
            masm.cmp(64, AArch64.sp, scratch);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.HI, entryPoint);
            crb.getLIR().addSlowPath(null, () -> {
                masm.bind(entryPoint);
                // store mark pc in scratch
                masm.adr(scratch, poll);
                masm.str(64, scratch, masm.makeAddress(64, thread, config.savedExceptionPCOffset));
                AArch64Call.directJmp(crb, masm, crb.getForeignCalls().lookupForeignCall(HotSpotHostBackend.POLLING_PAGE_RETURN_HANDLER));
            });
        } else {
            masm.ldr(64, scratch, masm.makeAddress(64, thread, config.threadPollingPageOffset, scratch));
            crb.recordMark(atReturn ? HotSpotMarkId.POLL_RETURN_FAR : HotSpotMarkId.POLL_FAR);
            if (state != null) {
                crb.recordInfopoint(masm.position(), state, InfopointReason.SAFEPOINT);
            }
            masm.deadLoad(32, AArch64Address.createBaseRegisterOnlyAddress(32, scratch), false);
        }
    }
}
