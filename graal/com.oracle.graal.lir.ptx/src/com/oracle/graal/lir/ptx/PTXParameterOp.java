/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.lir.ptx;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Param;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

public class PTXParameterOp extends LIRInstruction {

    @Def({REG}) protected Value[] params;
    // True if the parameter list has return argument as the last
    // item of the array params.
    private boolean hasReturnParam;

    public PTXParameterOp(Value[] params, boolean hasReturn) {
        this.params = params;
        hasReturnParam = hasReturn;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        PTXMacroAssembler masm = (PTXMacroAssembler) crb.asm;
        // Emit parameter directives for arguments
        int argCount = params.length;
        for (int i = 0; i < argCount; i++) {
            boolean isReturnParam = (hasReturnParam && (i == (argCount - 1)));
            new Param((Variable) params[i], isReturnParam).emit(masm, (i == (argCount - 1)));
        }
    }
}
