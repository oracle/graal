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

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;
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

final class StackValueSnippets extends SubstrateTemplates implements Snippets {
    private static final String EXCEPTION_MESSAGE = "StackValue must not be used in a virtual thread unless the method is annotated @" + Uninterruptible.class.getSimpleName() + '.';
    private static final IllegalThreadStateException CACHED_EXCEPTION = new IllegalThreadStateException(EXCEPTION_MESSAGE + ' ' + ImplicitExceptions.NO_STACK_MSG);

    static final SnippetRuntime.SubstrateForeignCallDescriptor THROW_CACHED_EXCEPTION = SnippetRuntime.findForeignCall(StackValueSnippets.class, "throwCachedException", true);
    static final SnippetRuntime.SubstrateForeignCallDescriptor THROW_NEW_EXCEPTION = SnippetRuntime.findForeignCall(StackValueSnippets.class, "throwNewException", true);
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
        lowerings.put(StackValueNode.class, new StackValueVirtualThreadCheckLowering());
    }

    final class StackValueVirtualThreadCheckLowering implements NodeLoweringProvider<StackValueNode> {
        @Override
        public void lower(StackValueNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();

            GraalError.guarantee(tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER, "Must lower before mid-tier StackValueRecursionDepthPhase");

            boolean mustNotAllocate = ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(graph.method());

            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(stackValueSnippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.addConst("sizeInBytes", node.sizeInBytes);
            args.addConst("alignmentInBytes", node.alignmentInBytes);
            args.addConst("slotIdentifier", node.slotIdentity);
            args.addConst("disallowVirtualThread", node.checkVirtualThread);
            args.addConst("mustNotAllocate", mustNotAllocate);
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
