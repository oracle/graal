/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.target;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

/**
 * The {@code Backend} class represents a compiler backend for Graal.
 */
public abstract class Backend {

    public final CodeCacheProvider runtime;
    public final TargetDescription target;

    protected Backend(CodeCacheProvider runtime, TargetDescription target) {
        this.runtime = runtime;
        this.target = target;
    }

    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new FrameMap(runtime, target, registerConfig);
    }

    public abstract LIRGenerator newLIRGenerator(Graph graph, FrameMap frameMap, ResolvedJavaMethod method, LIR lir);

    public abstract TargetMethodAssembler newAssembler(FrameMap frameMap, LIR lir);

    /**
     * Emits code to do stack overflow checking.
     *
     * @param afterFrameInit specifies if the stack pointer has already been adjusted to allocate the current frame
     */
    protected static void emitStackOverflowCheck(TargetMethodAssembler tasm, boolean afterFrameInit) {
        if (GraalOptions.StackShadowPages > 0) {
            int frameSize = tasm.frameMap.frameSize();
            if (frameSize > 0) {
                int lastFramePage = frameSize / tasm.target.pageSize;
                // emit multiple stack bangs for methods with frames larger than a page
                for (int i = 0; i <= lastFramePage; i++) {
                    int disp = (i + GraalOptions.StackShadowPages) * tasm.target.pageSize;
                    if (afterFrameInit) {
                        disp -= frameSize;
                    }
                    tasm.blockComment("[stack overflow check]");
                    tasm.asm.bangStack(disp);
                }
            }
        }
    }

    /**
     * Emits the code for a given method. This includes any architecture/runtime specific
     * prefix/suffix. A prefix typically contains the code for setting up the frame,
     * spilling callee-save registers, stack overflow checking, handling multiple entry
     * points etc. A suffix may contain out-of-line stubs and method end guard instructions.
     *
     * @param method the method associated with {@code lir}
     * @param lir the LIR of {@code method}
     */
    public abstract void emitCode(TargetMethodAssembler tasm, ResolvedJavaMethod method, LIR lir);
}
