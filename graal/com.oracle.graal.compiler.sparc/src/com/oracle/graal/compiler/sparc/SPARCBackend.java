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
package com.oracle.graal.compiler.sparc;

import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.CodeCacheProvider;
import com.oracle.graal.api.code.CompilationResult;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.asm.AbstractAssembler;
import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.compiler.gen.LIRGenerator;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.lir.FrameMap;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.asm.FrameContext;
import com.oracle.graal.lir.asm.TargetMethodAssembler;
import com.oracle.graal.nodes.StructuredGraph;

public class SPARCBackend extends Backend {

    public SPARCBackend(CodeCacheProvider runtime, TargetDescription target) {
        super(runtime, target);
    }

    @Override
    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, CallingConvention cc, LIR lir) {
        return new SPARCLIRGenerator(graph, runtime(), target, frameMap, cc, lir);
    }

    @Override
    protected AbstractAssembler createAssembler(FrameMap frameMap) {
        return new SPARCAssembler(target, frameMap.registerConfig);
    }

    class HotSpotFrameContext implements FrameContext {

        @Override
        public void enter(TargetMethodAssembler tasm) {
        }

        @Override
        public void leave(TargetMethodAssembler tasm) {
        }
    }
    @Override
    public TargetMethodAssembler newAssembler(LIRGenerator lirGen, CompilationResult compilationResult) {
        FrameMap frameMap = lirGen.frameMap;
        AbstractAssembler masm = createAssembler(frameMap);
        HotSpotFrameContext frameContext = new HotSpotFrameContext();
        TargetMethodAssembler tasm = new TargetMethodAssembler(target, runtime(), frameMap, masm, frameContext, compilationResult);
        tasm.setFrameSize(frameMap.frameSize());
        return tasm;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, LIRGenerator lirGen, ResolvedJavaMethod installedCodeOwner) {

        // Emit code for the LIR
        lirGen.lir.emitCode(tasm);

    }


}
