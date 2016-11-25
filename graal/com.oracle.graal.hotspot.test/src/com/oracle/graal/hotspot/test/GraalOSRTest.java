/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.api.directives.GraalDirectives;
import com.oracle.graal.bytecode.Bytecode;
import com.oracle.graal.bytecode.BytecodeStream;
import com.oracle.graal.bytecode.ResolvedJavaMethodBytecode;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.hotspot.CompilationTask;
import com.oracle.graal.hotspot.HotSpotGraalCompiler;
import com.oracle.graal.java.BciBlockMapping;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;

import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test on-stack-replacement with Graal. The test manually triggers a Graal OSR-compilation which is
 * later invoked when hitting the backedge counter overflow.
 */
public class GraalOSRTest extends GraalCompilerTest {

    @Test
    public void testOSR() {
        ResolvedJavaMethod method = getResolvedJavaMethod("test");
        // invalidate any existing compiled code
        method.reprofile();
        compileOSR(method);
        Result result = executeExpected(method, null);
        checkResult(result);
    }

    private void compileOSR(ResolvedJavaMethod method) {
        int bci = getBackedgeBCI(method);
        assert bci != -1;
        // ensure eager resolving
        parseEager(method, AllowAssumptions.YES);
        compile(method, bci);
    }

    private static void compile(ResolvedJavaMethod method, int bci) {
        HotSpotJVMCIRuntimeProvider runtime = HotSpotJVMCIRuntime.runtime();
        long jvmciEnv = 0L;
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) method, bci, jvmciEnv);
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) runtime.getCompiler();
        CompilationTask task = new CompilationTask(runtime, compiler, request, true, true);
        HotSpotCompilationRequestResult result = task.runCompilation();
        String m = result.getFailureMessage();
        assert m == null;
    }

    /**
     * Returns the target BCI of the first bytecode backedge. This is where HotSpot triggers
     * on-stack-replacement in case the backedge counter overflows.
     */
    private static int getBackedgeBCI(ResolvedJavaMethod method) {
        Bytecode code = new ResolvedJavaMethodBytecode(method);
        BytecodeStream stream = new BytecodeStream(code.getCode());
        BciBlockMapping bciBlockMapping = BciBlockMapping.create(stream, code);
        assert bciBlockMapping.getLoopCount() == 1 : "Expected exactly one loop " + method;

        for (BciBlock block : bciBlockMapping.getBlocks()) {
            int bci = block.startBci;
            for (BciBlock succ : block.getSuccessors()) {
                int succBci = succ.startBci;
                if (succBci < bci) {
                    // back edge
                    return succBci;
                }
            }
        }
        return -1;
    }

    private static void checkResult(Result result) {
        Assert.assertNull("Unexpected exception", result.exception);
        Assert.assertNotNull(result.returnValue);
        Assert.assertTrue(result.returnValue instanceof ReturnValue);
        Assert.assertEquals(ReturnValue.SUCCESS, result.returnValue);
    }

    static int limit = 10000;

    private enum ReturnValue {
        SUCCESS,
        FAILURE
    }

    public static ReturnValue test() {
        for (int i = 0; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (GraalDirectives.inCompiledCode()) {
                return ReturnValue.SUCCESS;
            }
        }
        return ReturnValue.FAILURE;
    }

}
