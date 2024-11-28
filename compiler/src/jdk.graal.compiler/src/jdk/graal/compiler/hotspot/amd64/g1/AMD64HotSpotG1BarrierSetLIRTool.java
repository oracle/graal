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
package jdk.graal.compiler.hotspot.amd64.g1;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotG1BarrierSetLIRTool;
import jdk.graal.compiler.hotspot.amd64.AMD64HotSpotMacroAssembler;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.amd64.g1.AMD64G1BarrierSetLIRTool;
import jdk.vm.ci.code.Register;

/**
 * The AMD64 HotSpot specific assembly helpers required for G1 assembly barrier emission.
 */
public class AMD64HotSpotG1BarrierSetLIRTool extends HotSpotG1BarrierSetLIRTool implements AMD64G1BarrierSetLIRTool {

    public AMD64HotSpotG1BarrierSetLIRTool(GraalHotSpotVMConfig config, HotSpotProviders providers) {
        super(config, providers);
    }

    @Override
    public Register getThread(AMD64MacroAssembler masm) {
        return providers.getRegisters().getThreadRegister();
    }

    private void computeCardOffset(Register cardAddress, Register storeAddress, AMD64MacroAssembler masm) {
        int cardTableShift = HotSpotReplacementsUtil.cardTableShift(config);
        masm.movq(cardAddress, storeAddress);
        masm.shrq(cardAddress, cardTableShift);
    }

    @Override
    public void computeCardThreadLocal(Register cardAddress, Register storeAddress, Register threadAddress, AMD64MacroAssembler masm) {
        computeCardOffset(cardAddress, storeAddress, masm);
        AMD64Address cardTableAddress = new AMD64Address(threadAddress, HotSpotReplacementsUtil.g1CardTableBaseOffset(config));
        masm.addq(cardAddress, cardTableAddress);
    }

    @Override
    public void computeCard(Register cardAddress, Register storeAddress, Register cardTableAddress, AMD64MacroAssembler masm) {
        computeCardOffset(cardAddress, storeAddress, masm);
        masm.movq(cardTableAddress, HotSpotReplacementsUtil.cardTableStart(config));
        masm.addq(cardAddress, cardTableAddress);
    }

    @Override
    public void loadObject(AMD64MacroAssembler masm, Register previousValue, AMD64Address storeAddress) {
        AMD64HotSpotMacroAssembler hasm = (AMD64HotSpotMacroAssembler) masm;
        hasm.loadObject(previousValue, storeAddress);
    }

    @Override
    public void verifyOop(AMD64MacroAssembler masm, Register value, Register tmp, Register tmp2, boolean compressed, boolean nonNull) {
        AMD64HotSpotMacroAssembler hasm = (AMD64HotSpotMacroAssembler) masm;
        hasm.verifyOop(value, tmp, tmp2, compressed, nonNull);
    }
}
