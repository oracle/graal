/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.HotSpotCodeCacheProvider.MarkId;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.SPARCCall.DirectCallOp;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;

/**
 * A direct call that complies with the conventions for such calls in HotSpot. In particular, for
 * calls using an inline cache, a MOVE instruction is emitted just prior to the aligned direct call.
 */
@Opcode("CALL_DIRECT")
final class SPARCHotspotDirectVirtualCallOp extends DirectCallOp {

    private final InvokeKind invokeKind;

    SPARCHotspotDirectVirtualCallOp(ResolvedJavaMethod target, Value result, Value[] parameters, Value[] temps, LIRFrameState state, InvokeKind invokeKind) {
        super(target, result, parameters, temps, state);
        this.invokeKind = invokeKind;
        assert invokeKind == InvokeKind.Interface || invokeKind == InvokeKind.Virtual;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        // The mark for an invocation that uses an inline cache must be placed at the
        // instruction that loads the Klass from the inline cache.
        MarkId.recordMark(crb, invokeKind == InvokeKind.Virtual ? MarkId.INVOKEVIRTUAL : MarkId.INVOKEINTERFACE);
        Register scratchRegister = g5;
        new Setx(HotSpotGraalRuntime.runtime().getConfig().nonOopBits, scratchRegister, true).emit(masm);
        super.emitCode(crb, masm);
    }
}
