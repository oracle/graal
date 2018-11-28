/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import static com.oracle.svm.core.graal.snippets.SubstrateIntrinsics.runtimeCall;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.DynamicDeoptimizeNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import com.oracle.svm.core.deopt.DeoptimizationRuntime;
import com.oracle.svm.core.graal.nodes.UnreachableNode;

import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

public final class DeoptRuntimeSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static void deoptSnippet(long actionAndReason, SpeculationReason speculation) {
        runtimeCall(DeoptimizationRuntime.DEOPTIMIZE, actionAndReason, speculation);
        throw UnreachableNode.unreachable();
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new DeoptRuntimeSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private DeoptRuntimeSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);

        AbstractDeoptimizeLowering lowering = new AbstractDeoptimizeLowering();
        lowerings.put(DeoptimizeNode.class, lowering);
        lowerings.put(DynamicDeoptimizeNode.class, lowering);
    }

    protected class AbstractDeoptimizeLowering implements NodeLoweringProvider<AbstractDeoptimizeNode> {

        private final SnippetInfo deopt = snippet(DeoptRuntimeSnippets.class, "deoptSnippet");

        @Override
        public void lower(AbstractDeoptimizeNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                return;
            }

            Arguments args = new Arguments(deopt, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("actionAndReason", node.getActionAndReason(tool.getMetaAccess()));
            args.add("speculation", node.getSpeculation(tool.getMetaAccess()));
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
