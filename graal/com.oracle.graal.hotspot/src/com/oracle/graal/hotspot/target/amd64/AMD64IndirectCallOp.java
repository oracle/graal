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
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Call.IndirectCallOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.amd64.*;

/**
 * A register indirect call that complies with the extra conventions for such calls in HotSpot.
 * In particular, the methodOop of the callee must be in RBX for the case where a vtable entry's
 * _from_compiled_entry is the address of an C2I adapter. Such adapters expect the target
 * method to be in RBX.
 */
@Opcode("CALL_INDIRECT")
final class AMD64IndirectCallOp extends IndirectCallOp {

    /**
     * Vtable stubs expect the methodOop in RBX.
     */
    public static final Register METHOD_OOP = AMD64.rbx;

    @Use({REG}) protected Value methodOop;

    AMD64IndirectCallOp(Object targetMethod, Value result, Value[] parameters, Value[] temps, Value methodOop, Value targetAddress, LIRFrameState state) {
        super(targetMethod, result, parameters, temps, targetAddress, state);
        this.methodOop = methodOop;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        tasm.recordMark(Marks.MARK_INLINE_INVOKEVIRTUAL);
        Register callReg = asRegister(targetAddress);
        assert callReg != METHOD_OOP;
        AMD64Call.indirectCall(tasm, masm, callReg, targetMethod, state);
    }

    @Override
    protected void verify() {
        super.verify();
        assert asRegister(methodOop) == METHOD_OOP;
    }
}
