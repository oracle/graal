/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Function;

import com.oracle.svm.core.graal.nodes.InlinedInvokeArgumentsNode;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;

import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.truffle.phases.TruffleEarlyInliningPhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * SVM specific behavior for Truffle early inlining.
 */
public final class SubstrateEarlyInliningPhase extends TruffleEarlyInliningPhase {
    private final Function<ResolvedJavaMethod, ResolvedJavaMethod> targetResolver;

    public SubstrateEarlyInliningPhase(OptionValues options, CanonicalizerPhase canonicalizer, Providers providers,
                    Function<ResolvedJavaMethod, StructuredGraph> graphBuilder,
                    Function<ResolvedJavaMethod, ResolvedJavaMethod> targetResolver,
                    ResolvedJavaType earlyInline) {
        super(options, canonicalizer, providers, graphBuilder, earlyInline);
        this.targetResolver = targetResolver;
    }

    @Override
    protected ResolvedJavaMethod getInvokeTarget(ResolvedJavaMethod targetMethod) {
        return targetResolver.apply(targetMethod);
    }

    @Override
    protected void processInvokeArgs(Invoke invoke, ResolvedJavaMethod targetMethod) {
        /*
         * This logic is aligned with
         * RuntimeCompilationInlineBeforeAnalysisPolicy#processInvokeArgs.
         */
        StructuredGraph graph = invoke.asFixedNode().graph();
        assert SubstrateCompilationDirectives.isRuntimeCompiledMethod(targetMethod) : targetMethod;
        ValueNode[] arguments = invoke.callTarget().arguments().toArray(ValueNode[]::new);
        InlinedInvokeArgumentsNode newNode = graph.add(new InlinedInvokeArgumentsNode(targetMethod, arguments));
        graph.addAfterFixed((FixedWithNextNode) invoke.asFixedNode().predecessor(), newNode);
    }

}
