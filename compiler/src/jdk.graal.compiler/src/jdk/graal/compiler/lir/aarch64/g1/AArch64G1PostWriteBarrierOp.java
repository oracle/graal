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
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AArch64 G1 post write barrier code emission. Platform specific code generation is performed by
 * {@link AArch64G1BarrierSetLIRTool}.
 */
// @formatter:off
@SyncPort(from = "https://github.com/tschatzl/jdk/blob/9feaeb2734f2b0f9dfb9866d598fa8c2385d2231/src/hotspot/cpu/aarch64/gc/g1/g1BarrierSetAssembler_aarch64.cpp#L227-L270",
          ignore = "JDK-8342382 HOTSPOT_PORT_SYNC_OVERWRITE=https://raw.githubusercontent.com/tschatzl/jdk/9feaeb2734f2b0f9dfb9866d598fa8c2385d2231/",
          sha1 = "6ada3c436955b1ac345d755eccf6ef7b30f9fba2")
// @formatter:on
public class AArch64G1PostWriteBarrierOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64G1PostWriteBarrierOp> TYPE = LIRInstructionClass.create(AArch64G1PostWriteBarrierOp.class);

    @Alive(REG) private Value address;
    @Alive(REG) private Value newValue;
    @Temp private Value temp;
    @Temp private Value temp2;
    private final boolean nonNull;
    private final AArch64G1BarrierSetLIRTool tool;

    public AArch64G1PostWriteBarrierOp(Value address, Value value, Value temp, AllocatableValue temp2, boolean nonNull, AArch64G1BarrierSetLIRTool tool) {
        super(TYPE);
        this.address = address;
        this.newValue = value;
        this.temp = temp;
        this.temp2 = temp2;
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

        guaranteeDifferentRegisters(storeAddress, newval, thread, tmp1, tmp2);

        Label done = new Label();

        // Does store cross heap regions?
        masm.eor(64, tmp1, storeAddress, newval);
        masm.lsr(64, tmp1, tmp1, tool.logOfHeapRegionGrainBytes());
        masm.cbz(64, tmp1, done);

        if (!nonNull) {
            // crosses regions, storing null?
            masm.cbz(64, newval, done);
        }

        // storing region crossing non-null, is card already dirty?
        tool.computeCardThreadLocal(tmp1, storeAddress, thread, tmp2, masm);
        AArch64Address cardAddress = masm.makeAddress(8, tmp1, 0);

        if (tool.useConditionalCardMarking()) {
            masm.ldr(8, tmp2, cardAddress);
            // Instead of loading clean_card_val and comparing, we exploit the fact that
            // the LSB of non-clean cards is always 0, and the LSB of clean cards 1.
            masm.tbz(tmp2, 0, done);
        }

        assert tool.dirtyCardValue() == 0 : "must be 0 to use zr";
        // Dirty card.
        masm.str(8, zr, cardAddress);

        masm.bind(done);
    }
}
