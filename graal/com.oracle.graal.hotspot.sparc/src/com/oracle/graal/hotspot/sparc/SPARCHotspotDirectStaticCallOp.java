/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.jvmci.meta.ResolvedJavaMethod;
import com.oracle.jvmci.meta.Value;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.SPARCCall.DirectCallOp;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.jvmci.hotspot.*;

/**
 * A direct call that complies with the conventions for such calls in HotSpot. It doesn't use an
 * inline cache so it's just a patchable call site.
 */
@Opcode("CALL_DIRECT")
final class SPARCHotspotDirectStaticCallOp extends DirectCallOp {
    public static final LIRInstructionClass<SPARCHotspotDirectStaticCallOp> TYPE = LIRInstructionClass.create(SPARCHotspotDirectStaticCallOp.class);

    private final InvokeKind invokeKind;
    private final HotSpotVMConfig config;

    SPARCHotspotDirectStaticCallOp(ResolvedJavaMethod target, Value result, Value[] parameters, Value[] temps, LIRFrameState state, InvokeKind invokeKind, HotSpotVMConfig config) {
        super(TYPE, target, result, parameters, temps, state);
        assert invokeKind.isDirect();
        this.invokeKind = invokeKind;
        this.config = config;
    }

    @Override
    public void emitCallPrefixCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        crb.recordMark(invokeKind == InvokeKind.Static ? config.MARKID_INVOKESTATIC : config.MARKID_INVOKESPECIAL);
    }
}
