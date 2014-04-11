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
package com.oracle.graal.hotspot.sparc;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.hotspot.meta.HotSpotCodeCacheProvider.MarkId;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.SPARCCall.DirectCallOp;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

/**
 * A direct call that complies with the conventions for such calls in HotSpot. It doesn't use an
 * inline cache so it's just a patchable call site.
 */
@Opcode("CALL_DIRECT")
final class SPARCHotspotDirectStaticCallOp extends DirectCallOp {

    private final InvokeKind invokeKind;

    SPARCHotspotDirectStaticCallOp(ResolvedJavaMethod target, Value result, Value[] parameters, Value[] temps, LIRFrameState state, InvokeKind invokeKind) {
        super(target, result, parameters, temps, state);
        assert invokeKind == InvokeKind.Static || invokeKind == InvokeKind.Special;
        this.invokeKind = invokeKind;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        MarkId.recordMark(crb, invokeKind == InvokeKind.Static ? MarkId.INVOKESTATIC : MarkId.INVOKESPECIAL);
        super.emitCode(crb, masm);
    }
}
