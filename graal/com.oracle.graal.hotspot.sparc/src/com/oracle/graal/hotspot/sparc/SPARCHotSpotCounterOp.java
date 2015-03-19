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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

@Opcode("BenchMarkCounter")
public class SPARCHotSpotCounterOp extends HotSpotCounterOp {
    public static final LIRInstructionClass<SPARCHotSpotCounterOp> TYPE = LIRInstructionClass.create(SPARCHotSpotCounterOp.class);

    public SPARCHotSpotCounterOp(String name, String group, Value increment, HotSpotRegistersProvider registers, HotSpotVMConfig config) {
        super(TYPE, name, group, increment, registers, config);
    }

    public SPARCHotSpotCounterOp(String[] names, String[] groups, Value[] increments, HotSpotRegistersProvider registers, HotSpotVMConfig config) {
        super(TYPE, names, groups, increments, registers, config);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
        TargetDescription target = crb.target;

        // address for counters array
        SPARCAddress countersArrayAddr = new SPARCAddress(thread, config.graalCountersThreadOffset);
        try (ScratchRegister scratch = masm.getScratchRegister()) {
            Register countersArrayReg = scratch.getRegister();

            // load counters array
            masm.ldx(countersArrayAddr, countersArrayReg);

            forEachCounter((name, group, increment) -> emitIncrement(masm, target, countersArrayReg, name, group, increment));
        }
    }

    private void emitIncrement(SPARCMacroAssembler masm, TargetDescription target, Register countersArrayReg, String name, String group, Value increment) {
        // address for counter
        SPARCAddress counterAddr = new SPARCAddress(countersArrayReg, getDisplacementForLongIndex(target, getIndex(name, group, increment)));

        try (ScratchRegister scratch = masm.getScratchRegister()) {
            Register counterReg = scratch.getRegister();
            // load counter value
            masm.ldx(counterAddr, counterReg);
            // increment counter
            if (isConstant(increment)) {
                masm.add(counterReg, asInt(asConstant(increment)), counterReg);
            } else {
                masm.add(counterReg, asRegister(increment), counterReg);
            }
            // store counter value
            masm.stx(counterReg, counterAddr);
        }
    }
}
