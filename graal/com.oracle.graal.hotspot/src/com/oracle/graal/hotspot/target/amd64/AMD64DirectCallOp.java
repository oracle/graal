/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.target.amd64;

import static com.oracle.graal.hotspot.target.amd64.HotSpotAMD64Backend.*;
import static com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind.*;

import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Call.DirectCallOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;

/**
 * A direct call that complies with the conventions for such calls in HotSpot.
 * In particular, for calls using an inline cache, a MOVE instruction is
 * emitted just prior to the aligned direct call. This instruction
 * (which moves null in RAX) is patched by the C++ Graal code to replace the
 * null constant with Universe::non_oop_word(), a special sentinel
 * used for the initial value of the klassOop in an inline cache.
 * <p>
 * For non-inline cache calls, a static call stub is emitted.
 */
@Opcode("CALL_DIRECT")
final class AMD64DirectCallOp extends DirectCallOp {

    /**
     * The mark emitted at the position of the direct call instruction.
     * This is only recorded for calls that have an associated static
     * call stub (i.e., {@code invokeKind == Static || invokeKind == Special}).
     */
    Mark callsiteMark;

    private final InvokeKind invokeKind;

    AMD64DirectCallOp(Object targetMethod, Value result, Value[] parameters, LIRFrameState state, InvokeKind invokeKind, LIR lir) {
        super(targetMethod, result, parameters, state, null);
        this.invokeKind = invokeKind;

        if (invokeKind == Static || invokeKind == Special) {
            lir.stubs.add(new AMD64Code() {
                public String description() {
                    return "static call stub for Invoke" + AMD64DirectCallOp.this.invokeKind;
                }
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    assert callsiteMark != null : "static call site has not yet been emitted";
                    tasm.recordMark(MARK_STATIC_CALL_STUB, callsiteMark);
                    masm.movq(AMD64.rbx, 0L);
                    Label dummy = new Label();
                    masm.jmp(dummy);
                    masm.bind(dummy);
                }
            });
        }

    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        if (invokeKind == Static || invokeKind == Special) {
            tasm.recordMark(invokeKind == Static ? MARK_INVOKESTATIC : MARK_INVOKESPECIAL);
        } else {
            assert invokeKind == Virtual || invokeKind == Interface;
            // The mark for an invocation that uses an inline cache must be placed at the instruction
            // that loads the klassOop from the inline cache so that the C++ code can find it
            // and replace the inline null value with Universe::non_oop_word()
            tasm.recordMark(invokeKind == Virtual ? MARK_INVOKEVIRTUAL : MARK_INVOKEINTERFACE);
            AMD64Move.move(tasm, masm, AMD64.rax.asValue(Kind.Object), Constant.NULL_OBJECT);
        }

        emitAlignmentForDirectCall(tasm, masm);

        if (invokeKind == Static || invokeKind == Special) {
            callsiteMark = tasm.recordMark(null);
        }

        AMD64Call.directCall(tasm, masm, targetMethod, state);
    }
}
