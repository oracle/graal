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

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

public class AArch64HotSpotShenandoahCardBarrierOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotShenandoahCardBarrierOp> TYPE = LIRInstructionClass.create(AArch64HotSpotShenandoahCardBarrierOp.class);

    private final GraalHotSpotVMConfig config;
    private final HotSpotProviders providers;

    @Alive({COMPOSITE}) protected AArch64AddressValue address;
    @Temp({REG}) protected AllocatableValue tmp;

    protected AArch64HotSpotShenandoahCardBarrierOp(GraalHotSpotVMConfig config, HotSpotProviders providers, AArch64AddressValue addr, AllocatableValue tmp) {
        super(TYPE);
        this.config = config;
        this.providers = providers;
        this.address = addr;
        this.tmp = tmp;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (AArch64MacroAssembler.ScratchRegister tmp2 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister tmp3 = masm.getScratchRegister()) {
            Register rtmp1 = asRegister(tmp);
            Register rtmp2 = tmp2.getRegister();
            Register rtmp3 = tmp3.getRegister();
            AArch64Address storeAddr = address.toAddress();
            Register rthread = providers.getRegisters().getThreadRegister();

            // Flatten address if necessary.
            Register rAddr;
            if (storeAddr.isBaseRegisterOnly()) {
                rAddr = storeAddr.getBase();
            } else {
                rAddr = rtmp1;
                masm.loadAddress(rAddr, storeAddr);
            }

            masm.lsr(64, rAddr, rAddr, HotSpotReplacementsUtil.cardTableShift(config));

            AArch64Address currCTHolderAddr = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED, rthread,
                            HotSpotReplacementsUtil.shenandoahCardTableOffset(config));
            masm.ldr(64, rtmp2, currCTHolderAddr);

            AArch64Address cardAddr = AArch64Address.createRegisterOffsetAddress(8, rAddr, rtmp2, false);
            if (HotSpotReplacementsUtil.useCondCardMark(config)) {
                Label alreadyDirty = new Label();
                masm.ldr(8, rtmp3, cardAddr);
                masm.cbz(8, rtmp3, alreadyDirty);
                masm.str(8, zr, cardAddr);
                masm.bind(alreadyDirty);
            } else {
                masm.str(8, zr, cardAddr);
            }
        }
    }
}
