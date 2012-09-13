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
import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.target.amd64.*;

/**
 * LIR instruction for calling HotSpot's {@code graal_monitorenter} stub.
 */
@Opcode("MONITORENTER_STUB")
public class AMD64MonitorEnterStubCallOp extends AMD64LIRInstruction {

    /**
     * The stub expects the object in the first stub ABI register.
     */
    public static final Register OBJECT = getStubParameterRegister(0);

    /**
     * The stub expects the lock in the second stub ABI register.
     */
    public static final Register LOCK = getStubParameterRegister(1);

    /**
     * The stub uses RAX, RBX and the first two ABI parameter registers.
     */
    public static final Register[] TEMPS = {AMD64.rax, AMD64.rbx};

    @Use protected Value object;
    @Use protected Value lock;
    @Temp protected Value[] temps;

    @State protected LIRFrameState state;

    public AMD64MonitorEnterStubCallOp(Value object, Value lock, LIRFrameState state) {
        this.object = object;
        this.lock = lock;
        this.temps = new Value[TEMPS.length];
        for (int i = 0; i < temps.length; i++) {
            temps[i] = TEMPS[i].asValue(Kind.Long);
        }
        this.state = state;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        HotSpotVMConfig config = HotSpotGraalRuntime.getInstance().getConfig();
        long stub = config.fastMonitorEnterStub;
        AMD64Call.directCall(tasm, masm, stub, state);
    }

    @Override
    protected void verify() {
        super.verify();
        assert asRegister(object) == OBJECT : "stub expects object in " + OBJECT;
        assert asRegister(lock) == LOCK : "stub expect lock in " + LOCK;
    }
}
