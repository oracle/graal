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

import com.oracle.graal.api.code.CodeCacheProvider;
import com.oracle.graal.api.code.CompilationResult;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.asm.AbstractAssembler;
import com.oracle.graal.hotspot.HotSpotGraalRuntime;
import com.oracle.graal.hotspot.bridge.CompilerToGPU;
import com.oracle.graal.hotspot.meta.HotSpotMethod;
import com.oracle.graal.lir.FrameMap;
import com.oracle.graal.lir.asm.FrameContext;
import com.oracle.graal.lir.asm.TargetMethodAssembler;

public class PTXTargetMethodAssembler extends TargetMethodAssembler {

    private static CompilerToGPU toGPU = HotSpotGraalRuntime.graalRuntime().getCompilerToGPU();
    private static boolean validDevice = toGPU.deviceInit();

    // detach ??

    public PTXTargetMethodAssembler(TargetDescription target,
                                    CodeCacheProvider runtime, FrameMap frameMap,
                                    AbstractAssembler asm, FrameContext frameContext,
                                    CompilationResult compilationResult) {
        super(target, runtime, frameMap, asm, frameContext, compilationResult);
    }

    @Override
    public CompilationResult finishTargetMethod(Object name, boolean isStub) {
        CompilationResult graalCompile = super.finishTargetMethod(name, isStub);

        try {
            if (validDevice) {
                HotSpotMethod method = (HotSpotMethod) name;
                toGPU.generateKernel(graalCompile.getTargetCode(), method.getName());
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }

        return graalCompile;  // for now
    }
}
