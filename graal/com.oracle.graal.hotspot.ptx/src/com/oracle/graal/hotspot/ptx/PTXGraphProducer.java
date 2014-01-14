/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.ptx;

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.compiler.GraalCompiler.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotReplacementsImpl.GraphProducer;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

public class PTXGraphProducer implements GraphProducer {
    private final CompilerToGPU toGPU;
    private final HotSpotBackend hostBackend;
    private final HotSpotBackend ptxBackend;

    public PTXGraphProducer(HotSpotBackend hostBackend, HotSpotBackend ptxBackend) {
        this.hostBackend = hostBackend;
        this.ptxBackend = ptxBackend;
        this.toGPU = ptxBackend.getRuntime().getCompilerToGPU();
    }

    public StructuredGraph getGraphFor(ResolvedJavaMethod method) {
        if (canOffloadToGPU(method)) {

            StructuredGraph graph = new StructuredGraph(method);
            HotSpotProviders providers = ptxBackend.getProviders();
            CallingConvention cc = getCallingConvention(providers.getCodeCache(), Type.JavaCallee, method, false);
            PhaseSuite<HighTierContext> graphBuilderSuite = providers.getSuites().getDefaultGraphBuilderSuite();
            Suites suites = providers.getSuites().getDefaultSuites();
            ExternalCompilationResult kernelResult = compileGraph(graph, cc, method, providers, ptxBackend, ptxBackend.getTarget(), null, graphBuilderSuite, OptimisticOptimizations.NONE,
                            getProfilingInfo(graph), new SpeculationLog(), suites, true, new ExternalCompilationResult(), CompilationResultBuilderFactory.Default);

            try (Scope ds = Debug.scope("GeneratingKernel")) {
                long kernel = toGPU.generateKernel(kernelResult.getTargetCode(), method.getName());
                kernelResult.setEntryPoint(kernel);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            InstalledCode installedCode = providers.getCodeCache().addExternalMethod(method, kernelResult);
            return new PTXLaunchKernelGraphKit(method, installedCode.getStart(), hostBackend.getProviders()).getGraph();
        }
        return null;
    }

    protected boolean canOffloadToGPU(ResolvedJavaMethod method) {
        return method.getName().contains("lambda$main$") & method.isSynthetic();
    }
}
