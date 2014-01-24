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

package com.oracle.graal.hotspot.hsail;

import static com.oracle.graal.compiler.GraalCompiler.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.runtime.*;

/**
 * Class that represents a HSAIL compilation result. Includes the compiled HSAIL code.
 */
public class HSAILCompilationResult extends ExternalCompilationResult {

    private static final long serialVersionUID = -4178700465275724625L;

    private static CompilerToGPU toGPU = HotSpotGraalRuntime.runtime().getCompilerToGPU();
    private static boolean validDevice = toGPU.deviceInit();

    // The installedCode is the executable representation of the kernel in the code cache
    private InstalledCode installedCode;

    public void setInstalledCode(InstalledCode newCode) {
        installedCode = newCode;
    }

    public InstalledCode getInstalledCode() {
        return installedCode;
    }

    static final HSAILHotSpotBackend backend;
    static {
        // Look for installed HSAIL backend
        RuntimeProvider runtime = Graal.getRequiredCapability(RuntimeProvider.class);
        HSAILHotSpotBackend b = (HSAILHotSpotBackend) runtime.getBackend(HSAIL.class);
        if (b == null) {
            // Fall back to a new instance
            b = new HSAILHotSpotBackendFactory().createBackend(runtime(), runtime().getHostBackend());
            b.completeInitialization();
        }
        backend = b;
    }

    public static HSAILCompilationResult getHSAILCompilationResult(Method meth) {
        HotSpotMetaAccessProvider metaAccess = backend.getProviders().getMetaAccess();
        ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(meth);
        return getHSAILCompilationResult(javaMethod);
    }

    public static HSAILCompilationResult getHSAILCompilationResult(ResolvedJavaMethod javaMethod) {
        HotSpotMetaAccessProvider metaAccess = backend.getProviders().getMetaAccess();
        StructuredGraph graph = new StructuredGraph(javaMethod);
        new GraphBuilderPhase.Instance(metaAccess, GraphBuilderConfiguration.getEagerDefault(), OptimisticOptimizations.ALL).apply(graph);
        return getHSAILCompilationResult(graph);
    }

    public static HSAILCompilationResult getCompiledLambda(Class consumerClass) {
        /**
         * Find the accept() method in the IntConsumer, then use Graal API to find the target lambda
         * that accept will call.
         */
        Method[] icMethods = consumerClass.getMethods();
        Method acceptMethod = null;
        for (Method m : icMethods) {
            if (m.getName().equals("accept") && acceptMethod == null) {
                acceptMethod = m;
                break;
            }
        }

        Providers providers = backend.getProviders();
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) providers.getMetaAccess();
        ResolvedJavaMethod rm = metaAccess.lookupJavaMethod(acceptMethod);
        StructuredGraph graph = new StructuredGraph(rm);
        new GraphBuilderPhase.Instance(providers.getMetaAccess(), GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL).apply(graph);
        NodeIterable<Node> nin = graph.getNodes();
        ResolvedJavaMethod lambdaMethod = null;
        for (Node n : nin) {
            if (n instanceof MethodCallTargetNode) {
                lambdaMethod = ((MethodCallTargetNode) n).targetMethod();
                Debug.log("target ... " + lambdaMethod);
                break;
            }
        }
        if (lambdaMethod == null) {
            // Did not find call in Consumer.accept.
            Debug.log("Should not Reach here, did not find call in accept()");
            return null;
        }
        // Now that we have the target lambda, compile it.
        HSAILCompilationResult hsailCompResult = HSAILCompilationResult.getHSAILCompilationResult(lambdaMethod);
        return hsailCompResult;
    }

    public static HSAILCompilationResult getHSAILCompilationResult(StructuredGraph graph) {
        Debug.dump(graph, "Graph");
        Providers providers = backend.getProviders();
        TargetDescription target = providers.getCodeCache().getTarget();
        PhaseSuite<HighTierContext> graphBuilderSuite = backend.getSuites().getDefaultGraphBuilderSuite().copy();
        graphBuilderSuite.appendPhase(new HSAILPhase());
        new HSAILPhase().apply(graph);
        CallingConvention cc = CodeUtil.getCallingConvention(providers.getCodeCache(), Type.JavaCallee, graph.method(), false);
        SuitesProvider suitesProvider = backend.getSuites();
        try {
            HSAILCompilationResult compResult = compileGraph(graph, cc, graph.method(), providers, backend, target, null, graphBuilderSuite, OptimisticOptimizations.NONE, getProfilingInfo(graph),
                            new SpeculationLog(), suitesProvider.getDefaultSuites(), true, new HSAILCompilationResult(), CompilationResultBuilderFactory.Default);
            if ((validDevice) && (compResult.getTargetCode() != null)) {
                long kernel = toGPU.generateKernel(compResult.getTargetCode(), graph.method().getName());

                if (kernel == 0) {
                    throw new GraalInternalError("Failed to compile kernel.");
                }

                ((ExternalCompilationResult) compResult).setEntryPoint(kernel);
                HotSpotResolvedJavaMethod compiledMethod = (HotSpotResolvedJavaMethod) graph.method();
                InstalledCode installedCode = ((HotSpotCodeCacheProvider) providers.getCodeCache()).addExternalMethod(compiledMethod, compResult);
                compResult.setInstalledCode(installedCode);
            }
            return compResult;
        } catch (InvalidInstalledCodeException e) {
            e.printStackTrace();
            return null;
        } catch (GraalInternalError e) {
            String partialCode = backend.getPartialCodeString();
            if (partialCode != null && !partialCode.equals("")) {
                Debug.log("-------------------\nPartial Code Generation:\n--------------------");
                Debug.log(partialCode);
                Debug.log("-------------------\nEnd of Partial Code Generation\n--------------------");
            }
            throw e;
        }
    }

    private static class HSAILPhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            for (ParameterNode param : graph.getNodes(ParameterNode.class)) {
                if (param.stamp() instanceof ObjectStamp) {
                    param.setStamp(StampFactory.declaredNonNull(((ObjectStamp) param.stamp()).type()));
                }
            }
        }
    }

    protected HSAILCompilationResult() {
    }

    public String getHSAILCode() {
        return new String(getTargetCode(), 0, getTargetCodeSize());
    }

}
