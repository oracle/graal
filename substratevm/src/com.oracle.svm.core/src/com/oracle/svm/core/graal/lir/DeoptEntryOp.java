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

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;

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
                    crb.asm.ensureUniquePC();
                    break;
                }
            }
        }
        /* Register this location as a deopt infopoint. */
        compilation.addInfopoint(new DeoptEntryInfopoint(crb.asm.position(), state.debugInfo()));
        /* Add NOP so that the next infopoint (e.g., an invoke) gets a unique PC. */
        crb.asm.ensureUniquePC();
    }

    @Override
    public boolean destroysCallerSavedRegisters() {
        return true;
    }
}
