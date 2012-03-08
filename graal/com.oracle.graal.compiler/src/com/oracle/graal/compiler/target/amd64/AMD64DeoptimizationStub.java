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
package com.oracle.graal.compiler.target.amd64;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.DeoptimizeNode.DeoptAction;

public class AMD64DeoptimizationStub extends AMD64SlowPath {
    public final Label label = new Label();
    public final LIRDebugInfo info;
    public final DeoptAction action;
    public final Object deoptInfo;

    public AMD64DeoptimizationStub(DeoptAction action, LIRDebugInfo info, Object deoptInfo) {
        this.action = action;
        this.info = info;
        this.deoptInfo = deoptInfo;
    }


    private static ArrayList<Object> keepAlive = new ArrayList<>();

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        // TODO (cwimmer): we want to get rid of a generally reserved scratch register.
        CiRegister scratch = tasm.frameMap.registerConfig.getScratchRegister();

        masm.bind(label);
        if (GraalOptions.CreateDeoptInfo && deoptInfo != null) {
            masm.nop();
            keepAlive.add(deoptInfo.toString());
            AMD64Move.move(tasm, masm, scratch.asValue(), CiConstant.forObject(deoptInfo));
            // TODO Make this an explicit calling convention instead of using a scratch register
            AMD64Call.directCall(tasm, masm, CiRuntimeCall.SetDeoptInfo, info);
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
                throw GraalInternalError.shouldNotReachHere();
        }
        masm.movq(scratch, code);
     // TODO Make this an explicit calling convention instead of using a scratch register
        AMD64Call.directCall(tasm, masm, CiRuntimeCall.Deoptimize, info);
        AMD64Call.shouldNotReachHere(tasm, masm);
    }
}
