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

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

import static jdk.graal.compiler.asm.Assembler.guaranteeDifferentRegisters;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

/**
 * X86 backend for the Shenandoah card barrier.
 */
public class AMD64HotSpotShenandoahCardBarrierOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotShenandoahCardBarrierOp> TYPE = LIRInstructionClass.create(AMD64HotSpotShenandoahCardBarrierOp.class);
    private final GraalHotSpotVMConfig config;
    private final HotSpotProviders providers;

    /**
     * The store address.
     */
    @Alive({COMPOSITE}) private AMD64AddressValue address;

    @Temp({REG}) private AllocatableValue tmp;

    @Temp({REG}) private AllocatableValue tmp2;

    protected AMD64HotSpotShenandoahCardBarrierOp(GraalHotSpotVMConfig config, HotSpotProviders providers, AMD64AddressValue addr, AllocatableValue tmp, AllocatableValue tmp2) {
        super(TYPE);
        this.config = config;
        this.providers = providers;
        this.address = addr;
        this.tmp = tmp;
        this.tmp2 = tmp2;
    }

    @Override
    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/a2743bab4fd203b0791cf47e617c1a95b05ab3cc/src/hotspot/cpu/x86/gc/shenandoah/shenandoahBarrierSetAssembler_x86.cpp#L509-L535",
              sha1 = "ad163e79b0707221700bb3b2230581fb711ded61")
    // @formatter:on
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register rtmp1 = asRegister(tmp);
        Register rtmp2 = asRegister(tmp2);
        Register rthread = providers.getRegisters().getThreadRegister();
        guaranteeDifferentRegisters(rtmp1, rtmp2, rthread);

        masm.leaq(rtmp1, address.toAddress(masm));
        masm.shrq(rtmp1, HotSpotReplacementsUtil.cardTableShift(config));

        AMD64Address currCTHolderAddr = new AMD64Address(rthread, HotSpotReplacementsUtil.shenandoahCardTableOffset(config));
        masm.movq(rtmp2, currCTHolderAddr);

        AMD64Address cardAddr = new AMD64Address(rtmp1, rtmp2, Stride.S1);
        if (HotSpotReplacementsUtil.useCondCardMark(config)) {
            Label alreadyDirty = new Label();
            masm.cmpb(cardAddr, 0 /* dirtyCardValue */);
            masm.jccb(AMD64Assembler.ConditionFlag.Equal, alreadyDirty);
            masm.movb(cardAddr, 0 /* dirtyCardValue */);
            masm.bind(alreadyDirty);
        } else {
            masm.movb(cardAddr, 0 /* dirtyCardValue */);
        }
    }
}
