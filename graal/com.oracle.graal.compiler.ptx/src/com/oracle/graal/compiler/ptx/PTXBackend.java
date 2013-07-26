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
    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, CallingConvention cc, LIR lir) {
        return new PTXLIRGenerator(graph, runtime(), target, frameMap, cc, lir);
    }

    class HotSpotFrameContext implements FrameContext {

        @Override
        public void enter(TargetMethodAssembler tasm) {
            // codeBuffer.emitString(".address_size 32"); // PTX ISA version 2.3
        }

        @Override
        public void leave(TargetMethodAssembler tasm) {
        }
    }

    @Override
    protected AbstractAssembler createAssembler(FrameMap frameMap) {
        return new PTXAssembler(target, frameMap.registerConfig);
    }

    @Override
    public TargetMethodAssembler newAssembler(LIRGenerator lirGen, CompilationResult compilationResult) {
        // Omit the frame if the method:
        // - has no spill slots or other slots allocated during register allocation
        // - has no callee-saved registers
        // - has no incoming arguments passed on the stack
        // - has no instructions with debug info
        FrameMap frameMap = lirGen.frameMap;
        AbstractAssembler masm = createAssembler(frameMap);
        HotSpotFrameContext frameContext = new HotSpotFrameContext();
        TargetMethodAssembler tasm = new PTXTargetMethodAssembler(target, runtime(), frameMap, masm, frameContext, compilationResult);
        tasm.setFrameSize(frameMap.frameSize());
        return tasm;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, LIRGenerator lirGen, ResolvedJavaMethod codeCacheOwner) {
        // Emit the prologue
        assert codeCacheOwner != null : lirGen.getGraph() + " is not associated with a method";
        final String name = codeCacheOwner.getName();
        Buffer codeBuffer = tasm.asm.codeBuffer;
        codeBuffer.emitString(".version 1.4");
        codeBuffer.emitString(".target sm_10");
        codeBuffer.emitString0(".entry " + name + " (");
        codeBuffer.emitString("");

        Signature signature = codeCacheOwner.getSignature();
        int paramCount = signature.getParameterCount(false);
        // TODO - Revisit this.
        // Bit-size of registers to be declared and used by the kernel.
        int regSize = 32;
        for (int i = 0; i < paramCount; i++) {
            String param;
            // No unsigned types in Java. So using .s specifier
            switch (signature.getParameterKind(i)) {
                case Boolean:
                case Byte:
                    param = ".param .s8 param" + i;
                    regSize = 8;
                    break;
                case Char:
                case Short:
                    param = ".param .s16 param" + i;
                    regSize = 16;
                    break;
                case Int:
                    param = ".param .s32 param" + i;
                    regSize = 32;
                    break;
                case Long:
                case Float:
                case Double:
                case Void:
                    param = ".param .s64 param" + i;
                    regSize = 32;
                    break;
                default:
                    // Not sure but specify 64-bit specifier??
                    param = ".param .s64 param" + i;
                    break;
            }
            if (i != (paramCount - 1)) {
                param += ",";
            }
            codeBuffer.emitString(param);
        }

        codeBuffer.emitString0(") {");
        codeBuffer.emitString("");

        // XXX For now declare one predicate and all registers
        codeBuffer.emitString("  .reg .pred %p,%q;");
        codeBuffer.emitString("  .reg .s" + regSize + " %r<16>;");

        // Emit code for the LIR
        lirGen.lir.emitCode(tasm);

        // Emit the epilogue
        codeBuffer.emitString0("}");
        codeBuffer.emitString("");
    }
}
