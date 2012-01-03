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
package com.oracle.max.graal.compiler.target.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.stub.*;
import com.oracle.max.graal.compiler.util.*;

public enum AMD64ConvertFLOpcode implements LIROpcode {
    F2L, D2L;

    public LIRInstruction create(Variable result, final CompilerStub stub, Variable input, Variable scratch) {
        CiValue[] inputs = new CiValue[] {input};
        CiValue[] temps = new CiValue[] {scratch};

        return new AMD64LIRInstruction(this, result, null, inputs, LIRInstruction.NO_OPERANDS, temps) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                emit(tasm, masm, result(), stub, input(0), temp(0));
            }
        };
    }

    private void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CompilerStub stub, CiValue input, CiValue scratch) {
        CiRegister dst = asLongReg(result);
        CiRegister tmp = asLongReg(scratch);
        switch (this) {
            case F2L: masm.cvttss2siq(dst, asFloatReg(input)); break;
            case D2L: masm.cvttsd2siq(dst, asDoubleReg(input)); break;
            default: throw Util.shouldNotReachHere();
        }

        Label endLabel = new Label();
        masm.movq(tmp, java.lang.Long.MIN_VALUE);
        masm.cmpq(dst, tmp);
        masm.jcc(ConditionFlag.notEqual, endLabel);
        AMD64CallOpcode.callStub(tasm, masm, stub, null, result, input);
        masm.bind(endLabel);
    }
}
