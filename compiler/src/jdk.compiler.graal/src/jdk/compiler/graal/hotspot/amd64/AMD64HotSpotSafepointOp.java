/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rip;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.compiler.graal.hotspot.HotSpotHostBackend.POLLING_PAGE_RETURN_HANDLER;

import java.util.function.IntConsumer;
import jdk.compiler.graal.asm.Label;
import jdk.compiler.graal.asm.amd64.AMD64Address;
import jdk.compiler.graal.asm.amd64.AMD64Assembler;
import jdk.compiler.graal.asm.amd64.AMD64MacroAssembler;
import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.HotSpotMarkId;
import jdk.compiler.graal.lir.LIRFrameState;
import jdk.compiler.graal.lir.LIRInstructionClass;
import jdk.compiler.graal.lir.Opcode;
import jdk.compiler.graal.lir.amd64.AMD64Call;
import jdk.compiler.graal.lir.amd64.AMD64LIRInstruction;
import jdk.compiler.graal.lir.asm.CompilationResultBuilder;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Emits a safepoint poll.
 */
@Opcode("SAFEPOINT")
public final class AMD64HotSpotSafepointOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotSafepointOp> TYPE = LIRInstructionClass.create(AMD64HotSpotSafepointOp.class);

    @State protected LIRFrameState state;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private AllocatableValue temp;

    private final GraalHotSpotVMConfig config;
    private final Register thread;

    public AMD64HotSpotSafepointOp(LIRFrameState state, GraalHotSpotVMConfig config, NodeLIRBuilderTool tool, Register thread) {
        super(TYPE);
        this.state = state;
        this.config = config;
        this.thread = thread;
        temp = tool.getLIRGeneratorTool().newVariable(LIRKind.value(tool.getLIRGeneratorTool().target().arch.getWordKind()));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        emitCode(crb, asm, config, false, state, thread, temp instanceof RegisterValue ? ((RegisterValue) temp).getRegister() : null);
    }

    public static void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm, GraalHotSpotVMConfig config, boolean atReturn, LIRFrameState state, Register thread, Register scratch) {
        assert !atReturn || state == null : "state is unneeded at return";

        assert config.threadPollingPageOffset >= 0;
        if (config.threadPollingWordOffset != -1 && atReturn && config.pollingPageReturnHandler != 0) {
            // HotSpot uses this strategy even if the selected GC doesn't require any concurrent
            // stack cleaning.

            final int[] pos = new int[1];
            IntConsumer doMark = value -> {
                pos[0] = value;
                crb.recordMark(HotSpotMarkId.POLL_RETURN_FAR);
            };

            Label entryPoint = new Label();
            asm.cmpqAndJcc(rsp, new AMD64Address(thread, config.threadPollingWordOffset), AMD64Assembler.ConditionFlag.Above, entryPoint, false, doMark);
            crb.getLIR().addSlowPath(null, () -> {
                asm.bind(entryPoint);
                // Load the pc of the poll instruction
                asm.leaq(scratch, new AMD64Address(rip, 0));
                final int afterLea = asm.position();
                asm.emitInt(pos[0] - afterLea, afterLea - 4);
                asm.movq(new AMD64Address(r15, config.savedExceptionPCOffset), scratch);
                AMD64Call.directJmp(crb, asm, crb.foreignCalls.lookupForeignCall(POLLING_PAGE_RETURN_HANDLER), null);
            });
        } else {
            asm.movptr(scratch, new AMD64Address(thread, config.threadPollingPageOffset));
            crb.recordMark(atReturn ? HotSpotMarkId.POLL_RETURN_FAR : HotSpotMarkId.POLL_FAR);
            final int pos = asm.position();
            if (state != null) {
                crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
            }
            asm.testl(rax, new AMD64Address(scratch));
        }
    }
}
