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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;

/**
 * LIR instruction for calling HotSpot's {@code new_instance} stub. This stub is declared in c1_Runtime1.hpp
 * and implemented in Runtime1::generate_code_for() which is located in c1_Runtime1_x86.cpp.
 */
@Opcode("NEW_INSTANCE")
public class AMD64NewInstanceStubCallOp extends AMD64LIRInstruction {
    @Def protected Value result;
    @Use protected Value hub;
    @Temp protected Value temp;
    @State protected LIRFrameState state;

    public AMD64NewInstanceStubCallOp(Value result, Value hub, LIRFrameState state) {
        this.result = result;
        this.hub = hub;
        this.temp = AMD64.rax.asValue(Kind.Object);
        this.state = state;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        // rdx: (in) hub
        // rax: (out) result
        AMD64Call.directCall(tasm, masm, HotSpotGraalRuntime.getInstance().getConfig().newInstanceStub, state);
        if (asRegister(result) != AMD64.rax) {
            masm.movq(asRegister(result), AMD64.rax);
        }
    }

    @Override
    protected void verify() {
        super.verify();
        assert asRegister(hub) == AMD64.rdx;
    }
}
