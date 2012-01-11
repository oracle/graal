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

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;

class AMD64ConvertFSlowPath implements LIR.SlowPath {

    public final Label start = new Label();
    public final Label continuation = new Label();

    private final CiRegister result;
    private final CiRegister input;
    private final AMD64MacroAssembler masm;
    private final boolean inputIsDouble;
    private final boolean resultIsLong;

    public AMD64ConvertFSlowPath(AMD64MacroAssembler masm, CiRegister result, CiRegister input, boolean inputIsDouble, boolean resultIsLong) {
        this.masm = masm;
        this.result = result;
        this.input = input;
        this.inputIsDouble = inputIsDouble;
        this.resultIsLong = resultIsLong;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm) {
        masm.bind(start);
        if (inputIsDouble) {
            masm.ucomisd(input, tasm.asDoubleConstRef(CiConstant.DOUBLE_0));
        } else {
            masm.ucomiss(input, tasm.asFloatConstRef(CiConstant.FLOAT_0));
        }
        Label nan = new Label();
        masm.jcc(ConditionFlag.parity, nan);
        masm.jcc(ConditionFlag.below, continuation);

        // input is > 0 -> return maxInt
        // result register already contains 0x80000000, so subtracting 1 gives 0x7fffffff
        if (resultIsLong) {
            masm.decrementq(result, 1);
        } else {
            masm.decrementl(result, 1);
        }
        masm.jmp(continuation);

        // input is NaN -> return 0
        masm.bind(nan);
        masm.xorptr(result, result);
        masm.jmp(continuation);
    }
}
