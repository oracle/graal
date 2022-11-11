/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.results.StaticAnalysisResults;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.phases.SubstrateGraphBuilderPhase.SubstrateBytecodeParser;

import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HostedGraphBuilderPhase extends SubstrateGraphBuilderPhase {

    public HostedGraphBuilderPhase(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext,
                    WordTypes wordTypes) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
    }

    @Override
    protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        return new HostedBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
    }
}

class HostedBytecodeParser extends SubstrateBytecodeParser {

    HostedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, true);
    }

    @Override
    public HostedMethod getMethod() {
        return (HostedMethod) super.getMethod();
    }

    @Override
    protected boolean stampFromValueForForcedPhis() {
        return true;
    }

    @Override
    public boolean allowDeoptInPlugins() {
        return false;
    }

    @Override
    protected boolean asyncExceptionLiveness() {
        /*
         * Only methods which can deoptimize need to consider live locals from asynchronous
         * exception handlers.
         */
        return isDeoptimizationEnabled() && getMethod().canDeoptimize();
    }

    @Override
    protected void build(FixedWithNextNode startInstruction, FrameStateBuilder startFrameState) {
        super.build(startInstruction, startFrameState);

        /* We never have floating guards in AOT compiled code. */
        getGraph().getGraphState().configureExplicitExceptionsNoDeopt();

        assert !getMethod().isEntryPoint() : "Cannot directly use as entry point, create a call stub ";
    }

    @Override
    public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, JavaTypeProfile profile) {
        StaticAnalysisResults staticAnalysisResults = getMethod().getProfilingInfo();
        return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp,
                        staticAnalysisResults.getTypeProfile(bci()), staticAnalysisResults.getMethodProfile(bci()), staticAnalysisResults.getStaticTypeProfile(bci()));
    }

}
