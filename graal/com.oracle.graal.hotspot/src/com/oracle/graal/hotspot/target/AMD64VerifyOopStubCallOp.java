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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.target.amd64.*;

/**
 * A call to HotSpot's object pointer verification stub.
 */
public class AMD64VerifyOopStubCallOp extends AMD64LIRInstruction {
    public AMD64VerifyOopStubCallOp(Value object, LIRDebugInfo info) {
        super("VERIFY_OOP", LIRInstruction.NO_OPERANDS, info, new Value[] {object}, NO_OPERANDS, NO_OPERANDS);
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        Register object = asRegister(input(0));
        // r13: (in) object
        if (object != AMD64.r13) {
            masm.push(AMD64.r13);
            masm.movq(AMD64.r13, object);
        }
        AMD64Call.directCall(tasm, masm, HotSpotGraalRuntime.getInstance().getConfig().verifyOopStub, info);
        if (object != AMD64.r13) {
            masm.pop(AMD64.r13);
        }
    }

    @Override
    protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
        if (mode == OperandMode.Input) {
            return EnumSet.of(OperandFlag.Register);
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
