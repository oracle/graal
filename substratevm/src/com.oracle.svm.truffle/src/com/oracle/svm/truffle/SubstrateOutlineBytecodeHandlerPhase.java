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
package com.oracle.svm.truffle;

import static com.oracle.svm.truffle.SubstrateTruffleBytecodeHandlerStub.asTruffleBytecodeHandlerTypes;
import static com.oracle.svm.truffle.SubstrateTruffleBytecodeHandlerStub.unwrap;

import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite.TruffleBytecodeHandlerTypes;
import jdk.graal.compiler.truffle.host.OutlineBytecodeHandlerPhase;
import jdk.graal.compiler.truffle.host.TruffleKnownHostTypes;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A Substrate-specific implementation of the {@link OutlineBytecodeHandlerPhase}. This phase is
 * responsible for replacing Truffle interpreter bytecode handler invocations to corresponding stub
 * calls.
 *
 * @see TruffleBytecodeHandlerInvokePlugin
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class SubstrateOutlineBytecodeHandlerPhase extends OutlineBytecodeHandlerPhase {

    /**
     * A map of registered bytecode handlers, where the key is the original method and the value is
     * the stub method. This map is collected in {@link TruffleBytecodeHandlerInvokePlugin} and both
     * the keys and values are of {@link com.oracle.graal.pointsto.meta.AnalysisMethod}.
     */
    private final EconomicMap<ResolvedJavaMethod, ResolvedJavaMethod> registeredBytecodeHandlers;

    public SubstrateOutlineBytecodeHandlerPhase(EconomicMap<ResolvedJavaMethod, ResolvedJavaMethod> registeredBytecodeHandlers) {
        this.registeredBytecodeHandlers = registeredBytecodeHandlers;
    }

    /**
     * {@link TruffleKnownHostTypes} holds the analysis types and should be converted to their
     * wrapped types.
     */
    @Override
    protected TruffleBytecodeHandlerTypes getTruffleBytecodeHandlerTypes(TruffleKnownHostTypes truffleKnownHostTypes) {
        return asTruffleBytecodeHandlerTypes(truffleKnownHostTypes);
    }

    /**
     * Returns the stub for the given target method.
     *
     * @param targetMethod a {@link HostedMethod}
     */
    private SubstrateTruffleBytecodeHandlerStub getStub(ResolvedJavaMethod targetMethod) {
        return (SubstrateTruffleBytecodeHandlerStub) unwrap(registeredBytecodeHandlers.get(unwrap(targetMethod)));
    }

    @Override
    protected TruffleBytecodeHandlerCallsite getTruffleBytecodeHandlerCallsite(ResolvedJavaMethod enclosingMethod, int bci, ResolvedJavaMethod targetMethod, TruffleBytecodeHandlerTypes truffleTypes) {
        return getStub(targetMethod).getCallsite();
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
    protected FixedNode replaceInvoke(HighTierContext context, TruffleBytecodeHandlerCallsite callsite, Invoke invoke, ValueNode[] arguments) {
        StructuredGraph graph = invoke.asNode().graph();
        CallTargetNode oldCallTargetNode = invoke.callTarget();
        ResolvedJavaMethod targetMethod = oldCallTargetNode.targetMethod();
        ResolvedJavaMethod analysisStub = registeredBytecodeHandlers.get(unwrap(targetMethod));
        HostedMethod hostedStub = ((HostedMetaAccess) context.getMetaAccess()).getUniverse().optionalLookup(analysisStub);

        SubstrateMethodCallTargetNode newCallTargetNode = graph.add(new SubstrateMethodCallTargetNode(CallTargetNode.InvokeKind.Static, hostedStub, arguments,
                        StampFactory.forDeclaredType(graph.getAssumptions(), targetMethod.getSignature().getReturnType(targetMethod.getDeclaringClass()), false)));
        invoke.asNode().replaceAllInputs(oldCallTargetNode, newCallTargetNode);
        return (FixedNode) invoke;
    }

    @Override
    protected boolean applicableTo(ResolvedJavaMethod enclosingMethod) {
        return !(enclosingMethod instanceof MultiMethod mm) || mm.isOriginalMethod();
    }
}
