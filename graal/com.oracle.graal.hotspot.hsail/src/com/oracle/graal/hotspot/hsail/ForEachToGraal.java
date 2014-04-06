/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.gpu.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.printer.*;

/**
 * Implements compile and dispatch of Java code containing lambda constructs. Currently only used by
 * JDK interception code that offloads to the GPU.
 */
public class ForEachToGraal implements CompileAndDispatch {

    private static HSAILHotSpotBackend getHSAILBackend() {
        Backend backend = runtime().getBackend(HSAIL.class);
        return (HSAILHotSpotBackend) backend;
    }

    /**
     * Gets a compiled and installed kernel for the lambda called by the {@code accept(int value)}
     * method in a class implementing {@code java.util.function.IntConsumer}.
     * 
     * @param intConsumerClass a class implementing {@code java.util.function.IntConsumer}
     * @return a {@link HotSpotNmethod} handle to the compiled and installed kernel
     */
    private static HotSpotNmethod getCompiledLambda(Class intConsumerClass) {
        Method acceptMethod = null;
        for (Method m : intConsumerClass.getMethods()) {
            if (m.getName().equals("accept")) {
                assert acceptMethod == null : "found more than one implementation of accept(int) in " + intConsumerClass;
                acceptMethod = m;
            }
        }

        // Ensure a debug configuration for this thread is initialized
        if (DebugScope.getConfig() == null) {
            DebugEnvironment.initialize(System.out);
        }

        HSAILHotSpotBackend backend = getHSAILBackend();
        Providers providers = backend.getProviders();
        StructuredGraph graph = new StructuredGraph(((HotSpotMetaAccessProvider) providers.getMetaAccess()).lookupJavaMethod(acceptMethod));
        new GraphBuilderPhase.Instance(providers.getMetaAccess(), GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL).apply(graph);
        NodeIterable<MethodCallTargetNode> calls = graph.getNodes(MethodCallTargetNode.class);
        assert calls.count() == 1;
        ResolvedJavaMethod lambdaMethod = calls.first().targetMethod();
        Debug.log("target ... %s", lambdaMethod);

        if (lambdaMethod == null) {
            Debug.log("Did not find call in accept()");
            return null;
        }
        assert lambdaMethod.getName().startsWith("lambda$");

        ExternalCompilationResult hsailCode = backend.compileKernel(lambdaMethod, true);
        return backend.installKernel(lambdaMethod, hsailCode);
    }

    @Override
    public Object createKernel(Class<?> consumerClass) {
        try {
            return getCompiledLambda(consumerClass);
        } catch (Throwable e) {
            // If Graal compilation throws an exception, we want to revert to regular Java
            Debug.log("WARNING: Graal compilation failed");
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean dispatchKernel(Object kernel, int jobSize, Object[] args) {
        HotSpotNmethod code = (HotSpotNmethod) kernel;
        if (code != null) {
            try {
                // No return value from HSAIL kernels
                getHSAILBackend().executeKernel(code, jobSize, args);
                return true;
            } catch (InvalidInstalledCodeException iice) {
                Debug.log("WARNING: Invalid installed code at exec time: %s", iice);
                iice.printStackTrace();
                return false;
            }
        } else {
            // Should throw something sensible here
            return false;
        }
    }
}
