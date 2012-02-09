/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot.target.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.target.amd64.*;
import com.oracle.max.graal.graph.*;

/**
 * Performs a hard-coded tail call to the specified target, which normally should be an RiCompiledCode instance.
 */
public class AMD64TailcallOp extends AMD64LIRInstruction {

    public AMD64TailcallOp(List<CiValue> parameters, CiValue target, CiValue[] callingConvention) {
        super("TAILCALL", LIRInstruction.NO_OPERANDS, null, toArray(parameters, target), LIRInstruction.NO_OPERANDS, callingConvention.clone());
        assert inputs.length == temps.length + 1;

        for (int i = 0; i < temps.length; i++) {
            assert isRegister(temps[i]) : "too many parameters for tail call";
            assert sameRegister(temps[i], inputs[i]) : "inputs do not match calling convention";
        }
    }

    private static CiValue[] toArray(List<CiValue> parameters, CiValue target) {
        CiValue[] result = new CiValue[parameters.size() + 1];
        parameters.toArray(result);
        result[parameters.size()] = target;
        return result;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        // destroy the current frame (now the return address is the top of stack)
        masm.leave();

        // jump to the target method
        masm.jmp(asRegister(inputs[inputs.length - 1]));
        masm.ensureUniquePC();
    }

    @Override
    protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
        if (mode == OperandMode.Input) {
            return EnumSet.of(OperandFlag.Register);
        } else if (mode == OperandMode.Temp) {
            return EnumSet.of(OperandFlag.Register);
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
