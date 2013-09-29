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
package com.oracle.graal.compiler.ptx.test;

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.*;
import com.oracle.graal.api.runtime.Graal;
import com.oracle.graal.compiler.GraalCompiler;
import com.oracle.graal.compiler.ptx.PTXBackend;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.hotspot.meta.HotSpotNmethod;
import com.oracle.graal.hotspot.meta.HotSpotRuntime;
import com.oracle.graal.hotspot.meta.HotSpotResolvedJavaMethod;
import com.oracle.graal.hotspot.ptx.PTXHotSpotRuntime;
import com.oracle.graal.java.GraphBuilderConfiguration;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.ptx.ParallelOver;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.GraalCodeCacheProvider;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhasePlan;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.ptx.PTX;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

public abstract class PTXTestBase extends GraalCompilerTest {

    private StructuredGraph sg;

    protected CompilationResult compile(String test) {
        if (runtime instanceof PTXHotSpotRuntime) {
            StructuredGraph graph = parse(test);
            sg = graph;
            Debug.dump(graph, "Graph");
            TargetDescription target = new TargetDescription(new PTX(), true, 1, 0, true);
            PTXBackend ptxBackend = new PTXBackend(Graal.getRequiredCapability(GraalCodeCacheProvider.class), target);
            PhasePlan phasePlan = new PhasePlan();
            GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.NONE);
            phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
            phasePlan.addPhase(PhasePosition.AFTER_PARSING, new PTXPhase());
            new PTXPhase().apply(graph);
            CallingConvention cc = getCallingConvention(runtime, Type.JavaCallee, graph.method(), false);
            /*
             * Use Suites.createDefaultSuites() instead of GraalCompilerTest.suites. The
             * GraalCompilerTest.suites variable contains the Suites for the HotSpotRuntime. This code
             * will not run on hotspot, so it should use the plain Graal default suites, without hotspot
             * specific phases.
             *
             * Ultimately we might want to have both the kernel and the code natively compiled for GPU fallback to CPU in cases
             * of ECC failure on kernel invocation.  
             */
            CompilationResult result = GraalCompiler.compileGraph(graph, cc, graph.method(), runtime,
                                                                  graalRuntime().getReplacements(), ptxBackend, target,
                                                                  null, phasePlan,
                                                                  OptimisticOptimizations.NONE, new SpeculationLog(),
                                                                  Suites.createDefaultSuites(), new ExternalCompilationResult());
            return result;
        } else {
            return null;
        }
    }

    protected StructuredGraph getStructuredGraph() {
        return sg;
    }

    protected Object invoke(CompilationResult result, Object... args) {
        if (result == null) {
            return null;
        }
        try {
            if (((ExternalCompilationResult) result).getEntryPoint() == 0) {
                Debug.dump(result, "[CUDA] *** Null entry point - Not launching kernel");
                return null;
            }

            /* Check if the method compiled is static */
            HotSpotResolvedJavaMethod compiledMethod = (HotSpotResolvedJavaMethod) sg.method();
            boolean isStatic = Modifier.isStatic(compiledMethod.getModifiers());
            Object[] executeArgs = argsWithReceiver((isStatic ? null : this), args);
            HotSpotRuntime hsr = (HotSpotRuntime) runtime;
            InstalledCode installedCode = hsr.addExternalMethod(compiledMethod, result, sg);
            Annotation[][] params = compiledMethod.getParameterAnnotations();

            int dimensionX = 1;
            int dimensionY = 1;
            int dimensionZ = 1;

            for (int p = 0; p < params.length; p++) {
                Annotation[] annos = params[p];
                if (annos != null) {
                    for (int a = 0; a < annos.length; a++) {
                        Annotation aa = annos[a];
                        if (args[p] instanceof int[] && aa.annotationType().equals(ParallelOver.class)) {
                            int[] iarray = (int[]) args[p];
                            ParallelOver threadBlockDimension = (ParallelOver) aa;
                            switch (threadBlockDimension.dimension()) {
                                case X:
                                    dimensionX = iarray.length;
                                    break;
                                case Y:
                                    dimensionY = iarray.length;
                                    break;
                                case Z:
                                    dimensionZ = iarray.length;
                                    break;
                            }
                        }
                    }
                }
            }
            Object r;
            if (dimensionX != 1 || dimensionY != 1 || dimensionZ != 1) {
                r = ((HotSpotNmethod) installedCode).executeParallel(dimensionX, dimensionY, dimensionZ, executeArgs);
            } else {
                r = installedCode.executeVarargs(executeArgs);
            }
            return r;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }
}
