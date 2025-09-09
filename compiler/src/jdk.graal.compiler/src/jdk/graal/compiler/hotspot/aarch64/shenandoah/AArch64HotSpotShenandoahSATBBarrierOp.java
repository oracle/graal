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
package jdk.graal.compiler.hotspot.aarch64.shenandoah;

import static jdk.graal.compiler.asm.Assembler.guaranteeDifferentRegisters;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.graal.compiler.core.common.GraalOptions.AssemblyGCBarriersSlowPathOnly;
import static jdk.graal.compiler.core.common.GraalOptions.VerifyAssemblyGCBarriers;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotMacroAssembler;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.aarch64.AArch64Call;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.aarch64.AArch64Move;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AArch64 backend for the Shenandoah SATB barrier.
 */
public class AArch64HotSpotShenandoahSATBBarrierOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotShenandoahSATBBarrierOp> TYPE = LIRInstructionClass.create(AArch64HotSpotShenandoahSATBBarrierOp.class);

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

    public AArch64HotSpotShenandoahSATBBarrierOp(GraalHotSpotVMConfig config, HotSpotProviders providers,
                    AllocatableValue address, AllocatableValue expectedObject, AllocatableValue temp, AllocatableValue temp2, ForeignCallLinkage callTarget, boolean narrow, boolean nonNull) {
        super(TYPE);
        this.config = config;
        this.providers = providers;
        this.address = address;
        GraalError.guarantee(expectedObject.equals(Value.ILLEGAL) ^ temp2.equals(Value.ILLEGAL), "only one register is necessary");
        this.expectedObject = expectedObject;
        this.temp = temp;
        this.temp2 = temp2;
        this.callTarget = callTarget;
        this.nonNull = nonNull;
        this.narrow = narrow;
        GraalError.guarantee(expectedObject.equals(Value.ILLEGAL) || expectedObject.getPlatformKind().getSizeInBytes() == 8, "expected uncompressed pointer");
    }

    public void loadObject(AArch64MacroAssembler masm, Register preVal, Register immediateAddress) {
        if (narrow) {
            masm.ldr(32, preVal, AArch64Address.createImmediateAddress(32, IMMEDIATE_SIGNED_UNSCALED, immediateAddress, 0));
            CompressEncoding encoding = config.getOopEncoding();
            AArch64Move.UncompressPointerOp.emitUncompressCode(masm, preVal, preVal, encoding, false, providers.getRegisters().getHeapBaseRegister(), false);
        } else {
            masm.ldr(64, preVal, AArch64Address.createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, immediateAddress, 0));
        }
    }

    @Override
    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/a2743bab4fd203b0791cf47e617c1a95b05ab3cc/src/hotspot/cpu/aarch64/gc/shenandoah/shenandoahBarrierSetAssembler_aarch64.cpp#L100-L183",
              sha1 = "7b3d183187ff6578e0d14eb54e4b5007ff4d5e1e")
    // @formatter:on
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register storeAddress = asRegister(address);
        Register thread = providers.getRegisters().getThreadRegister();
        Register tmp = asRegister(temp);
        Register previousValue = expectedObject.equals(Value.ILLEGAL) ? asRegister(temp2) : asRegister(expectedObject);

        guaranteeDifferentRegisters(storeAddress, thread, tmp, previousValue);

        Label done = new Label();
        Label midPath = new Label();
        Label runtime = new Label();

        // Is marking active?
        int gcStateOffset = HotSpotReplacementsUtil.shenandoahGCStateOffset(config);
        AArch64Address gcState = masm.makeAddress(8, thread, gcStateOffset);
        masm.ldr(8, tmp, gcState);
        masm.tst(64, tmp, config.shenandoahGCStateMarking);
        masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, midPath);
        masm.bind(done);

        // Out of line mid-path.
        crb.getLIR().addSlowPath(this, () -> {
            masm.bind(midPath);

            // Do we need to load the previous value?
            if (expectedObject.equals(Value.ILLEGAL)) {
                loadObject(masm, previousValue, storeAddress);
            }

            if (!nonNull) {
                // Is the previous value null?
                masm.cbz(64, previousValue, done);
            }

            if (VerifyAssemblyGCBarriers.getValue(crb.getOptions())) {
                try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
                    Register tmp2 = sc1.getRegister();
                    verifyOop(masm, previousValue, tmp, tmp2, false);
                }
            }

            if (AssemblyGCBarriersSlowPathOnly.getValue(crb.getOptions())) {
                masm.jmp(runtime);
            } else {
                int satbQueueIndexOffset = HotSpotReplacementsUtil.shenandoahSATBIndexOffset(config);
                AArch64Address satbQueueIndex = masm.makeAddress(64, thread, satbQueueIndexOffset);
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
                    int satbQueueBufferOffset = HotSpotReplacementsUtil.shenandoahSATBBufferOffset(config);
                    AArch64Address satbQueueBuffer = masm.makeAddress(64, thread, satbQueueBufferOffset);
                    masm.ldr(64, scratch1, satbQueueBuffer);
                    masm.add(64, tmp, tmp, scratch1);
                }

                // Record the previous value
                masm.str(64, previousValue, masm.makeAddress(64, tmp, 0));
                masm.jmp(done);
            }
        });

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

    private static void verifyOop(AArch64MacroAssembler masm, Register previousValue, Register tmp, Register tmp2, boolean compressed) {
        ((AArch64HotSpotMacroAssembler) masm).verifyOop(previousValue, tmp, tmp2, compressed, true);
    }
}
