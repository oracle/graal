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

import com.oracle.graal.compiler.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;

public class AMD64DeoptimizationStub extends AMD64SlowPath {
    public final Label label = new Label();
    public final LIRDebugInfo info;
    public final DeoptAction action;
    public final DeoptReason reason;
    public final Object deoptInfo;

    public AMD64DeoptimizationStub(DeoptAction action, DeoptReason reason, LIRDebugInfo info, Object deoptInfo) {
        this.action = action;
        this.reason = reason;
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

        masm.movl(scratch, encodeDeoptActionAndReason(action, reason));
        // TODO Make this an explicit calling convention instead of using a scratch register
        AMD64Call.directCall(tasm, masm, CiRuntimeCall.Deoptimize, info);
        AMD64Call.shouldNotReachHere(tasm, masm);
    }

    // TODO (chaeubl) this is HotSpot specific -> move it somewhere else
    public static int encodeDeoptActionAndReason(DeoptAction action, DeoptReason reason) {
        final int actionShift = 0;
        final int reasonShift = 3;

        int actionValue = getDeoptActionValue(action);
        int reasonValue = getDeoptReasonValue(reason);
        return (~(((reasonValue) << reasonShift) + ((actionValue) << actionShift)));
    }

    // TODO (chaeubl) this is HotSpot specific -> move it somewhere else
    private static int getDeoptActionValue(DeoptAction action) {
        switch(action) {
            case None: return 0;
            case RecompileIfTooManyDeopts: return 1;
            case InvalidateReprofile: return 2;
            case InvalidateRecompile: return 3;
            case InvalidateStopCompiling: return 4;
            default: throw GraalInternalError.shouldNotReachHere();
        }
    }

    // TODO (chaeubl) this is HotSpot specific -> move it somewhere else
    private static int getDeoptReasonValue(DeoptReason reason) {
        switch(reason) {
            case None: return 0;
            case NullCheckException: return 1;
            case BoundsCheckException: return 2;
            case ClassCastException: return 3;
            case ArrayStoreException: return 4;
            case UnreachedCode: return 5;
            case TypeCheckedInliningViolated: return 6;
            case OptimizedTypeCheckViolated: return 7;
            case NotCompiledExceptionHandler: return 8;
            case Unresolved: return 9;
            case JavaSubroutineMismatch: return 10;
            case ArithmeticException: return 11;
            case RuntimeConstraint: return 12;
            default: throw GraalInternalError.shouldNotReachHere();
        }
    }
}
