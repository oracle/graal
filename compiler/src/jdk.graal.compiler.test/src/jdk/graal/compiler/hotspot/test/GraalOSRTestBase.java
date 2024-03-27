/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hotspot.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.BytecodeDisassembler;
import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecode;
import jdk.graal.compiler.core.GraalCompilerOptions;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.CompilationTask;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class GraalOSRTestBase extends GraalCompilerTest {

    protected void testOSR(OptionValues options, String methodName) {
        testOSR(options, methodName, null);
    }

    protected void testOSR(OptionValues options, String methodName, Object receiver, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        testOSR(options, method, receiver, args);
    }

    protected void testOSR(OptionValues options, ResolvedJavaMethod method, Object receiver, Object... args) {
        // invalidate any existing compiled code
        method.reprofile();
        compileOSR(options, method);
        Result result = executeExpected(method, receiver, args);
        checkResult(result);
    }

    protected static void compile(DebugContext debug, ResolvedJavaMethod method, int bci) {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        long jvmciEnv = 0L;
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) method, bci, jvmciEnv);
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) runtime.getCompiler();
        CompilationTask task = new CompilationTask(runtime, compiler, request, true, false, true, true);
        if (method instanceof HotSpotResolvedJavaMethod) {
            HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();
            GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
            if (((HotSpotResolvedJavaMethod) method).hasCodeAtLevel(bci, config.compilationLevelFullOptimization)) {
                return;
            }
        }
        HotSpotCompilationRequestResult result = task.runCompilation(debug);
        if (result.getFailure() != null) {
            throw new GraalError(result.getFailureMessage());
        }
    }

    /**
     * Returns the target BCIs of all bytecode backedges.
     */
    public int[] getBackedgeBCIs(DebugContext debug, ResolvedJavaMethod method) {
        Bytecode code = new ResolvedJavaMethodBytecode(method);
        BytecodeStream stream = new BytecodeStream(code.getCode());
        OptionValues options = debug.getOptions();
        BciBlockMapping bciBlockMapping = BciBlockMapping.create(stream, code, options, debug, true);

        List<Integer> backedgeBcis = new ArrayList<>();
        for (BciBlockMapping.BciBlock block : bciBlockMapping.getBlocks()) {
            if (block.getStartBci() != -1) {
                int bci = block.getEndBci();
                for (BciBlockMapping.BciBlock succ : block.getSuccessors()) {
                    if (succ.getStartBci() != -1) {
                        int succBci = succ.getStartBci();
                        if (succBci < bci) {
                            // back edge
                            backedgeBcis.add(succBci);
                        }
                    }
                }
            }
        }
        return backedgeBcis.stream().mapToInt(Integer::intValue).toArray();
    }

    protected static void checkResult(Result result) {
        if (result.exception != null) {
            throw new AssertionError(result.exception);
        }
        Assert.assertNotNull(result.returnValue);
        Assert.assertTrue(result.returnValue instanceof ReturnValue);
        Assert.assertEquals(ReturnValue.SUCCESS, result.returnValue);
    }

    protected void compileOSR(OptionValues options, ResolvedJavaMethod method) {
        compileOSR(options, method, true);
    }

    protected void compileOSR(OptionValues options, ResolvedJavaMethod method, boolean expectBackedge) {
        OptionValues goptions = options;
        // Silence diagnostics for permanent bailout errors as they
        // are expected for some OSR tests.
        if (!GraalCompilerOptions.CompilationBailoutAsFailure.hasBeenSet(options)) {
            goptions = new OptionValues(options, GraalCompilerOptions.CompilationBailoutAsFailure, false);
        }
        // ensure eager resolving
        StructuredGraph graph = parseEager(method, AllowAssumptions.YES, goptions);
        DebugContext debug = graph.getDebug();
        int[] backedgeBCIs = getBackedgeBCIs(debug, method);
        if (expectBackedge && backedgeBCIs.length == 0) {
            Bytecode code = new ResolvedJavaMethodBytecode(method);
            throw new AssertionError(String.format("Cannot find any loop back edges in %s:%n%s", method.format("%H.%n(%p)"),
                            new BytecodeDisassembler().disassemble(code)));

        }
        for (int bci : backedgeBCIs) {
            compile(debug, method, bci);
        }
    }

    protected enum ReturnValue {
        SUCCESS,
        FAILURE,
        SIDE
    }

    public GraalOSRTestBase() {
        super();
    }

    public GraalOSRTestBase(Class<? extends Architecture> arch) {
        super(arch);
    }

    public GraalOSRTestBase(Backend backend) {
        super(backend);
    }

}
