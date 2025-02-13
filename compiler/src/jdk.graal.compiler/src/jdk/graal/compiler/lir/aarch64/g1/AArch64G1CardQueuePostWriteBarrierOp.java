/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.aarch64.g1;

import static jdk.graal.compiler.asm.Assembler.guaranteeDifferentRegisters;
import static jdk.graal.compiler.core.common.GraalOptions.AssemblyGCBarriersSlowPathOnly;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.aarch64.AArch64Call;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AArch64 G1 post write barrier code emission. Platform specific code generation is performed by
 * {@link AArch64G1BarrierSetLIRTool}.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/43a2f17342af8f5bf1f5823df9fa0bf0bdfdfce2/src/hotspot/cpu/aarch64/gc/g1/g1BarrierSetAssembler_aarch64.cpp#L185-L259",
          ignore = "GR-58685, JDK-8342382",
          sha1 = "dd42f4d351403eb99f9bd76454131e0659be1565")
// @formatter:on
public class AArch64G1CardQueuePostWriteBarrierOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64G1CardQueuePostWriteBarrierOp> TYPE = LIRInstructionClass.create(AArch64G1CardQueuePostWriteBarrierOp.class);

    @Alive(REG) private Value address;
    @Alive(REG) private Value newValue;
    @Temp private Value temp;
    @Temp private Value temp2;
    private final ForeignCallLinkage callTarget;
    private final boolean nonNull;
    private final AArch64G1BarrierSetLIRTool tool;

    public AArch64G1CardQueuePostWriteBarrierOp(Value address, Value value, Value temp, AllocatableValue temp2, ForeignCallLinkage callTarget, boolean nonNull, AArch64G1BarrierSetLIRTool tool) {
        super(TYPE);
        this.address = address;
        this.newValue = value;
        this.temp = temp;
        this.temp2 = temp2;
        this.callTarget = callTarget;
        this.nonNull = nonNull;
        this.tool = tool;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register storeAddress = asRegister(address);
        Register newval = asRegister(newValue);
        Register thread = tool.getThread(masm);
        Register tmp1 = asRegister(temp);
        Register tmp2 = asRegister(temp2);

        guaranteeDifferentRegisters(storeAddress, thread, tmp1, tmp2);

        Label done = new Label();
        Label runtime = new Label();

        // Does store cross heap regions?
        masm.eor(64, tmp1, storeAddress, newval);
        masm.lsr(64, tmp1, tmp1, tool.logOfHeapRegionGrainBytes());
        masm.cbz(64, tmp1, done);

        if (!nonNull) {
            // crosses regions, storing null?
            masm.cbz(64, newval, done);
        }

        // storing region crossing non-null, is card already dirty?
        Register cardPointer = tmp1;

        tool.computeCard(cardPointer, storeAddress, tmp2, masm);

        AArch64Address cardAddress = masm.makeAddress(8, cardPointer, 0);
        masm.ldr(8, tmp2, cardAddress);
        masm.compare(32, tmp2, tool.youngCardValue());
        masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, done);

        assert tool.dirtyCardValue() == 0 : "must be 0";

        // __ membar(Assembler::StoreLoad);
        masm.dmb(AArch64Assembler.BarrierKind.ANY_ANY);

        masm.ldr(8, tmp2, cardAddress);
        masm.cbz(32, tmp2, done);

        // storing a region crossing, non-null oop, card is clean.
        if (AssemblyGCBarriersSlowPathOnly.getValue(crb.getOptions())) {
            masm.jmp(runtime);
        } else {
            // dirty card and log.
            masm.str(8, zr, cardAddress);

            try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
                Register rscratch1 = sc1.getRegister();
                AArch64Address cardQueueIndex = masm.makeAddress(64, thread, tool.cardQueueIndexOffset());
                AArch64Address cardQueueBuffer = masm.makeAddress(64, thread, tool.cardQueueBufferOffset());

                masm.ldr(64, rscratch1, cardQueueIndex);
                masm.cbz(64, rscratch1, runtime);
                masm.sub(64, rscratch1, rscratch1, 8);
                masm.str(64, rscratch1, cardQueueIndex);

                masm.ldr(64, tmp2, cardQueueBuffer);
                masm.str(64, cardPointer, AArch64Address.createRegisterOffsetAddress(64, tmp2, rscratch1, false));
            }
        }
        masm.bind(done);

        // Out of line slow path
        crb.getLIR().addSlowPath(this, () -> {
            try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
                Register scratch1 = sc1.getRegister();
                masm.bind(runtime);
                CallingConvention cc = callTarget.getOutgoingCallingConvention();
                AArch64Address cArg0 = (AArch64Address) crb.asAddress(cc.getArgument(0));
                masm.str(64, cardPointer, cArg0);
                AArch64Call.directCall(crb, masm, callTarget, AArch64Call.isNearCall(callTarget) ? null : scratch1, null);
                masm.jmp(done);
            }
        });

    }
}
