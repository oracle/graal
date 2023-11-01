/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRValueUtil.asJavaConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isJavaConstant;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotCounterOp;
import jdk.graal.compiler.hotspot.debug.BenchmarkCounters;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

@Opcode("BenchMarkCounter")
public class AArch64HotSpotCounterOp extends HotSpotCounterOp {
    public static final LIRInstructionClass<AArch64HotSpotCounterOp> TYPE = LIRInstructionClass.create(AArch64HotSpotCounterOp.class);

    public AArch64HotSpotCounterOp(String name, String group, Value increment, HotSpotRegistersProvider registers, GraalHotSpotVMConfig config) {
        super(TYPE, name, group, increment, registers, config);
    }

    public AArch64HotSpotCounterOp(String[] names, String[] groups, Value[] increments, HotSpotRegistersProvider registers, GraalHotSpotVMConfig config) {
        super(TYPE, names, groups, increments, registers, config);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
        TargetDescription target = crb.target;

        try (ScratchRegister sc1 = masm.getScratchRegister(); ScratchRegister sc2 = masm.getScratchRegister()) {
            Register scratch1 = sc1.getRegister();
            Register scratch2 = sc2.getRegister();

            /* Retrieve counters array. */
            AArch64Address countersArrayAddr = masm.makeAddress(64, thread, config.jvmciCountersThreadOffset, scratch2);
            masm.ldr(64, scratch1, countersArrayAddr);

            /* Perform increments. */
            CounterProcedure emitProcedure = (counterIndex, increment, displacement) -> emitIncrement(crb, masm, scratch1, increment, displacement, scratch2);
            forEachCounter(emitProcedure, target);
        }
    }

    private static void emitIncrement(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register countersArrayReg, Value incrementValue, int displacement, Register scratch) {
        /*
         * Address for counter value. If the displacement is larger than what can fit in an
         * immediate index, then temporarily adjust countersArrayReg.
         */
        boolean restoreCounterAddr = false;
        AArch64Address counterAddr = masm.tryMakeAddress(64, countersArrayReg, displacement);
        if (counterAddr == null) {
            restoreCounterAddr = true;
            masm.add(64, countersArrayReg, countersArrayReg, displacement);
            counterAddr = masm.makeAddress(64, countersArrayReg, 0);
        }
        /* Increment counter. */
        masm.ldr(64, scratch, counterAddr);
        if (isJavaConstant(incrementValue)) {
            masm.adds(64, scratch, scratch, asInt(asJavaConstant(incrementValue)));
        } else {
            masm.adds(64, scratch, scratch, asRegister(incrementValue));
        }
        if (BenchmarkCounters.Options.AbortOnBenchmarkCounterOverflow.getValue(crb.getOptions())) {
            Label noOverflow = new Label();
            masm.branchConditionally(AArch64Assembler.ConditionFlag.VC, noOverflow);
            crb.blockComment("[BENCHMARK COUNTER OVERFLOW]");
            masm.illegal();
            masm.bind(noOverflow);
        }
        masm.str(64, scratch, counterAddr);
        if (restoreCounterAddr) {
            masm.sub(64, countersArrayReg, countersArrayReg, displacement);
        }
    }
}
