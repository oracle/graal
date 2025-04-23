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
package jdk.graal.compiler.lir.amd64.g1;

import static jdk.graal.compiler.asm.Assembler.guaranteeDifferentRegisters;
import static jdk.graal.compiler.core.common.GraalOptions.AssemblyGCBarriersSlowPathOnly;
import static jdk.graal.compiler.core.common.GraalOptions.VerifyAssemblyGCBarriers;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AMD64 G1 pre barrier code emission. Platform specific code generation is performed by
 * {@link AMD64G1BarrierSetLIRTool}.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/c5f1dcccfce7b943c1a91aa65709576038098e91/src/hotspot/cpu/x86/gc/g1/g1BarrierSetAssembler_x86.cpp#L163-L264",
          ignore = "GR-58685",
          sha1 = "34e794bbd45946013b84c5a4838b5b80d3b8a131")
// @formatter:on
public class AMD64G1PreWriteBarrierOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64G1PreWriteBarrierOp> TYPE = LIRInstructionClass.create(AMD64G1PreWriteBarrierOp.class);

    @Alive private Value address;
    @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value expectedObject;
    @Temp private Value temp;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value temp2;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value temp3;
    private final ForeignCallLinkage callTarget;
    private final boolean nonNull;
    private final AMD64G1BarrierSetLIRTool tool;

    public AMD64G1PreWriteBarrierOp(Value address, Value expectedObject, Value temp, AllocatableValue temp2, AllocatableValue temp3, ForeignCallLinkage callTarget, boolean nonNull,
                    AMD64G1BarrierSetLIRTool tool) {
        super(TYPE);
        this.address = address;
        this.expectedObject = expectedObject;
        this.callTarget = callTarget;
        GraalError.guarantee(expectedObject.equals(Value.ILLEGAL) || expectedObject.getPlatformKind().getSizeInBytes() == 8, "expected uncompressed pointer");
        assert expectedObject.equals(Value.ILLEGAL) ^ temp2.equals(Value.ILLEGAL) : "only one register is necessary";
        this.temp = temp;
        this.temp2 = temp2;
        this.temp3 = temp3;
        this.nonNull = nonNull;
        this.tool = tool;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Address storeAddress = ((AMD64AddressValue) this.address).toAddress(masm);
        Register thread = tool.getThread(masm);
        Register tmp = asRegister(temp);
        Register previousValue = expectedObject.equals(Value.ILLEGAL) ? asRegister(temp2) : asRegister(expectedObject);

        guaranteeDifferentRegisters(thread, tmp, previousValue);

        Label done = new Label();
        Label runtime = new Label();

        AMD64Address markingActive = new AMD64Address(thread, tool.satbQueueMarkingActiveOffset());

        // Is marking active?
        masm.cmpb(markingActive, 0);
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, done);

        // Do we need to load the previous value?
        if (expectedObject.equals(Value.ILLEGAL)) {
            tool.loadObject(masm, previousValue, storeAddress);
        } else {
            // previousValue contains the value
        }

        if (!nonNull) {
            // Is the previous value null?
            masm.testq(previousValue, previousValue);
            masm.jcc(AMD64Assembler.ConditionFlag.Equal, done);
        }

        if (VerifyAssemblyGCBarriers.getValue(crb.getOptions())) {
            tool.verifyOop(masm, previousValue, tmp, asRegister(temp3), false, true);
        }

        if (AssemblyGCBarriersSlowPathOnly.getValue(crb.getOptions())) {
            masm.jmp(runtime);
        } else {
            AMD64Address satbQueueIndex = new AMD64Address(thread, tool.satbQueueIndexOffset());
            // tmp := *index_adr
            // tmp == 0?
            // If yes, goto runtime
            masm.movq(tmp, satbQueueIndex);
            masm.cmpq(tmp, 0);
            masm.jcc(AMD64Assembler.ConditionFlag.Equal, runtime);

            // tmp := tmp - wordSize
            // *index_adr := tmp
            // tmp := tmp + *buffer_adr
            masm.subq(tmp, 8);
            masm.movptr(satbQueueIndex, tmp);
            AMD64Address satbQueueBuffer = new AMD64Address(thread, tool.satbQueueBufferOffset());
            masm.addq(tmp, satbQueueBuffer);

            // Record the previous value
            masm.movptr(new AMD64Address(tmp, 0), previousValue);
        }
        masm.bind(done);

        // Out of line slow path
        crb.getLIR().addSlowPath(this, () -> {
            masm.bind(runtime);
            CallingConvention cc = callTarget.getOutgoingCallingConvention();
            AllocatableValue arg0 = cc.getArgument(0);
            if (arg0 instanceof StackSlot) {
                AMD64Address slot0 = (AMD64Address) crb.asAddress(arg0);
                masm.movq(slot0, previousValue);
            } else {
                GraalError.shouldNotReachHere("must be StackSlot: " + arg0);
            }
            AMD64Call.directCall(crb, masm, tool.getCallTarget(callTarget), null, false, null);
            masm.jmp(done);
        });
    }
}
