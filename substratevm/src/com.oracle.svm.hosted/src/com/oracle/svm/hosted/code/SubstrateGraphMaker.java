/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.BitSet;

import com.oracle.svm.hosted.phases.SubstrateGraphBuilderPhase;

import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.java.GraphBuilderPhase.Instance;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.ReplacementsImpl;
import jdk.graal.compiler.replacements.ReplacementsImpl.GraphMaker;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateGraphMaker extends GraphMaker {

    public SubstrateGraphMaker(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
        super(replacements, substitute, substitutedMethod);
    }

    @Override
    protected Instance createGraphBuilder(Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
        return new SubstrateGraphBuilderPhase(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
    }

    @Override
    protected StructuredGraph buildInitialGraph(DebugContext debug, BytecodeProvider bytecodeProvider, ResolvedJavaMethod methodToParse, Object[] args, BitSet nonNullParameters,
                    boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition, IntrinsicContext.CompilationContext context) {
        StructuredGraph graph = super.buildInitialGraph(debug, bytecodeProvider, methodToParse, args, nonNullParameters, trackNodeSourcePosition, replaceePosition, context);
        graph.getGraphState().configureExplicitExceptionsNoDeopt();
        return graph;
    }
}
