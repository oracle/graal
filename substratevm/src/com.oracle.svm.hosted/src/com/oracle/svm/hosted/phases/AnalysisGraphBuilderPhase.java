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

import com.oracle.graal.pointsto.infrastructure.AnalysisConstantPool;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.util.ModuleSupport;

import jdk.graal.compiler.core.common.BootstrapMethodIntrospection;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AnalysisGraphBuilderPhase extends SharedGraphBuilderPhase {

    protected final SVMHost hostVM;

    public AnalysisGraphBuilderPhase(CoreProviders providers,
                    GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, WordTypes wordTypes, SVMHost hostVM) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
        this.hostVM = hostVM;
    }

    @Override
    protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        return new AnalysisBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext, hostVM, true);
    }

    public static class AnalysisBytecodeParser extends SharedBytecodeParser {

        private final SVMHost hostVM;

        protected AnalysisBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, SVMHost hostVM, boolean explicitExceptionEdges) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, explicitExceptionEdges);
            this.hostVM = hostVM;
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
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, accessingClass, false, "jdk.graal.compiler", "jdk.graal.compiler.nodes");
            return super.applyInvocationPlugin(invokeKind, args, targetMethod, resultType, plugin);
        }

        private boolean tryNodePluginForDynamicInvocation(BootstrapMethodIntrospection bootstrap) {
            for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
                var result = plugin.convertInvokeDynamic(this, bootstrap);
                if (result != null) {
                    appendInvoke(InvokeKind.Static, result.getLeft(), result.getRight(), null);
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void genInvokeDynamic(int cpi, int opcode) {
            BootstrapMethodIntrospection bootstrap = ((AnalysisConstantPool) constantPool).lookupBootstrapMethodIntrospection(cpi, opcode);
            if (bootstrap != null && tryNodePluginForDynamicInvocation(bootstrap)) {
                return;
            }
            super.genInvokeDynamic(cpi, opcode);
        }

        @Override
        protected void genStoreField(ValueNode receiver, ResolvedJavaField field, ValueNode value) {
            hostVM.recordFieldStore(field, method);
            super.genStoreField(receiver, field, value);
        }

        @Override
        protected FrameStateBuilder createFrameStateForExceptionHandling(int bci) {
            var dispatchState = super.createFrameStateForExceptionHandling(bci);
            /*
             * It is beneficial to eagerly clear all non-live locals on the exception object before
             * entering the dispatch target. This helps us prune unneeded values from the graph,
             * which can positively impact our analysis. Since deoptimization is not possible, then
             * there is no risk in clearing the unneeded locals.
             */
            AnalysisMethod aMethod = (AnalysisMethod) method;
            if (aMethod.isOriginalMethod() && !SubstrateCompilationDirectives.singleton().isRegisteredForDeoptTesting(aMethod)) {
                BciBlockMapping.BciBlock dispatchBlock = getDispatchBlock(bci);
                clearNonLiveLocals(dispatchState, dispatchBlock, true);
            }
            return dispatchState;
        }
    }
}
