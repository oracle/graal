/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.GC_LOCATIONS;

import java.util.Arrays;
import java.util.Map;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.nodes.foreign.MemoryArenaValidInScopeNode;
import com.oracle.svm.core.thread.SafepointCheckCounter;
import com.oracle.svm.core.thread.SafepointSlowpath;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.vm.ci.meta.ResolvedJavaField;

public final class SafepointSnippets extends SubstrateTemplates implements Snippets {

    private final SnippetInfo safepoint;

    SafepointSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);

        this.safepoint = snippet(providers, SafepointSnippets.class, "safepointSnippet", getKilledLocations(providers.getMetaAccess().lookupJavaField(MemoryArenaValidInScopeNode.STATE_FIELD)));
        lowerings.put(SafepointNode.class, new SafepointLowering());
    }

    @Snippet
    private static void safepointSnippet() {
        final boolean needSlowPath = SafepointCheckNode.test();
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY, needSlowPath)) {
            callSlowPathSafepointCheck(SafepointSlowpath.ENTER_SLOW_PATH_SAFEPOINT_CHECK);
        }
    }

    private static LocationIdentity[] getKilledLocations(ResolvedJavaField memorySessionImplStateField) {
        int newLength = GC_LOCATIONS.length + 2;
        LocationIdentity[] locations = Arrays.copyOf(GC_LOCATIONS, newLength);
        locations[newLength - 2] = SafepointCheckCounter.getLocationIdentity();
        locations[newLength - 1] = new FieldLocationIdentity(memorySessionImplStateField);
        return locations;
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPathSafepointCheck(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    class SafepointLowering implements NodeLoweringProvider<SafepointNode> {
        @Override
        public void lower(SafepointNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
                if (((SharedMethod) node.graph().method()).isUninterruptible()) {
                    /* Basic sanity check to catch errors during safepoint insertion. */
                    throw GraalError.shouldNotReachHere("Must not insert safepoints in Uninterruptible code: " + node.stateBefore().toString(Verbosity.Debugger)); // ExcludeFromJacocoGeneratedReport
                }
                Arguments args = new Arguments(safepoint, node.graph(), tool.getLoweringStage());
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
        foreignCalls.register(SafepointSlowpath.FOREIGN_CALLS);
    }

    @Override
    @SuppressWarnings("unused")
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        new SafepointSnippets(options, providers, lowerings);
    }
}
