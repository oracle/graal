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

import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;

public enum AMD64ConvertFIOpcode implements LIROpcode {
    F2I, D2I;

    public LIRInstruction create(CiValue result, CiValue x) {
        CiValue[] inputs = new CiValue[] {x};
        CiValue[] outputs = new CiValue[] {result};

        return new AMD64LIRInstruction(this, outputs, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
            @Override
            public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                emit(tasm, masm, output(0), input(0));
            }
        };
    }

    private void emit(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue x) {
        AMD64ConvertFSlowPath slowPath;
        switch (this) {
            case F2I:
                masm.cvttss2sil(asIntReg(result), asFloatReg(x));
                slowPath = new AMD64ConvertFSlowPath(masm, asIntReg(result), asFloatReg(x), false, false);
                break;
            case D2I:
                masm.cvttsd2sil(asIntReg(result), asDoubleReg(x));
                slowPath = new AMD64ConvertFSlowPath(masm, asIntReg(result), asDoubleReg(x), true, false);
                break;
            default:
                throw Util.shouldNotReachHere();
        }
        tasm.slowPaths.add(slowPath);

        masm.cmp32(asIntReg(result), Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.equal, slowPath.start);
        masm.bind(slowPath.continuation);
    }
}
