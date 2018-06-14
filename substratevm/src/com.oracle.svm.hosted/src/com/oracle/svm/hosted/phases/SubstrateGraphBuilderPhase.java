/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Predicate;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.KillingBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.nodes.SubstrateNewArrayNode;
import com.oracle.svm.core.graal.nodes.SubstrateNewInstanceNode;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateGraphBuilderPhase extends SharedGraphBuilderPhase {

    private final Predicate<ResolvedJavaMethod> deoptimizeOnExceptionPredicate;

    public SubstrateGraphBuilderPhase(MetaAccessProvider metaAccess, StampProvider stampProvider, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                    GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, WordTypes wordTypes,
                    Predicate<ResolvedJavaMethod> deoptimizeOnExceptionPredicate) {
        super(metaAccess, stampProvider, constantReflection, constantFieldProvider, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
        this.deoptimizeOnExceptionPredicate = deoptimizeOnExceptionPredicate != null ? deoptimizeOnExceptionPredicate : (method -> false);
    }

    @Override
    protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        return new SubstrateBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext, false);
    }

    public static class SubstrateBytecodeParser extends SharedBytecodeParser {
        public SubstrateBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, boolean explicitExceptionEdges) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, explicitExceptionEdges);
        }

        @Override
        protected SubstrateGraphBuilderPhase getGraphBuilderInstance() {
            return (SubstrateGraphBuilderPhase) super.getGraphBuilderInstance();
        }

        @Override
        protected boolean disableLoopSafepoint() {
            return super.disableLoopSafepoint() || method.getAnnotation(Uninterruptible.class) != null;
        }

        @Override
        protected NewInstanceNode createNewInstance(ResolvedJavaType type, boolean fillContents) {
            return new SubstrateNewInstanceNode(type, fillContents, null);
        }

        @Override
        protected NewArrayNode createNewArray(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
            return new SubstrateNewArrayNode(elementType, length, fillContents, null);
        }

        /**
         * We do not have access to the inovked method i {@link #createHandleExceptionTarget}.
         * Therefore, we need to make the decision whether to deoptimize in
         * {@link #createInvokeWithException} and propagate the result via this field.
         */
        private boolean curDeoptimizeOnException;

        @Override
        protected void createHandleExceptionTarget(FixedWithNextNode finishedDispatch, int bci, FrameStateBuilder dispatchState) {
            if (curDeoptimizeOnException) {
                DeoptimizeNode deoptimize = graph.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.NotCompiledExceptionHandler));
                VMError.guarantee(finishedDispatch.next() == null);
                finishedDispatch.setNext(deoptimize);

            } else {
                super.createHandleExceptionTarget(finishedDispatch, bci, dispatchState);
            }
        }

        @Override
        protected InvokeWithExceptionNode createInvokeWithException(int invokeBci, CallTargetNode callTarget, JavaKind resultType, ExceptionEdgeAction exceptionEdgeAction) {
            try {
                assert curDeoptimizeOnException == false;
                curDeoptimizeOnException = getGraphBuilderInstance().deoptimizeOnExceptionPredicate.test(callTarget.targetMethod());
                return super.createInvokeWithException(invokeBci, callTarget, resultType, exceptionEdgeAction);
            } finally {
                curDeoptimizeOnException = false;
            }
        }

        /**
         * {@link Fold} and {@link NodeIntrinsic} can be deferred during parsing/decoding. Only by
         * the end of {@linkplain SnippetTemplate#instantiate Snippet instantiation} do they need to
         * have been processed.
         *
         * This is how SVM handles snippets. They are parsed with plugins disabled and then encoded
         * and stored in the image. When the snippet is needed at runtime the graph is decoded and
         * the plugins are run during the decoding process. If they aren't handled at this point
         * then they will never be handled.
         */
        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            return plugin.getSource().equals(Fold.class) || plugin.getSource().equals(Node.NodeIntrinsic.class);
        }

        public InvokeWithExceptionNode handleInvokeWithException(CallTargetNode callTarget, JavaKind resultType) {
            InvokeWithExceptionNode invoke = createInvokeWithException(bci(), callTarget, resultType, ExceptionEdgeAction.INCLUDE_AND_HANDLE);
            AbstractBeginNode beginNode = graph.add(KillingBeginNode.create(LocationIdentity.any()));
            invoke.setNext(beginNode);
            lastInstr = beginNode;
            return invoke;
        }

    }
}
