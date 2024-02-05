/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.graal.lir;

import java.util.List;

import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.site.Infopoint;

/**
 * A pseudo-instruction which is the landing-pad for deoptimization. This instruction is generated
 * in deopt target methods for all deoptimization entry points.
 */
public final class DeoptEntryOp extends LIRInstruction {
    public static final LIRInstructionClass<DeoptEntryOp> TYPE = LIRInstructionClass.create(DeoptEntryOp.class);

    @State protected LIRFrameState state;

    public DeoptEntryOp(LIRFrameState state) {
        super(TYPE);
        this.state = state;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        CompilationResult compilation = crb.compilationResult;

        /* Search for the previous added info point. */
        List<Infopoint> infoPoints = compilation.getInfopoints();
        int size = infoPoints.size();
        for (int idx = size - 1; idx >= 0; idx--) {
            Infopoint infopoint = infoPoints.get(idx);
            int entryOffset = CodeInfoEncoder.getEntryOffset(infopoint);
            if (entryOffset >= 0) {
                if (entryOffset == crb.asm.position()) {
                    /* Found prior info point at pc; increment pc to ensure uniqueness. */
                    crb.asm.ensureUniquePC();
                    break;
                }
            }
        }
        /* Register this location as a deopt infopoint. */
        int position = crb.asm.position();
        compilation.addInfopoint(new DeoptEntryInfopoint(position, state.debugInfo()));
        /* Need to register exception edge, if present. */
        crb.recordExceptionHandlers(position, state);
        if (SubstrateControlFlowIntegrity.useSoftwareCFI()) {
            /* Mark this position as a CFI Target. */
            int initialPos = crb.asm.position();
            crb.asm.maybeEmitIndirectTargetMarker();
            assert initialPos != crb.asm.position() : "No target marker emitted. Position: " + initialPos;
        } else {
            /* Add NOP so that the next info point (e.g., an invoke) gets a unique PC. */
            crb.asm.ensureUniquePC();
        }
    }

    @Override
    public boolean destroysCallerSavedRegisters() {
        return true;
    }
}
