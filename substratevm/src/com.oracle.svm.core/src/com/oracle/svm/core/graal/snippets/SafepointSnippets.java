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
package com.oracle.svm.core.graal.snippets;

import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.TLAB_LOCATIONS;

import java.util.Arrays;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.Safepoint;

final class SafepointSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    private static void safepointSnippet() {
        final boolean needSlowPath = SafepointCheckNode.test();
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY, needSlowPath)) {
            callSlowPathSafepointCheck(Safepoint.ENTER_SLOW_PATH_SAFEPOINT_CHECK);
        }
    }

    private final SnippetInfo safepoint;

    SafepointSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);

        this.safepoint = snippet(providers, SafepointSnippets.class, "safepointSnippet", getKilledLocations());
        lowerings.put(SafepointNode.class, new SafepointLowering());
    }

    private static LocationIdentity[] getKilledLocations() {
        int newLength = TLAB_LOCATIONS.length + 1;
        LocationIdentity[] locations = Arrays.copyOf(TLAB_LOCATIONS, newLength);
        locations[newLength - 1] = Safepoint.getThreadLocalSafepointRequestedLocationIdentity();
        return locations;
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPathSafepointCheck(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    class SafepointLowering implements NodeLoweringProvider<SafepointNode> {
        @Override
        public void lower(SafepointNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
                assert SubstrateOptions.MultiThreaded.getValue() : "safepoints are only inserted into the graph in MultiThreaded mode";
                if (Uninterruptible.Utils.isUninterruptible(node.graph().method())) {
                    /* Basic sanity check to catch errors during safepoint insertion. */
                    throw GraalError.shouldNotReachHere("Must not insert safepoints in Uninterruptible code: " + node.stateBefore().toString(Verbosity.Debugger));
                }
                Arguments args = new Arguments(safepoint, node.graph().getGuardsStage(), tool.getLoweringStage());
                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}

@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
class SafepointFeature implements InternalFeature {

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(Safepoint.FOREIGN_CALLS);
    }

    @Override
    @SuppressWarnings("unused")
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        new SafepointSnippets(options, providers, lowerings);
    }
}
