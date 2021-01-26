/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_LOCATIONS;

import java.util.Arrays;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleSafepointNode;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;

public final class SubstrateThreadLocalHandshakeSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    private static void pollSnippet() {
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY,
                        SubstrateThreadLocalHandshake.PENDING.get() != 0) && SubstrateThreadLocalHandshake.DISABLED.get() == 0) {
            foreignPoll(SubstrateThreadLocalHandshake.FOREIGN_POLL);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void foreignPoll(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    public SubstrateThreadLocalHandshakeSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);
        lowerings.put(TruffleSafepointNode.class, new SafepointLowering());
    }

    private static LocationIdentity[] getPollKilledLocations() {
        int newLength = TLAB_LOCATIONS.length + 2;
        LocationIdentity[] locations = Arrays.copyOf(TLAB_LOCATIONS, newLength);
        locations[newLength - 1] = SubstrateThreadLocalHandshake.PENDING.getLocationIdentity();
        locations[newLength - 2] = SubstrateThreadLocalHandshake.DISABLED.getLocationIdentity();
        return locations;
    }

    class SafepointLowering implements NodeLoweringProvider<TruffleSafepointNode> {
        private final SnippetInfo pollSnippet = snippet(SubstrateThreadLocalHandshakeSnippets.class, "pollSnippet",
                        getPollKilledLocations());

        @Override
        public void lower(TruffleSafepointNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
                assert SubstrateOptions.MultiThreaded.getValue() : "safepoints are only inserted into the graph in MultiThreaded mode";

                Arguments args = new Arguments(pollSnippet, node.graph().getGuardsStage(), tool.getLoweringStage());
                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
