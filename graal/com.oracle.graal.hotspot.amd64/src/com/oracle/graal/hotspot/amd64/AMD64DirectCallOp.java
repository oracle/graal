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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Call.DirectCallOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

/**
 * A direct call that complies with the conventions for such calls in HotSpot.
 * In particular, for calls using an inline cache, a MOVE instruction is
 * emitted just prior to the aligned direct call. This instruction
 * (which moves 0L in RAX) is patched by the C++ Graal code to replace the
 * 0L constant with Universe::non_oop_word(), a special sentinel
 * used for the initial value of the Klass in an inline cache.
 * <p>
 * For non-inline cache calls (i.e., INVOKESTATIC and INVOKESPECIAL), a static
 * call stub is emitted. Initially, these calls go to the global static call
 * resolution stub (i.e., SharedRuntime::get_resolve_static_call_stub()).
 * Resolution will link the call to a compiled version of the callee if
 * available otherwise to the interpreter. The interpreter expects to
 * find the Method* for the callee in RBX. To achieve this, the static call
 * is linked to a static call stub which initializes RBX and jumps to the
 * interpreter. This pattern is shown below:
 * <pre>
 *       call L1
 *       nop
 *
 *       ...
 *
 *   L1: mov rbx [Method*]
 *       jmp [interpreter entry point]
 * </pre>
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

    AMD64DirectCallOp(Object targetMethod, Value result, Value[] parameters, Value[] temps, LIRFrameState state, InvokeKind invokeKind, LIR lir) {
        super(targetMethod, result, parameters, temps, state);
        this.invokeKind = invokeKind;

        if (invokeKind == Static || invokeKind == Special) {
            lir.stubs.add(new AMD64Code() {
                public String description() {
                    return "static call stub for Invoke" + AMD64DirectCallOp.this.invokeKind;
                }
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    assert callsiteMark != null : "static call site has not yet been emitted";
                    tasm.recordMark(Marks.MARK_STATIC_CALL_STUB, callsiteMark);
                    masm.movq(AMD64.rbx, 0L);
                    int pos = masm.codeBuffer.position();
                    // Create a jump-to-self as expected by CompiledStaticCall::set_to_interpreted() in compiledIC.cpp
                    masm.jmp(pos, true);
                }
            });
        }

    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        if (invokeKind == Static || invokeKind == Special) {
            tasm.recordMark(invokeKind == Static ? Marks.MARK_INVOKESTATIC : Marks.MARK_INVOKESPECIAL);
        } else {
            assert invokeKind == Virtual || invokeKind == Interface;
            // The mark for an invocation that uses an inline cache must be placed at the instruction
            // that loads the Klass from the inline cache so that the C++ code can find it
            // and replace the inline 0L value with Universe::non_oop_word()
            tasm.recordMark(invokeKind == Virtual ? Marks.MARK_INVOKEVIRTUAL : Marks.MARK_INVOKEINTERFACE);
            AMD64Move.move(tasm, masm, AMD64.rax.asValue(Kind.Long), Constant.LONG_0);
        }

        emitAlignmentForDirectCall(tasm, masm);

        if (invokeKind == Static || invokeKind == Special) {
            callsiteMark = tasm.recordMark(null);
        }

        AMD64Call.directCall(tasm, masm, targetMethod, state);
    }
}
