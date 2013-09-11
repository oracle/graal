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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

public class PTXParameterOp extends LIRInstruction {

    @Def({REG}) protected Value[] params;

    public PTXParameterOp(Value[] params) {
        this.params = params;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm) {
        PTXAssembler masm = (PTXAssembler) tasm.asm;
        // Emit parameter directives for arguments
        int argCount = params.length;
        for (int i = 0; i < argCount; i++) {
            Kind paramKind = params[i].getKind();
            switch (paramKind) {
            case Byte :
                masm.param_8_decl(asRegister(params[i]), (i == (argCount - 1)));
                break;
            case Int :
                masm.param_32_decl(asIntReg(params[i]), (i == (argCount - 1)));
                break;
            case Long :
                masm.param_64_decl(asLongReg(params[i]), (i == (argCount - 1)));
                break;
            case Float :
                masm.param_32_decl(asFloatReg(params[i]), (i == (argCount - 1)));
                break;
            case Double :
                masm.param_64_decl(asDoubleReg(params[i]), (i == (argCount - 1)));
                break;
            default :
                throw GraalInternalError.shouldNotReachHere("unhandled parameter type "  + paramKind.toString());
            }
        }
    }
}
