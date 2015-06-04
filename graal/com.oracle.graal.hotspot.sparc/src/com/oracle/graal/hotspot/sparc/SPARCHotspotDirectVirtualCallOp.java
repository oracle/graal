/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.jvmci.code.Register;
import com.oracle.jvmci.meta.ResolvedJavaMethod;
import com.oracle.jvmci.meta.Value;

import static com.oracle.jvmci.sparc.SPARC.*;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.SPARCCall.DirectCallOp;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.jvmci.hotspot.*;

/**
 * A direct call that complies with the conventions for such calls in HotSpot. In particular, for
 * calls using an inline cache, a MOVE instruction is emitted just prior to the aligned direct call.
 */
@Opcode("CALL_DIRECT")
final class SPARCHotspotDirectVirtualCallOp extends DirectCallOp {
    public static final LIRInstructionClass<SPARCHotspotDirectVirtualCallOp> TYPE = LIRInstructionClass.create(SPARCHotspotDirectVirtualCallOp.class);

    private final InvokeKind invokeKind;
    private final HotSpotVMConfig config;

    SPARCHotspotDirectVirtualCallOp(ResolvedJavaMethod target, Value result, Value[] parameters, Value[] temps, LIRFrameState state, InvokeKind invokeKind, HotSpotVMConfig config) {
        super(TYPE, target, result, parameters, temps, state);
        this.invokeKind = invokeKind;
        this.config = config;
        assert invokeKind.isIndirect();
    }

    @Override
    public void emitCallPrefixCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        // The mark for an invocation that uses an inline cache must be placed at the
        // instruction that loads the Klass from the inline cache.
        crb.recordMark(invokeKind == InvokeKind.Virtual ? config.MARKID_INVOKEVIRTUAL : config.MARKID_INVOKEINTERFACE);
        Register scratchRegister = g5;
        new Setx(config.nonOopBits, scratchRegister, true).emit(masm);
    }
}
