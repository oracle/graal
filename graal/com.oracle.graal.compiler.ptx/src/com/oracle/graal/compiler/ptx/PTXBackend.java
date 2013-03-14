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
package com.oracle.graal.compiler.ptx;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;

/**
 * PTX specific backend.
 */
public class PTXBackend extends Backend {

    public PTXBackend(CodeCacheProvider runtime, TargetDescription target) {
        super(runtime, target);
    }

    @Override
    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, ResolvedJavaMethod method, LIR lir) {
        return new PTXLIRGenerator(graph, runtime(), target, frameMap, method, lir);
    }

    class HotSpotFrameContext implements FrameContext {

        @Override
        public void enter(TargetMethodAssembler tasm) {
            Buffer codeBuffer = tasm.asm.codeBuffer;
            codeBuffer.emitString(".version 1.4");
            codeBuffer.emitString(".target sm_10");
            // codeBuffer.emitString(".address_size 32"); // PTX ISA version 2.3
        }

        @Override
        public void leave(TargetMethodAssembler tasm) {
        }
    }

    @Override
    public TargetMethodAssembler newAssembler(LIRGenerator lirGen, CompilationResult compilationResult) {
        // Omit the frame if the method:
        // - has no spill slots or other slots allocated during register allocation
        // - has no callee-saved registers
        // - has no incoming arguments passed on the stack
        // - has no instructions with debug info
        FrameMap frameMap = lirGen.frameMap;
        AbstractAssembler masm = new PTXAssembler(target, frameMap.registerConfig);
        HotSpotFrameContext frameContext = new HotSpotFrameContext();
        TargetMethodAssembler tasm = new TargetMethodAssembler(target, runtime(), frameMap, masm, frameContext, compilationResult);
        tasm.setFrameSize(frameMap.frameSize());
        return tasm;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, ResolvedJavaMethod method, LIRGenerator lirGen) {
        // Emit the prologue
        final String name = method.getName();
        Buffer codeBuffer = tasm.asm.codeBuffer;
        codeBuffer.emitString0(".entry " + name + " (");
        codeBuffer.emitString("");

        Signature signature = method.getSignature();
        for (int i = 0; i < signature.getParameterCount(false); i++) {
            System.err.println(i + ": " + signature.getParameterKind(i));
            String param = ".param .u32 param" + i;
            codeBuffer.emitString(param);
        }

        codeBuffer.emitString0(") {");
        codeBuffer.emitString("");

        // XXX For now declare one predicate and all registers
        codeBuffer.emitString("  .reg .pred %p;");
        codeBuffer.emitString("  .reg .u32 %r<16>;");

        // Emit code for the LIR
        lirGen.lir.emitCode(tasm);

        // Emit the epilogue
        codeBuffer.emitString0("}");
        codeBuffer.emitString("");

        byte[] data = codeBuffer.copyData(0, codeBuffer.position());
        System.err.println(new String(data));
    }
}
