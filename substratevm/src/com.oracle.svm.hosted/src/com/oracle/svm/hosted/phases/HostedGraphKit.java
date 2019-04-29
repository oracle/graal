/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.util.Providers;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.replacements.SubstrateGraphKit;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.nodes.SubstrateMethodCallTargetNode;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HostedGraphKit extends SubstrateGraphKit {

    private static final Method dynamicHubEnsureInitialized;
    static {
        Method ensureInitialized = null;
        try {
            ensureInitialized = DynamicHub.class.getDeclaredMethod("ensureInitialized");
        } catch (NoSuchMethodException e) {
            VMError.shouldNotReachHere(e);
        }
        dynamicHubEnsureInitialized = ensureInitialized;
    }

    public HostedGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        super(debug, method, providers, providers.getWordTypes(), providers.getGraphBuilderPlugins(), null);
    }

    @Override
    protected MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, int bci) {
        ResolvedJavaMethod method = graph.method();
        if (method instanceof HostedMethod) {
            return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp, ((HostedMethod) method).getProfilingInfo(), bci);
        } else {
            return super.createMethodCallTarget(invokeKind, targetMethod, args, returnStamp, bci);
        }
    }

    @Override
    protected Instance createGraphBuilderInstance(Providers theProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext) {

        ResolvedJavaMethod method = graph.method();
        if (method instanceof AnalysisMethod) {
            return new AnalysisGraphBuilderPhase(theProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
        } else if (method instanceof HostedMethod) {
            return new HostedGraphBuilderPhase(theProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
        } else {
            throw VMError.shouldNotReachHere();
        }
    }

    public void emitEnsureInitializedCall(ResolvedJavaType type) {
        if (SubstrateClassInitializationPlugin.needsRuntimeInitialization(graph.method().getDeclaringClass(), type)) {
            ResolvedJavaMethod ensureInitialized = providers.getMetaAccess().lookupJavaMethod(dynamicHubEnsureInitialized);

            Constant dynamicHub = getConstantReflection().asJavaClass(type);
            ValueNode dynamicHubNode = createConstant(dynamicHub, JavaKind.Object);

            ValueNode[] args = new ValueNode[]{dynamicHubNode};

            createJavaCallWithException(InvokeKind.Virtual, ensureInitialized, args);
            noExceptionPart();

            exceptionPart();
            throwInvocationTargetException();

            endInvokeWithException();

            mergeUnwinds();
        }
    }

    public void throwInvocationTargetException() {
        ValueNode exception = exceptionObject();

        ResolvedJavaType exceptionType = getMetaAccess().lookupJavaType(InvocationTargetException.class);
        ValueNode ite = append(new NewInstanceNode(exceptionType, true));

        ResolvedJavaMethod cons = null;
        for (ResolvedJavaMethod c : exceptionType.getDeclaredConstructors()) {
            if (c.getSignature().getParameterCount(false) == 1) {
                cons = c;
            }
        }

        createJavaCallWithExceptionAndUnwind(InvokeKind.Special, cons, ite, exception);

        append(new UnwindNode(ite));
        mergeUnwinds();
    }

}
