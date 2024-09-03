/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.stackvalue;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.JavaThreads;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.AbstractStateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;

final class StackValueSnippets extends SubstrateTemplates implements Snippets {
    private static final String EXCEPTION_MESSAGE = "StackValue must not be used in a virtual thread unless the method is annotated @" + Uninterruptible.class.getSimpleName() + '.';
    private static final IllegalThreadStateException CACHED_EXCEPTION = new IllegalThreadStateException(EXCEPTION_MESSAGE + ' ' + ImplicitExceptions.NO_STACK_MSG);

    static final SnippetRuntime.SubstrateForeignCallDescriptor THROW_CACHED_EXCEPTION = SnippetRuntime.findForeignCall(StackValueSnippets.class, "throwCachedException", NO_SIDE_EFFECT);
    static final SnippetRuntime.SubstrateForeignCallDescriptor THROW_NEW_EXCEPTION = SnippetRuntime.findForeignCall(StackValueSnippets.class, "throwNewException", NO_SIDE_EFFECT);
    static final SnippetRuntime.SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SnippetRuntime.SubstrateForeignCallDescriptor[]{THROW_CACHED_EXCEPTION, THROW_NEW_EXCEPTION};

    @Snippet
    private static WordBase stackValueSnippet(@ConstantParameter int sizeInBytes, @ConstantParameter int alignmentInBytes,
                    @ConstantParameter StackValueNode.StackSlotIdentity slotIdentifier, @ConstantParameter boolean disallowVirtualThread,
                    @ConstantParameter boolean mustNotAllocate) {
        if (disallowVirtualThread) {
            if (probability(EXTREMELY_SLOW_PATH_PROBABILITY, JavaThreads.isCurrentThreadVirtual())) {
                if (mustNotAllocate) {
                    callSlowPath(THROW_CACHED_EXCEPTION);
                } else {
                    callSlowPath(THROW_NEW_EXCEPTION);
                }
            }
        }
        return LoweredStackValueNode.loweredStackValue(sizeInBytes, alignmentInBytes, slotIdentifier);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPath(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwCachedException() {
        throw CACHED_EXCEPTION;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void throwNewException() {
        RuntimeException error;
        if (Heap.getHeap().isAllocationDisallowed()) {
            error = CACHED_EXCEPTION;
        } else {
            error = new IllegalThreadStateException(EXCEPTION_MESSAGE);
        }
        throw error;
    }

    private final SnippetTemplate.SnippetInfo stackValueSnippet;

    /*
     * Boilerplate code to register and perform the lowering.
     */

    StackValueSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);
        this.stackValueSnippet = snippet(providers, StackValueSnippets.class, "stackValueSnippet");
        lowerings.put(StackValueNode.class, new StackValueLowering());
        lowerings.put(LateStackValueNode.class, new LateStackValueLowering());
    }

    private void lower(LoweringTool tool, AbstractStateSplit node, int sizeInBytes, int alignmentInBytes, StackValueNode.StackSlotIdentity slotIdentity, boolean checkVirtualThread) {
        GraalError.guarantee(tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER, "Must lower before mid-tier StackValueRecursionDepthPhase");

        StructuredGraph graph = node.graph();
        boolean mustNotAllocate = ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(graph.method());

        SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(stackValueSnippet, graph.getGuardsStage(), tool.getLoweringStage());
        args.addConst("sizeInBytes", sizeInBytes);
        args.addConst("alignmentInBytes", alignmentInBytes);
        args.addConst("slotIdentifier", slotIdentity);
        args.addConst("disallowVirtualThread", checkVirtualThread);
        args.addConst("mustNotAllocate", mustNotAllocate);
        template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
    }

    final class StackValueLowering implements NodeLoweringProvider<StackValueNode> {
        @Override
        public void lower(StackValueNode node, LoweringTool tool) {
            StackValueSnippets.this.lower(tool, node, node.sizeInBytes, node.alignmentInBytes, node.slotIdentity, node.checkVirtualThread);
        }
    }

    final class LateStackValueLowering implements NodeLoweringProvider<LateStackValueNode> {
        @Override
        public void lower(LateStackValueNode node, LoweringTool tool) {
            ValueNode sizeNode = node.sizeInBytes;
            if (!sizeNode.isConstant()) {
                throw new PermanentBailoutException("%s has a size that is not a compile time constant.", node);
            }

            int sizeInBytes = NumUtil.safeToInt(sizeNode.asJavaConstant().asLong());
            GraalError.guarantee(sizeInBytes > 0, "%s: must allocate at least 1 byte.", node);
            StackValueSnippets.this.lower(tool, node, sizeInBytes, node.alignmentInBytes, node.slotIdentity, node.checkVirtualThread);
        }
    }
}
