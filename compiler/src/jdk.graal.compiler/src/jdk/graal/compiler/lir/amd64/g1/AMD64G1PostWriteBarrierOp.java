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
package jdk.graal.compiler.lir.amd64.g1;

import static jdk.graal.compiler.asm.Assembler.guaranteeDifferentRegisters;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AMD64 G1 post barrier code emission. Platform specific code generation is performed by
 * {@link AMD64G1BarrierSetLIRTool}.
 */
// @formatter:off
@SyncPort(from = "https://github.com/tschatzl/jdk/blob/9feaeb2734f2b0f9dfb9866d598fa8c2385d2231/src/hotspot/cpu/x86/gc/g1/g1BarrierSetAssembler_x86.cpp#L316-L353",
          ignore = "JDK-8342382 HOTSPOT_PORT_SYNC_OVERWRITE=https://raw.githubusercontent.com/tschatzl/jdk/9feaeb2734f2b0f9dfb9866d598fa8c2385d2231/",
          sha1 = "1dd68fc099ffea45136184833514841df4b84b01")
// @formatter:on
public class AMD64G1PostWriteBarrierOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64G1PostWriteBarrierOp> TYPE = LIRInstructionClass.create(AMD64G1PostWriteBarrierOp.class);

    @Alive({REG}) private Value address;
    @Alive({REG}) private Value newValue;
    @Temp private Value temp;
    private final boolean nonNull;
    private final AMD64G1BarrierSetLIRTool tool;

    public AMD64G1PostWriteBarrierOp(Value address, Value value, AllocatableValue temp, boolean nonNull,
                    AMD64G1BarrierSetLIRTool tool) {
        super(TYPE);
        this.address = address;
        this.newValue = value;
        this.temp = temp;
        this.nonNull = nonNull;
        this.tool = tool;
        assert !address.equals(value) : "this can be filtered out statically";
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register storeAddress = asRegister(address);
        Register newval = asRegister(newValue);
        Register thread = tool.getThread(masm);
        Register tmp = asRegister(temp);

        guaranteeDifferentRegisters(storeAddress, newval, thread, tmp);

        Label done = new Label();

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

        // storing region crossing non-null, is card already non-clean?
        Register cardAddress = tmp;

        tool.computeCardThreadLocal(cardAddress, storeAddress, thread, masm);

        if (tool.useConditionalCardMarking()) {
            masm.cmpb(new AMD64Address(cardAddress, 0), tool.cleanCardValue());
            masm.jccb(AMD64Assembler.ConditionFlag.NotEqual, done);
        }

        // storing region crossing, non-null oop, card is clean.
        // Dirty card.
        masm.movb(new AMD64Address(cardAddress, 0), tool.dirtyCardValue());

        masm.bind(done);
    }
}
