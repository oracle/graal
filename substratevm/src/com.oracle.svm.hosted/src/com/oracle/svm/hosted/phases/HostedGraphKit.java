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

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.util.Providers;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.svm.core.graal.nodes.DeadEndNode;
import com.oracle.svm.core.graal.replacements.SubstrateGraphKit;
import com.oracle.svm.core.util.ExceptionHelpers;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.nodes.SubstrateMethodCallTargetNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HostedGraphKit extends SubstrateGraphKit {

    public HostedGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        super(debug, method, providers, providers.getWordTypes(), providers.getGraphBuilderPlugins(), new SubstrateCompilationIdentifier());
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
            ValueNode hub = createConstant(getConstantReflection().asJavaClass(type), JavaKind.Object);
            int bci = bci();
            EnsureClassInitializedNode ensureInitializedNode = append(new EnsureClassInitializedNode(hub));
            ensureInitializedNode.setStateAfter(getFrameState().create(bci, ensureInitializedNode));

            AbstractBeginNode noExceptionEdge = add(ensureInitializedNode.createNextBegin());
            ensureInitializedNode.setNext(noExceptionEdge);
            ExceptionObjectNode exceptionEdge = createExceptionObjectNode(getFrameState(), bci);
            ensureInitializedNode.setExceptionEdge(exceptionEdge);

            lastFixedNode = exceptionEdge;
            throwInvocationTargetException(exceptionEdge);

            assert lastFixedNode == null;
            lastFixedNode = noExceptionEdge;
        }
    }

    public void throwInvocationTargetException(ValueNode exception) {
        ResolvedJavaMethod throwInvocationTargetException = findMethod(ExceptionHelpers.class, "throwInvocationTargetException", true);
        createJavaCallWithExceptionAndUnwind(InvokeKind.Static, throwInvocationTargetException, exception);
        append(new DeadEndNode());
    }

}
