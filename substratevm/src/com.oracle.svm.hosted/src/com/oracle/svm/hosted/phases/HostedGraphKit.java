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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.results.StaticAnalysisResults;
import com.oracle.svm.core.c.BoxedRelocatedPointer;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.svm.core.graal.replacements.SubstrateGraphKit;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HostedGraphKit extends SubstrateGraphKit {

    public HostedGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method) {
        this(debug, providers, method, false);
    }

    public HostedGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method, boolean forceTrackNodeSourcePosition) {
        super(debug, method, providers, providers.getWordTypes(), providers.getGraphBuilderPlugins(), new SubstrateCompilationIdentifier(), forceTrackNodeSourcePosition);
        graph.getGraphState().configureExplicitExceptionsNoDeopt();
    }

    @Override
    protected MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, int bci) {
        ResolvedJavaMethod method = graph.method();
        if (method instanceof HostedMethod) {
            StaticAnalysisResults profilingInfo = ((HostedMethod) method).getProfilingInfo();
            return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp,
                            profilingInfo.getTypeProfile(bci), profilingInfo.getMethodProfile(bci), profilingInfo.getStaticTypeProfile(bci));
        } else {
            return super.createMethodCallTarget(invokeKind, targetMethod, args, returnStamp, bci);
        }
    }

    @Override
    protected Instance createGraphBuilderInstance(GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext) {

        ResolvedJavaMethod method = graph.method();
        if (method instanceof AnalysisMethod) {
            return new AnalysisGraphBuilderPhase(getProviders(), graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
        } else if (method instanceof HostedMethod) {
            return new HostedGraphBuilderPhase(getProviders(), graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
        } else {
            throw VMError.shouldNotReachHere();
        }
    }

    public void emitEnsureInitializedCall(ResolvedJavaType type) {
        if (EnsureClassInitializedNode.needsRuntimeInitialization(graph.method().getDeclaringClass(), type)) {
            ValueNode hub = createConstant(getConstantReflection().asJavaClass(type), JavaKind.Object);
            appendWithUnwind(new EnsureClassInitializedNode(hub));
        }
    }

    /**
     * Appends the provided node to the control flow graph. The exception edge is connected to an
     * {@link UnwindNode}, i.e., the exception is not handled in this method.
     */
    public <T extends WithExceptionNode> T appendWithUnwind(T withExceptionNode) {
        return appendWithUnwind(withExceptionNode, bci());
    }

    public LoadFieldNode createLoadFieldNode(ConstantNode receiver, Class<BoxedRelocatedPointer> clazz, String fieldName) {
        try {
            ResolvedJavaField field = getMetaAccess().lookupJavaField(clazz.getDeclaredField(fieldName));
            return LoadFieldNode.createOverrideStamp(StampPair.createSingle(wordStamp((ResolvedJavaType) field.getType())), receiver, field);
        } catch (NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    public GuardingNode createCheckThrowingBytecodeException(LogicNode condition, boolean failOnTrue, BytecodeExceptionNode.BytecodeExceptionKind exceptionKind, ValueNode... arguments) {
        BranchProbabilityData trueProbability = failOnTrue ? BranchProbabilityNode.SLOW_PATH_PROFILE : BranchProbabilityNode.FAST_PATH_PROFILE;
        IfNode ifNode = startIf(condition, trueProbability);
        if (failOnTrue) {
            thenPart();
        } else {
            elsePart();
        }
        BytecodeExceptionNode exception = createBytecodeExceptionObjectNode(exceptionKind, true, arguments);
        append(new UnwindNode(exception));
        AbstractMergeNode merge = endIf();
        assert merge == null;
        return failOnTrue ? ifNode.falseSuccessor() : ifNode.trueSuccessor();
    }

    public BytecodeExceptionNode createBytecodeExceptionObjectNode(BytecodeExceptionNode.BytecodeExceptionKind exceptionKind, boolean rethrow, ValueNode... arguments) {
        BytecodeExceptionNode exception = append(new BytecodeExceptionNode(getMetaAccess(), exceptionKind, arguments));
        setStateAfterException(getFrameState(), bci(), exception, rethrow);
        return exception;
    }

    public ValueNode maybeCreateExplicitNullCheck(ValueNode object) {
        assert object.stamp(NodeView.DEFAULT).isPointerStamp();
        if (StampTool.isPointerNonNull(object)) {
            return object;
        }
        createCheckThrowingBytecodeException(IsNullNode.create(object), true, BytecodeExceptionNode.BytecodeExceptionKind.NULL_POINTER);
        return append(PiNode.create(object, StampFactory.objectNonNull()));
    }
}
