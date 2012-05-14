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
package com.oracle.graal.hotspot.target.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;

/**
 * Performs a call to the {@code slow_subtype_check_id} stub.
 */
public class AMD64TypeCheckSlowPathOp extends AMD64LIRInstruction {

    public AMD64TypeCheckSlowPathOp(CiValue result, CiValue objectHub, CiValue hub) {
        super("TYPECHECK_SLOW", new CiValue[] {result}, null, new CiValue[] {objectHub, hub}, NO_OPERANDS, NO_OPERANDS);
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        CiValue result = output(0);
        CiValue objectHub = input(0);
        CiValue hub = input(1);

        masm.push(asRegister(objectHub));
        masm.push(asRegister(hub));
        AMD64Call.directCall(tasm, masm, CompilerImpl.getInstance().getConfig().instanceofStub, null);

        // Two pops to balance the two pushes above - the value first popped is discarded
        masm.pop(asRegister(result));
        masm.pop(asRegister(result));
    }

    @Override
    protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
        if (mode == OperandMode.Input) {
            return EnumSet.of(OperandFlag.Register);
        } else if (mode == OperandMode.Output) {
            return EnumSet.of(OperandFlag.Register);
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
