/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static com.oracle.svm.hosted.SubstrateBytecodeHandlerStub.unwrap;

import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.common.meta.MethodVariant;
import com.oracle.svm.core.nodes.SubstrateIndirectCallTargetNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BytecodeHandlerMainDispatchAddressNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.phases.OutlineBytecodeHandlerPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig.ArgumentInfo;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeHandlerCallSite;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A Substrate-specific implementation of the {@link OutlineBytecodeHandlerPhase}. This phase is
 * responsible for replacing bytecode handler invocations with corresponding stub
 * calls.
 *
 * @see BytecodeHandlerInvokePlugin
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class SubstrateOutlineBytecodeHandlerPhase extends OutlineBytecodeHandlerPhase {

    /**
     * A map of registered bytecode handlers, keyed by the original method and partitioned by
     * {@link BytecodeHandlerConfig}. Each entry resolves to the corresponding stub wrapper method.
     * This map is collected in {@link BytecodeHandlerInvokePlugin} and the contents are
     * analysis-time {@link com.oracle.graal.pointsto.meta.AnalysisMethod} instances.
     */
    private final EconomicMap<BytecodeHandlerStubKey, ResolvedJavaMethod> registeredBytecodeHandlers;
    private final SubstrateBytecodeHandlerStubHelper stubHelper;

    public SubstrateOutlineBytecodeHandlerPhase(EconomicMap<BytecodeHandlerStubKey, ResolvedJavaMethod> registeredBytecodeHandlers, SubstrateBytecodeHandlerStubHelper stubHelper) {
        this.registeredBytecodeHandlers = registeredBytecodeHandlers;
        this.stubHelper = stubHelper;
    }

    private ResolvedJavaMethod lookupStubWrapper(ResolvedJavaMethod targetMethod, ResolvedJavaType interpreterHolder, BytecodeHandlerConfig handlerConfig, int templateIndex) {
        BytecodeHandlerStubKey key = BytecodeHandlerStubKey.create(unwrap(targetMethod), interpreterHolder, handlerConfig, templateIndex);
        ResolvedJavaMethod stubWrapper = registeredBytecodeHandlers.get(key);
        GraalError.guarantee(stubWrapper != null, "No stub registered for %s with config %s", targetMethod, handlerConfig);
        return stubWrapper;
    }

    @Override
    protected BytecodeHandlerCallSite getBytecodeHandlerCallSite(ResolvedJavaMethod enclosingMethod, int bci, ResolvedJavaMethod targetMethod) {
        return new BytecodeHandlerCallSite(unwrap(enclosingMethod), bci, unwrap(targetMethod), templateModeEnabled());
    }

    @Override
    protected boolean templateModeEnabled() {
        return true;
    }

    @Override
    protected Function<ResolvedJavaField, ResolvedJavaField> getFieldMap(MetaAccessProvider metaAccess) {
        HostedUniverse hostedUniverse = ((HostedMetaAccess) metaAccess).getUniverse();
        return hostedUniverse::optionalLookup;
    }

    @Override
    protected Function<ResolvedJavaType, ResolvedJavaType> getTypeMap(MetaAccessProvider metaAccess) {
        HostedUniverse hostedUniverse = ((HostedMetaAccess) metaAccess).getUniverse();
        return hostedUniverse::optionalLookup;
    }

    @Override
    protected FixedNode replaceInvoke(HighTierContext context, BytecodeHandlerCallSite callsite, Invoke invoke, ValueNode[] arguments) {
        StructuredGraph graph = invoke.asNode().graph();
        CallTargetNode oldCallTargetNode = invoke.callTarget();
        ValueNode[] originalArguments = oldCallTargetNode.arguments().toArray(ValueNode.EMPTY_ARRAY);
        ResolvedJavaMethod targetMethod = oldCallTargetNode.targetMethod();
        ResolvedJavaType interpreterHolder = unwrap(callsite.getEnclosingMethod().getDeclaringClass());
        ResolvedJavaMethod analysisStub = lookupStubWrapper(callsite.getTargetMethod(), interpreterHolder, callsite.getHandlerConfig(), 0);
        HostedMethod hostedStub = ((HostedMetaAccess) context.getMetaAccess()).getUniverse().optionalLookup(analysisStub);

        CallTargetNode newCallTargetNode;
        if (callsite.getHandlerConfig().getTemplatesLength() == 1) {
            newCallTargetNode = graph.add(new SubstrateMethodCallTargetNode(CallTargetNode.InvokeKind.Static, hostedStub, arguments,
                            StampFactory.forDeclaredType(graph.getAssumptions(), targetMethod.getSignature().getReturnType(targetMethod.getDeclaringClass()), false)));
        } else {
            ValueNode dispatchAddress = createMainDispatchAddress(context, callsite, invoke, oldCallTargetNode, interpreterHolder);
            JavaType[] signature = hostedStub.getSignature().toParameterTypes(null);
            CallingConvention.Type callType = hostedStub.getCallingConventionKind().isCustom() ? hostedStub.getCustomCallingConventionType() : hostedStub.getCallingConventionKind().toType(true);
            newCallTargetNode = graph.add(new SubstrateIndirectCallTargetNode(dispatchAddress, arguments,
                            StampFactory.forDeclaredType(graph.getAssumptions(), targetMethod.getSignature().getReturnType(targetMethod.getDeclaringClass()), false),
                            signature, hostedStub, callType, CallTargetNode.InvokeKind.Static));
        }
        invoke.asNode().replaceAllInputs(oldCallTargetNode, newCallTargetNode);
        if (!callsite.getHandlerConfig().getTemplateVariableArguments().isEmpty()) {
            FixedNode normalSuccessor = invoke instanceof InvokeNode invokeNode ? invokeNode.next() : ((InvokeWithExceptionNode) invoke).next().next();
            FrameState stateAfter = invoke.stateAfter();
            GraalError.guarantee(stateAfter != null, "Missing state after handler invoke %s", invoke);
            SubstrateBytecodeHandlerUnwindPath.readTemplateStateOnCaller(context.getMetaAccess(), callsite.getHandlerConfig(), originalArguments,
                            normalSuccessor, stateAfter.duplicate(), getFieldMap(context.getMetaAccess()));
        }
        if (invoke instanceof InvokeWithExceptionNode invokeWithExceptionNode) {
            SubstrateBytecodeHandlerUnwindPath.readOnCaller(context.getMetaAccess(), callsite.getHandlerConfig(), invokeWithExceptionNode, arguments,
                            originalArguments, getFieldMap(context.getMetaAccess()));
        }
        return (FixedNode) invoke;
    }

    private ValueNode createMainDispatchAddress(HighTierContext context, BytecodeHandlerCallSite callsite, Invoke invoke, CallTargetNode oldCallTargetNode,
                    ResolvedJavaType interpreterHolder) {
        StructuredGraph graph = invoke.asNode().graph();
        BytecodeHandlerConfig handlerConfig = callsite.getHandlerConfig();
        ValueNode[] templateValues = loadTemplateValues(context.getMetaAccess(), handlerConfig, oldCallTargetNode, (FixedNode) invoke.asNode());
        int[] templateVariants = new int[templateValues.length];
        for (int i = 0; i < templateValues.length; i++) {
            templateVariants[i] = handlerConfig.getTemplateVariableArguments().get(i).templateVariants();
        }
        Object dispatchTargets = stubHelper.getMainDispatchHandlers(callsite.getTargetMethod(), interpreterHolder, handlerConfig);
        BytecodeHandlerMainDispatchAddressNode dispatchAddress = graph.add(new BytecodeHandlerMainDispatchAddressNode(templateValues, templateVariants, dispatchTargets));
        graph.addBeforeFixed((FixedNode) invoke.asNode(), dispatchAddress);
        return dispatchAddress;
    }

    private ValueNode[] loadTemplateValues(MetaAccessProvider metaAccess, BytecodeHandlerConfig handlerConfig, CallTargetNode oldCallTargetNode, FixedNode insertBefore) {
        StructuredGraph graph = insertBefore.graph();
        Function<ResolvedJavaField, ResolvedJavaField> fieldMap = getFieldMap(metaAccess);
        ValueNode[] templateValues = new ValueNode[handlerConfig.getTemplateVariableArguments().size()];
        for (int i = 0; i < templateValues.length; i++) {
            ArgumentInfo templateVariable = handlerConfig.getTemplateVariableArguments().get(i);
            ValueNode owner = oldCallTargetNode.arguments().get(templateVariable.originalIndex());
            LoadFieldNode load = LoadFieldNode.create(graph.getAssumptions(), owner, fieldMap.apply(templateVariable.field()));
            graph.addBeforeFixed(insertBefore, graph.add(load));
            templateValues[i] = load;
        }
        return templateValues;
    }

    @Override
    protected boolean applicableTo(ResolvedJavaMethod enclosingMethod) {
        return !(enclosingMethod instanceof MethodVariant mm) || mm.isOriginalMethod();
    }

    @Override
    protected void afterProcessGraph(HighTierContext context, StructuredGraph graph) {
        SubstrateBytecodeHandlerUnwindPath.processPendingExceptionStateValues(context.getMetaAccess(), graph);
    }
}
