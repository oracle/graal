/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.nodes.DeoptimizeNode.DeoptAction;
import com.sun.cri.ci.*;

public class AMD64DeoptimizationStub implements LIR.SlowPath {
    public final Label label = new Label();
    public final LIRDebugInfo info;
    public final DeoptAction action;
    public final Object deoptInfo;

    public AMD64DeoptimizationStub(DeoptAction action, LIRDebugInfo info, Object deoptInfo) {
        this.action = action;
        this.info = info;
        this.deoptInfo = deoptInfo;
    }


    private static ArrayList<Object> keepAlive = new ArrayList<Object>();

    @Override
    public void emitCode(TargetMethodAssembler tasm) {
        AMD64MacroAssembler masm = (AMD64MacroAssembler) tasm.asm;

        // TODO(cwi): we want to get rid of a generally reserved scratch register.
        CiRegister scratch = tasm.compilation.registerConfig.getScratchRegister();

        masm.bind(label);
        if (GraalOptions.CreateDeoptInfo && deoptInfo != null) {
            masm.nop();
            keepAlive.add(deoptInfo.toString());
            AMD64MoveOpcode.move(tasm, masm, scratch.asValue(), CiConstant.forObject(deoptInfo));
            // TODO Why use scratch register here? Is it an implicit calling convention that the runtime function reads this register?
            AMD64CallOpcode.directCall(tasm, masm, CiRuntimeCall.SetDeoptInfo, info);
        }
        int code;
        switch(action) {
            case None:
                code = 0;
                break;
            case Recompile:
                code = 1;
                break;
            case InvalidateReprofile:
                code = 2;
                break;
            case InvalidateRecompile:
                code = 3;
                break;
            case InvalidateStopCompiling:
                code = 4;
                break;
            default:
                throw Util.shouldNotReachHere();
        }
        masm.movq(scratch, code);
        // TODO Why use scratch register here? Is it an implicit calling convention that the runtime function reads this register?
        AMD64CallOpcode.directCall(tasm, masm, CiRuntimeCall.Deoptimize, info);
        AMD64CallOpcode.shouldNotReachHere(tasm, masm);
    }
}
