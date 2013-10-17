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

import java.lang.reflect.*;

import com.amd.okra.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;

/**
 * Implements compile and dispatch of Java code containing lambda constructs. Currently only used by
 * JDK interception code that offloads to the GPU.
 */
public class ForEachToGraal implements CompileAndDispatch {

    private static CompilationResult getCompiledLambda(Class consumerClass) {
        /**
         * Find the accept() method in the IntConsumer, then use Graal API to find the target lambda
         * that accept will call.
         */
        Method[] icMethods = consumerClass.getMethods();
        Method acceptMethod = null;
        for (Method m : icMethods) {
            if (m.getName().equals("accept") && acceptMethod == null) {
                acceptMethod = m;
            }
        }
        HotSpotProviders providers = HSAILCompilationResult.backend.getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(acceptMethod);
        StructuredGraph graph = new StructuredGraph(method);
        ForeignCallsProvider foreignCalls = providers.getForeignCalls();
        new GraphBuilderPhase(metaAccess, foreignCalls, GraphBuilderConfiguration.getEagerDefault(), OptimisticOptimizations.ALL).apply(graph);
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
        if (hsailCompResult != null) {
            hsailCompResult.dumpCompilationResult();
        }
        return hsailCompResult.getCompilationResult();
    }

    // Implementations of the CompileAndDispatch interface.
    @Override
    public Object createKernel(Class<?> consumerClass) {
        try {
            CompilationResult result = getCompiledLambda(consumerClass);
            if (result != null) {
                String code = new String(new String(result.getTargetCode(), 0, result.getTargetCodeSize()));
                OkraContext okraContext = new OkraContext();
                OkraKernel okraKernel = new OkraKernel(okraContext, code, "&run");
                if (okraKernel.isValid()) {
                    return okraKernel;
                }
            }
        } catch (Throwable e) {
            // Note: Graal throws Errors. We want to revert to regular Java in these cases.
            Debug.log("WARNING:Graal compilation failed.");
            e.printStackTrace();
            return null;
        }
        // If we got this far, return null.
        return null;
    }

    @Override
    public boolean dispatchKernel(Object kernel, int jobSize, Object[] args) {
        if (!(kernel instanceof OkraKernel)) {
            Debug.log("unknown kernel for dispatchKernel");
            return false;
        }
        OkraKernel okraKernel = (OkraKernel) kernel;
        okraKernel.setLaunchAttributes(jobSize);
        int status = okraKernel.dispatchWithArgs(args);
        return (status == 0);
    }
}
