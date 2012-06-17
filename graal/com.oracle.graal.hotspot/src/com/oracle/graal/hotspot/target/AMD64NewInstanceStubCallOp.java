/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.target;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.target.amd64.*;

/**
 * LIR instruction for calling HotSpot's {@code new_instance} stub. This stub is declared in c1_Runtime1.hpp
 * and implemented in Runtime1::generate_code_for() which is located in c1_Runtime1_x86.cpp.
 */
public class AMD64NewInstanceStubCallOp extends AMD64LIRInstruction {
    public AMD64NewInstanceStubCallOp(Value result, Value hub, LIRDebugInfo info) {
        super("NEW_INSTANCE", new Value[] {result}, info, new Value[] {hub}, NO_OPERANDS, new Value[]{AMD64.rax.asValue(Kind.Object)});
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        Value result = output(0);
        Value hub = input(0);

        // rdx: (in) hub
        // rax: (out) result
        assert asRegister(hub) == AMD64.rdx;
        AMD64Call.directCall(tasm, masm, HotSpotGraalRuntime.getInstance().getConfig().newInstanceStub, info);
        if (asRegister(result) != AMD64.rax) {
            masm.movq(asRegister(result), AMD64.rax);
        }
    }

    @Override
    protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
        if (mode == OperandMode.Input) {
            return EnumSet.of(OperandFlag.Register);
        } else if (mode == OperandMode.Output) {
            return EnumSet.of(OperandFlag.Register);
        } else if (mode == OperandMode.Temp) {
            return EnumSet.of(OperandFlag.Register);
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
