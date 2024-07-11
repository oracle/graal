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
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AMD64 G1 post barrier code emission. Platform specific code generation is performed by
 * {@link AMD64G1BarrierSetLIRTool}.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/43a2f17342af8f5bf1f5823df9fa0bf0bdfdfce2/src/hotspot/cpu/x86/gc/g1/g1BarrierSetAssembler_x86.cpp#L266-L342",
          sha1 = "5691006914119b2a6047c6935a3d4fe656dc663f")
// @formatter:on
public class AMD64G1PostWriteBarrierOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64G1PostWriteBarrierOp> TYPE = LIRInstructionClass.create(AMD64G1PostWriteBarrierOp.class);

    @Alive({REG}) private Value address;
    @Alive({REG}) private Value newValue;
    @Temp private Value temp;
    @Temp private Value temp2;
    private final ForeignCallLinkage callTarget;
    private final boolean nonNull;
    private final AMD64G1BarrierSetLIRTool tool;

    public AMD64G1PostWriteBarrierOp(Value address, Value value, AllocatableValue temp, AllocatableValue temp2, ForeignCallLinkage callTarget, boolean nonNull,
                    AMD64G1BarrierSetLIRTool tool) {
        super(TYPE);
        this.address = address;
        this.newValue = value;
        this.temp = temp;
        this.temp2 = temp2;
        this.callTarget = callTarget;
        this.nonNull = nonNull;
        this.tool = tool;
        assert !address.equals(value) : "this can be filtered out statically";
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register storeAddress = asRegister(address);
        Register newval = asRegister(newValue);
        Register tmp = asRegister(temp);
        Register tmp2 = asRegister(temp2);

        guaranteeDifferentRegisters(storeAddress, newval, tmp, tmp2);

        Label done = new Label();
        Label runtime = new Label();

        // Does store cross heap regions?
        masm.movq(tmp, storeAddress);
        masm.xorq(tmp, newval);
        masm.shrq(tmp, tool.logOfHeapRegionGrainBytes());
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, done);

        if (!nonNull) {
            // crosses regions, storing null?
            masm.testq(newval, newval);
            masm.jcc(AMD64Assembler.ConditionFlag.Equal, done);
        }

        // storing region crossing non-null, is card already dirty?
        Register cardAddress = tmp;

        tool.computeCard(cardAddress, storeAddress, tmp2, masm);

        masm.cmpb(new AMD64Address(cardAddress, 0), tool.youngCardValue());
        masm.jccb(AMD64Assembler.ConditionFlag.Equal, done);

        masm.membar(STORE_LOAD);
        masm.cmpb(new AMD64Address(cardAddress, 0), tool.dirtyCardValue());
        masm.jccb(AMD64Assembler.ConditionFlag.Equal, done);

        // storing a region crossing, non-null oop, card is clean.
        if (AssemblyGCBarriersSlowPathOnly.getValue(crb.getOptions())) {
            masm.jmp(runtime);
        } else {
            // dirty card and log.
            masm.movb(new AMD64Address(cardAddress, 0), tool.dirtyCardValue());

            Register thread = tool.getThread(masm);
            AMD64Address cardQueueIndex = new AMD64Address(thread, tool.cardQueueIndexOffset());
            AMD64Address cardQueueBuffer = new AMD64Address(thread, tool.cardQueueBufferOffset());
            masm.movq(tmp2, cardQueueIndex);
            masm.testq(tmp2, tmp2);
            masm.jcc(AMD64Assembler.ConditionFlag.Zero, runtime);
            masm.subq(tmp2, 8);
            masm.movq(cardQueueIndex, tmp2);
            masm.addq(tmp2, cardQueueBuffer);
            masm.movq(new AMD64Address(tmp2, 0), cardAddress);
        }
        masm.bind(done);

        // Out of line slow path
        crb.getLIR().addSlowPath(this, () -> {
            masm.bind(runtime);
            CallingConvention cc = callTarget.getOutgoingCallingConvention();
            AllocatableValue arg0 = cc.getArgument(0);
            if (arg0 instanceof StackSlot) {
                AMD64Address cArg0 = (AMD64Address) crb.asAddress(arg0);
                masm.movq(cArg0, cardAddress);
            } else {
                GraalError.shouldNotReachHere("must be StackSlot: " + arg0);
            }
            AMD64Call.directCall(crb, masm, tool.getCallTarget(callTarget), null, false, null);
            masm.jmp(done);
        });
    }
}
