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
package jdk.graal.compiler.hotspot.aarch64.g1;

import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;

import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotG1BarrierSetLIRTool;
import jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotMacroAssembler;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.aarch64.AArch64Move;
import jdk.graal.compiler.lir.aarch64.g1.AArch64G1BarrierSetLIRTool;
import jdk.vm.ci.code.Register;

/**
 * The AArch64 HotSpot specific assembly helpers required for G1 assembly barrier emission.
 */
public class AArch64HotSpotG1BarrierSetLIRTool extends HotSpotG1BarrierSetLIRTool implements AArch64G1BarrierSetLIRTool {

    public AArch64HotSpotG1BarrierSetLIRTool(GraalHotSpotVMConfig config, HotSpotProviders providers) {
        super(config, providers);
    }

    @Override
    public void loadObject(AArch64MacroAssembler masm, Register preVal, Register immediateAddress) {
        if (config.useCompressedOops) {
            masm.ldr(32, preVal, AArch64Address.createImmediateAddress(32, IMMEDIATE_SIGNED_UNSCALED, immediateAddress, 0));
            CompressEncoding encoding = config.getOopEncoding();
            AArch64Move.UncompressPointerOp.emitUncompressCode(masm, preVal, preVal, encoding, false, providers.getRegisters().getHeapBaseRegister(), false);
        } else {
            masm.ldr(64, preVal, AArch64Address.createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, immediateAddress, 0));
        }
    }

    @Override
    public Register getThread(AArch64MacroAssembler masm) {
        return providers.getRegisters().getThreadRegister();
    }

    private void computeCardFromTable(Register cardAddress, Register cardTableAddress, Register storeAddress, AArch64MacroAssembler masm) {
        int cardTableShift = HotSpotReplacementsUtil.cardTableShift(config);
        masm.add(64, cardAddress, cardTableAddress, storeAddress, AArch64Assembler.ShiftType.LSR, cardTableShift);
    }

    @Override
    public void computeCardThreadLocal(Register cardAddress, Register storeAddress, Register threadAddress, Register cardTableAddress, AArch64MacroAssembler masm) {
        masm.ldr(64, cardTableAddress, masm.makeAddress(64, threadAddress, HotSpotReplacementsUtil.g1CardTableBaseOffset(config)));
        computeCardFromTable(cardAddress, cardTableAddress, storeAddress, masm);
    }

    @Override
    public void computeCard(Register cardAddress, Register storeAddress, Register cardTableAddress, AArch64MacroAssembler masm) {
        masm.mov(cardTableAddress, cardTableAddress());
        computeCardFromTable(cardAddress, cardTableAddress, storeAddress, masm);
    }

    @Override
    public void verifyOop(AArch64MacroAssembler masm, Register previousValue, Register tmp, Register tmp2, boolean compressed, boolean nonNull) {
        ((AArch64HotSpotMacroAssembler) masm).verifyOop(previousValue, tmp, tmp2, compressed, nonNull);
    }
}
