/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.hotspot.CompileTheWorld.Options.*;

import com.oracle.graal.hotspot.CompileTheWorld.Config;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.hotspot.*;
import com.oracle.jvmci.service.*;

@ServiceProvider(HotSpotVMEventListener.class)
public class HotSpotGraalVMEventListener implements HotSpotVMEventListener {

    @Override
    public void notifyCompileTheWorld() throws Throwable {
        CompilerToVM compilerToVM = HotSpotGraalRuntime.runtime().getJVMCIRuntime().getCompilerToVM();
        int iterations = CompileTheWorld.Options.CompileTheWorldIterations.getValue();
        for (int i = 0; i < iterations; i++) {
            compilerToVM.resetCompilationStatistics();
            TTY.println("CompileTheWorld : iteration " + i);
            CompileTheWorld ctw = new CompileTheWorld(CompileTheWorldClasspath.getValue(), new Config(CompileTheWorldConfig.getValue()), CompileTheWorldStartAt.getValue(),
                            CompileTheWorldStopAt.getValue(), CompileTheWorldMethodFilter.getValue(), CompileTheWorldExcludeMethodFilter.getValue(), CompileTheWorldVerbose.getValue());
            ctw.compile();
        }
        System.exit(0);
    }

    @Override
    public void notifyShutdown() {
        HotSpotGraalRuntime.runtime().shutdown();
    }

    @Override
    public void compileMetaspaceMethod(long metaspaceMethod, int entryBCI, long jvmciEnv, int id) {
        CompilationTask.compileMetaspaceMethod(metaspaceMethod, entryBCI, jvmciEnv, id);
    }
}
