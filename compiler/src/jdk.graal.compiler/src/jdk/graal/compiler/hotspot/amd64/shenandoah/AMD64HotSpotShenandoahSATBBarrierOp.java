/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64.shenandoah;

import static jdk.graal.compiler.asm.Assembler.guaranteeDifferentRegisters;
import static jdk.graal.compiler.core.common.GraalOptions.AssemblyGCBarriersSlowPathOnly;
import static jdk.graal.compiler.core.common.GraalOptions.VerifyAssemblyGCBarriers;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.amd64.AMD64HotSpotMacroAssembler;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * X86 backend for the Shenandoah SATB barrier.
 */
public class AMD64HotSpotShenandoahSATBBarrierOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotShenandoahSATBBarrierOp> TYPE = LIRInstructionClass.create(AMD64HotSpotShenandoahSATBBarrierOp.class);

    private final GraalHotSpotVMConfig config;
    private final HotSpotProviders providers;

    /**
     * The SATB slow-path runtime entry.
     */
    private final ForeignCallLinkage callTarget;

    /**
     * If we know that the previous value is not null, then we don't need to emit a null-check.
     */
    private final boolean nonNull;

    /**
     * Whether the reference is compressed.
     */
    private final boolean narrow;

    /**
     * The store address.
     */
    @Alive private Value address;

    /**
     * The pre-loaded previous value, if any.
     */
    @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value expectedObject;

    @Temp private Value temp;

    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value temp2;

    @Temp private Value temp3;

    public AMD64HotSpotShenandoahSATBBarrierOp(GraalHotSpotVMConfig config, HotSpotProviders providers,
                    AllocatableValue address, AllocatableValue expectedObject,
                    AllocatableValue temp, AllocatableValue temp2, AllocatableValue temp3,
                    ForeignCallLinkage callTarget, boolean narrow, boolean nonNull) {
        super(TYPE);
        this.config = config;
        this.providers = providers;
        this.address = address;
        GraalError.guarantee(expectedObject.equals(Value.ILLEGAL) ^ temp2.equals(Value.ILLEGAL), "only one register is necessary");
        this.expectedObject = expectedObject;
        this.temp = temp;
        this.temp2 = temp2;
        this.temp3 = temp3;
        this.callTarget = callTarget;
        this.nonNull = nonNull;
        this.narrow = narrow;
        GraalError.guarantee(expectedObject.equals(Value.ILLEGAL) || expectedObject.getPlatformKind().getSizeInBytes() == 8, "expected uncompressed pointer");
    }

    public void loadObject(AMD64MacroAssembler masm, Register preVal, Register immediateAddress) {
        if (narrow) {
            masm.movl(preVal, new AMD64Address(immediateAddress));
            CompressEncoding encoding = config.getOopEncoding();
            AMD64Move.UncompressPointerOp.emitUncompressCode(masm, preVal, encoding.getShift(), providers.getRegisters().getHeapBaseRegister(), false);
        } else {
            masm.movq(preVal, new AMD64Address(immediateAddress));
        }
    }

    @Override
    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/a2743bab4fd203b0791cf47e617c1a95b05ab3cc/src/hotspot/cpu/x86/gc/shenandoah/shenandoahBarrierSetAssembler_x86.cpp#L189-L294",
              sha1 = "8fb9ab816c4ee89723e0cf3efb15dcea87128044")
    // @formatter:on
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register storeAddress = asRegister(address);
        Register thread = providers.getRegisters().getThreadRegister();
        Register tmp = asRegister(temp);
        Register tmp3 = asRegister(temp3);
        Register previousValue = expectedObject.equals(Value.ILLEGAL) ? asRegister(temp2) : asRegister(expectedObject);
        guaranteeDifferentRegisters(storeAddress, thread, tmp, tmp3, previousValue);

        Label done = new Label();
        Label runtime = new Label();

        // Is marking active?
        masm.movb(tmp, new AMD64Address(thread, HotSpotReplacementsUtil.shenandoahGCStateOffset(config)));
        masm.testlAndJcc(tmp, config.shenandoahGCStateMarking, AMD64Assembler.ConditionFlag.Zero, done, true);

        // Do we need to load the previous value?
        if (expectedObject.equals(Value.ILLEGAL)) {
            loadObject(masm, previousValue, storeAddress);
        }

        if (!nonNull) {
            // Is the previous value null?
            masm.testAndJcc(AMD64BaseAssembler.OperandSize.QWORD, previousValue, previousValue, AMD64Assembler.ConditionFlag.Zero, done, true);
        }

        if (VerifyAssemblyGCBarriers.getValue(crb.getOptions())) {
            verifyOop(masm, previousValue, tmp, tmp3);
        }

        if (AssemblyGCBarriersSlowPathOnly.getValue(crb.getOptions())) {
            masm.jmp(runtime);
        } else {
            int satbQueueIndexOffset = HotSpotReplacementsUtil.shenandoahSATBIndexOffset(config);
            AMD64Address satbQueueIndex = new AMD64Address(thread, satbQueueIndexOffset);
            // tmp := *index_adr
            // if tmp == 0 then goto runtime
            masm.movq(tmp, satbQueueIndex);
            masm.cmpq(tmp, 0);
            masm.jcc(AMD64Assembler.ConditionFlag.Equal, runtime);

            // tmp := tmp - wordSize
            // *index_adr := tmp
            masm.subq(tmp, 8);
            masm.movq(satbQueueIndex, tmp);

            // tmp := tmp + *buffer_adr
            int satbQueueBufferOffset = HotSpotReplacementsUtil.shenandoahSATBBufferOffset(config);
            AMD64Address satbQueueBuffer = new AMD64Address(thread, satbQueueBufferOffset);
            masm.movq(tmp3, satbQueueBuffer);
            masm.addq(tmp, tmp3);

            // Record the previous value
            masm.movq(new AMD64Address(tmp), previousValue);
        }
        masm.bind(done);

        // Out of line slow path
        crb.getLIR().addSlowPath(this, () -> {
            masm.bind(runtime);
            CallingConvention cc = callTarget.getOutgoingCallingConvention();
            GraalError.guarantee(cc.getArgumentCount() == 1, "Expecting callTarget to have only 1 parameters. It has %d", cc.getArgumentCount());

            AMD64Address cArg0 = (AMD64Address) crb.asAddress(cc.getArgument(0));
            masm.movq(cArg0, previousValue);
            AMD64Call.directCall(crb, masm, callTarget, null, false, null);
            masm.jmp(done);
        });
    }

    private static void verifyOop(AMD64MacroAssembler masm, Register previousValue, Register tmp, Register tmp2) {
        ((AMD64HotSpotMacroAssembler) masm).verifyOop(previousValue, tmp, tmp2, false, true);
    }
}
