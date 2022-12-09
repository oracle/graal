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

import org.graalvm.compiler.core.common.BootstrapMethodIntrospection;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.infrastructure.AnalysisConstantPool;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.util.ModuleSupport;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AnalysisGraphBuilderPhase extends SharedGraphBuilderPhase {

    public AnalysisGraphBuilderPhase(CoreProviders providers,
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
                ((AnalysisMethod) targetMethod).registerAsIntrinsicMethod(nonNullReason(graph.currentNodeSourcePosition()));
            }
            return result;
        }

        private static Object nonNullReason(Object reason) {
            return reason == null ? "Unknown invocation location." : reason;
        }

        @Override
        protected boolean applyInvocationPlugin(InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType, InvocationPlugin plugin) {
            Class<? extends InvocationPlugin> accessingClass = plugin.getClass();
            /*
             * The annotation-processor creates InvocationPlugins in classes in modules that e.g.
             * use the @Fold annotation. This way InvocationPlugins can be in various classes in
             * various modules. For these InvocationPlugins to do their work they need access to
             * bits of graal. Thus the modules that contain such plugins need to be allowed such
             * access.
             */
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, accessingClass, false, "jdk.internal.vm.ci", "jdk.vm.ci.meta");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, accessingClass, false, "jdk.internal.vm.compiler", "org.graalvm.compiler.nodes");
            return super.applyInvocationPlugin(invokeKind, args, targetMethod, resultType, plugin);
        }

        private final boolean parseOnce = SubstrateOptions.parseOnce();

        @Override
        protected BytecodeParser.ExceptionEdgeAction getActionForInvokeExceptionEdge(InlineInfo lastInlineInfo) {
            if (!parseOnce && !insideTryBlock()) {
                /*
                 * The static analysis does not track the flow of exceptions across method
                 * boundaries. Therefore, it is not necessary to have exception edges that go
                 * directly to an UnwindNode because there is no exception handler in between.
                 */
                return ExceptionEdgeAction.OMIT;
            }
            return super.getActionForInvokeExceptionEdge(lastInlineInfo);
        }

        private boolean tryNodePluginForDynamicInvocation(BootstrapMethodIntrospection bootstrap) {
            for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
                var result = plugin.convertInvokeDynamic(this, bootstrap);
                if (result != null) {
                    appendInvoke(InvokeKind.Static, result.getLeft(), result.getRight());
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void genInvokeDynamic(int cpi, int opcode) {
            if (parseOnce) {
                BootstrapMethodIntrospection bootstrap = ((AnalysisConstantPool) constantPool).lookupBootstrapMethodIntrospection(cpi, opcode);
                if (bootstrap != null && tryNodePluginForDynamicInvocation(bootstrap)) {
                    return;
                }
            }
            super.genInvokeDynamic(cpi, opcode);
        }
    }
}
