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
package com.oracle.graal.hotspot.target.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.target.amd64.*;

/**
 * LIR instruction for calling HotSpot's {@code new_[object|type]_array} stub. This stub is declared in c1_Runtime1.hpp
 * and implemented in Runtime1::generate_code_for() which is located in c1_Runtime1_x86.cpp.
 */
@Opcode("NEW_ARRAY")
public class AMD64NewArrayStubCallOp extends AMD64LIRInstruction {

    /**
     * The stub places the result in RAX.
     */
    public static final Register RESULT = AMD64.rax;

    /**
     * The stub expects the hub in RDX.
     */
    public static final Register HUB = AMD64.rdx;

    /**
     * The stub expects the length in RBX.
     */
    public static final Register LENGTH = AMD64.rbx;

    /**
     * The stub uses RCX, RSI, and RDI as temps.
     */
    public static final Register[] TEMPS = {AMD64.rcx, AMD64.rdi, AMD64.rsi};

    private final boolean isObjectArray;

    @Def protected Value result;
    @Use protected Value hub;
    @Use protected Value length;
    @Temp protected Value[] temps;

    @State protected LIRFrameState state;

    public AMD64NewArrayStubCallOp(boolean isObjectArray, Value result, Value hub, Value length, LIRFrameState state) {
        this.isObjectArray = isObjectArray;
        this.result = result;
        this.hub = hub;
        this.length = length;
        this.temps = new Value[TEMPS.length];
        for (int i = 0; i < temps.length; i++) {
            temps[i] = TEMPS[i].asValue(Kind.Long);
        }
        this.state = state;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        HotSpotVMConfig config = HotSpotGraalRuntime.getInstance().getConfig();
        long stub = isObjectArray ? config.newObjectArrayStub : config.newTypeArrayStub;
        AMD64Call.directCall(tasm, masm, stub, state);
    }

    @Override
    protected void verify() {
        super.verify();
        assert asRegister(hub) == HUB : "stub expects hub in " + HUB;
        assert asRegister(length) == LENGTH : "stub expect length in " + LENGTH;
        assert asRegister(result) == RESULT : "stub places result in " + RESULT;
    }
}
