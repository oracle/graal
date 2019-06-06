/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AnalysisGraphBuilderPhase extends SharedGraphBuilderPhase {

    public AnalysisGraphBuilderPhase(Providers providers,
                    GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, WordTypes wordTypes) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
    }

    @Override
    protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        return new AnalysisBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
    }

    public static class AnalysisBytecodeParser extends SharedBytecodeParser {
        protected AnalysisBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, true);
        }

        @Override
        protected boolean tryInvocationPlugin(InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
            boolean result = super.tryInvocationPlugin(invokeKind, args, targetMethod, resultType);
            if (result) {
                ((AnalysisMethod) targetMethod).registerAsIntrinsicMethod();
            }
            return result;
        }

        @Override
        protected BytecodeParser.ExceptionEdgeAction getActionForInvokeExceptionEdge(InlineInfo lastInlineInfo) {
            if (currentBlock.exceptionDispatchBlock() == null) {
                /*
                 * The static analysis does not track the flow of exceptions across method
                 * boundaries. Therefore, it is not necessary to have exception edges that go
                 * directly to an UnwindNode because there is no exception handler in between.
                 */
                return ExceptionEdgeAction.OMIT;
            }
            return super.getActionForInvokeExceptionEdge(lastInlineInfo);
        }

        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            return plugin.getSource().equals(Fold.class) || plugin.getSource().equals(Node.NodeIntrinsic.class);
        }
    }
}
