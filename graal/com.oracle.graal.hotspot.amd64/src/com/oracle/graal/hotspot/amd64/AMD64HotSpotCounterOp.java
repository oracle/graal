/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.jvmci.common.JVMCIError.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.jvmci.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

@Opcode("BenchMarkCounter")
public class AMD64HotSpotCounterOp extends HotSpotCounterOp {
    public static final LIRInstructionClass<AMD64HotSpotCounterOp> TYPE = LIRInstructionClass.create(AMD64HotSpotCounterOp.class);

    @Alive({OperandFlag.STACK, OperandFlag.UNINITIALIZED}) private StackSlotValue backupSlot;

    public AMD64HotSpotCounterOp(String name, String group, Value increment, HotSpotRegistersProvider registers, HotSpotVMConfig config, StackSlotValue backupSlot) {
        super(TYPE, name, group, increment, registers, config);
        this.backupSlot = backupSlot;
    }

    public AMD64HotSpotCounterOp(String[] names, String[] groups, Value[] increments, HotSpotRegistersProvider registers, HotSpotVMConfig config, StackSlotValue backupSlot) {
        super(TYPE, names, groups, increments, registers, config);
        this.backupSlot = backupSlot;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
        TargetDescription target = crb.target;

        Register scratch;
        // It can happen that the rax register is the increment register, in this case we do not
        // want to spill it to the stack.
        if (!contains(increments, rax)) {
            scratch = rax;
        } else if (!contains(increments, rbx)) {
            scratch = rbx;
        } else {
            // In this case rax and rbx are used as increment. Either we implement a third register
            // or we implement a spillover the value from rax to rbx or vice versa during
            // emitIncrement().
            throw unimplemented("RAX and RBX are increment registers a the same time, spilling over the scratch register is not supported right now");
        }

        // address for counters array
        AMD64Address countersArrayAddr = new AMD64Address(thread, config.graalCountersThreadOffset);
        Register countersArrayReg = scratch;

        // backup scratch register
        masm.movq((AMD64Address) crb.asAddress(backupSlot), scratch);

        // load counters array
        masm.movptr(countersArrayReg, countersArrayAddr);
        CounterProcedure emitProcedure = (counterIndex, increment, displacement) -> emitIncrement(masm, countersArrayReg, increment, displacement);
        forEachCounter(emitProcedure, target);

        // restore scratch register
        masm.movq(scratch, (AMD64Address) crb.asAddress(backupSlot));
    }

    /**
     * Tests if the array contains the register.
     */
    private static boolean contains(Value[] increments, Register register) {
        for (Value increment : increments) {
            if (isRegister(increment) && asRegister(increment).equals(register)) {
                return true;
            }
        }
        return false;
    }

    private static void emitIncrement(AMD64MacroAssembler masm, Register countersArrayReg, Value incrementValue, int displacement) {
        // address for counter value
        AMD64Address counterAddr = new AMD64Address(countersArrayReg, displacement);
        // increment counter (in memory)
        if (isConstant(incrementValue)) {
            int increment = asInt(asConstant(incrementValue));
            masm.incrementq(counterAddr, increment);
        } else {
            masm.addq(counterAddr, asRegister(incrementValue));
        }

    }
}
