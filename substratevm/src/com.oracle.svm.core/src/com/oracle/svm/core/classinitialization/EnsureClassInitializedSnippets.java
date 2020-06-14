/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.classinitialization;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.ForeignCallWithExceptionNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

public final class EnsureClassInitializedSnippets extends SubstrateTemplates implements Snippets {

    private static final SubstrateForeignCallDescriptor INITIALIZE = SnippetRuntime.findForeignCall(ClassInitializationInfo.class, "initialize", false);

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    INITIALIZE,
    };

    @Snippet
    private static void ensureClassIsInitializedSnippet(DynamicHub hub) {
        ClassInitializationInfo info = hub.getClassInitializationInfo();
        /*
         * The ClassInitializationInfo field is always initialized by the image generator. We can
         * save the explicit null check.
         */
        ClassInitializationInfo infoNonNull = (ClassInitializationInfo) PiNode.piCastNonNull(info, SnippetAnchorNode.anchor());

        if (BranchProbabilityNode.probability(BranchProbabilityNode.LUDICROUSLY_SLOW_PATH_PROBABILITY, !infoNonNull.isInitialized())) {
            callInitialize(INITIALIZE, infoNonNull, DynamicHub.toClass(hub));
        }
    }

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native void callInitialize(@ConstantNodeParameter ForeignCallDescriptor descriptor, ClassInitializationInfo info, Class<?> clazz);

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new EnsureClassInitializedSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private EnsureClassInitializedSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);
        lowerings.put(EnsureClassInitializedNode.class, new EnsureClassInitializedNodeLowering());
    }

    class EnsureClassInitializedNodeLowering implements NodeLoweringProvider<EnsureClassInitializedNode> {
        private final SnippetInfo ensureClassIsInitialized = snippet(EnsureClassInitializedSnippets.class, "ensureClassIsInitializedSnippet", LocationIdentity.any());

        @Override
        public void lower(EnsureClassInitializedNode node, LoweringTool tool) {
            Arguments args = new Arguments(ensureClassIsInitialized, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("hub", node.getHub());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
