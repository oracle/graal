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

import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.SPARCCall.DirectCallOp;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

/**
 * A direct call that complies with the conventions for such calls in HotSpot. In particular, for
 * calls using an inline cache, a MOVE instruction is emitted just prior to the aligned direct call.
 * This instruction (which moves 0L in G3) is patched by the C++ Graal code to replace the 0L
 * constant with Universe::non_oop_word(), a special sentinel used for the initial value of the
 * Klass in an inline cache. It puts the called method into G5 before calling.
 */
@Opcode("CALL_DIRECT")
final class SPARCHotspotDirectStaticCallOp extends DirectCallOp {

    private final Constant metaspaceMethod;
    private final InvokeKind invokeKind;

    SPARCHotspotDirectStaticCallOp(ResolvedJavaMethod target, Value result, Value[] parameters, Value[] temps, LIRFrameState state, InvokeKind invokeKind, Constant metaspaceMethod) {
        super(target, result, parameters, temps, state);
        assert invokeKind == InvokeKind.Static || invokeKind == InvokeKind.Special;
        this.metaspaceMethod = metaspaceMethod;
        this.invokeKind = invokeKind;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
        // The mark for an invocation that uses an inline cache must be placed at the
        // instruction that loads the Klass from the inline cache so that the C++ code can find it
        // and replace the inline 0L value with Universe::non_oop_word()
// SPARCMove.move(tasm, masm, g5.asValue(Kind.Long), tasm.asLongConstRef(metaspaceMethod));

        new Rdpc(g5).emit(masm);
        tasm.asLongConstRef(metaspaceMethod);
        new Ldx(new SPARCAddress(g5, 0), g5).emit(masm);
        tasm.recordMark(invokeKind == InvokeKind.Static ? Marks.MARK_INVOKESTATIC : Marks.MARK_INVOKESPECIAL);
        // XXX move must be patchable!
        SPARCMove.move(tasm, masm, g3.asValue(Kind.Long), Constant.LONG_0);
        new Nop().emit(masm);
        new Nop().emit(masm);
        new Nop().emit(masm);
        new Nop().emit(masm);
        new Nop().emit(masm);
        new Nop().emit(masm);
        super.emitCode(tasm, masm);
    }
}
