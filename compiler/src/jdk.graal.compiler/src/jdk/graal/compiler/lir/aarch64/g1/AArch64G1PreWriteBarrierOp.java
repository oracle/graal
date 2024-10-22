/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.core.common.GraalOptions.VerifyAssemblyGCBarriers;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
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
 * AArch46 G1 pre write barrier code emission. Platform specific code generation is performed by
 * {@link AArch64G1BarrierSetLIRTool}.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/5860a48c71e324f77a7ecc613c063cbb81580011/src/hotspot/cpu/aarch64/gc/g1/g1BarrierSetAssembler_aarch64.cpp#L98-L183",
          ignore = "GR-58685",
          sha1 = "9b21f261fba89d49673568e85193159286db3ef5")
// @formatter:on
public class AArch64G1PreWriteBarrierOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64G1PreWriteBarrierOp> TYPE = LIRInstructionClass.create(AArch64G1PreWriteBarrierOp.class);

    @Alive private Value address;
    @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value expectedObject;
    @Temp private Value temp;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value temp2;
    private final ForeignCallLinkage callTarget;
    private final boolean nonNull;
    private final AArch64G1BarrierSetLIRTool tool;

    public AArch64G1PreWriteBarrierOp(Value address, Value expectedObject, Value temp, AllocatableValue temp2, ForeignCallLinkage callTarget,
                    boolean nonNull, AArch64G1BarrierSetLIRTool tool) {
        super(TYPE);
        this.address = address;
        assert expectedObject.equals(Value.ILLEGAL) ^ temp2.equals(Value.ILLEGAL) : "only one register is necessary";
        this.expectedObject = expectedObject;
        this.temp = temp;
        this.temp2 = temp2;
        this.callTarget = callTarget;
        this.nonNull = nonNull;
        this.tool = tool;
        GraalError.guarantee(expectedObject.equals(Value.ILLEGAL) || expectedObject.getPlatformKind().getSizeInBytes() == 8, "expected uncompressed pointer");
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register storeAddress = asRegister(address);
        Register thread = tool.getThread(masm);
        Register tmp = asRegister(temp);
        Register previousValue = expectedObject.equals(Value.ILLEGAL) ? asRegister(temp2) : asRegister(expectedObject);

        guaranteeDifferentRegisters(storeAddress, thread, tmp, previousValue);

        Label done = new Label();
        Label runtime = new Label();

        // Is marking active?
        AArch64Address markingActive = masm.makeAddress(8, thread, tool.satbQueueMarkingActiveOffset());
        masm.ldr(8, tmp, markingActive);
        masm.cbz(32, tmp, done);

        // Do we need to load the previous value?
        if (expectedObject.equals(Value.ILLEGAL)) {
            tool.loadObject(masm, previousValue, storeAddress);
        } else {
            // previousValue contains the value
        }
        if (!nonNull) {
            // Is the previous value null?
            masm.cbz(64, previousValue, done);
        }

        if (VerifyAssemblyGCBarriers.getValue(crb.getOptions())) {
            try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
                Register tmp2 = sc1.getRegister();
                tool.verifyOop(masm, previousValue, tmp, tmp2, false, true);
            }
        }

        if (AssemblyGCBarriersSlowPathOnly.getValue(crb.getOptions())) {
            masm.jmp(runtime);
        } else {
            AArch64Address satbQueueIndex = masm.makeAddress(64, thread, tool.satbQueueIndexOffset());
            // tmp := *index_adr
            // if tmp == 0 then goto runtime
            masm.ldr(64, tmp, satbQueueIndex);
            masm.cbz(64, tmp, runtime);

            // tmp := tmp - wordSize
            // *index_adr := tmp
            // tmp := tmp + *buffer_adr
            masm.sub(64, tmp, tmp, 8);
            masm.str(64, tmp, satbQueueIndex);
            try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
                Register scratch1 = sc1.getRegister();
                AArch64Address satbQueueBuffer = masm.makeAddress(64, thread, tool.satbQueueBufferOffset());
                masm.ldr(64, scratch1, satbQueueBuffer);
                masm.add(64, tmp, tmp, scratch1);
            }

            // Record the previous value
            masm.str(64, previousValue, masm.makeAddress(64, tmp, 0));
        }
        masm.bind(done);

        // Out of line slow path
        crb.getLIR().addSlowPath(this, () -> {
            try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
                Register scratch1 = sc1.getRegister();
                masm.bind(runtime);
                CallingConvention cc = callTarget.getOutgoingCallingConvention();
                AArch64Address cArg0 = (AArch64Address) crb.asAddress(cc.getArgument(0));
                masm.str(64, previousValue, cArg0);
                AArch64Call.directCall(crb, masm, callTarget, AArch64Call.isNearCall(callTarget) ? null : scratch1, null);
                masm.jmp(done);
            }
        });

    }
}
