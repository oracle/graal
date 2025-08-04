/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.unknownProbability;

import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.nodes.LoadOpenTypeWorldDispatchTableStartingOffset;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.vm.ci.meta.JavaKind;

public final class OpenTypeWorldDispatchTableSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    private static long loadITableStartingOffset(
                    @Snippet.NonNullParameter DynamicHub hub,
                    int interfaceTypeID) {
        return determineITableStartingOffset(hub, interfaceTypeID);
    }

    @Snippet
    private static long loadDispatchTableStartingOffset(
                    @Snippet.NonNullParameter DynamicHub hub,
                    int interfaceTypeID, @Snippet.ConstantParameter int vtableStartingOffset) {
        if (unknownProbability(interfaceTypeID >= 0)) {
            return determineITableStartingOffset(hub, interfaceTypeID);
        } else {
            // the class dispatch table is always first
            return vtableStartingOffset;
        }
    }

    public static long determineITableStartingOffset(
                    DynamicHub checkedHub,
                    int interfaceID) {

        int numClassTypes = checkedHub.getNumClassTypes();
        int numInterfaceTypes = checkedHub.getNumInterfaceTypes();
        int[] checkedTypeIds = checkedHub.getOpenTypeWorldTypeCheckSlots();
        for (int i = 0; i < numInterfaceTypes * 2; i += 2) {
            // int checkedInterfaceId = checkedTypeIds[numClassTypes + i];
            int offset = (int) ImageSingletons.lookup(ObjectLayout.class).getArrayElementOffset(JavaKind.Int, numClassTypes + i);
            // GR-51603 can make a floating read
            int checkedInterfaceId = ObjectAccess.readInt(checkedTypeIds, offset, NamedLocationIdentity.FINAL_LOCATION);
            if (checkedInterfaceId == interfaceID) {
                offset = (int) ImageSingletons.lookup(ObjectLayout.class).getArrayElementOffset(JavaKind.Int, numClassTypes + i + 1);
                long result = ObjectAccess.readInt(checkedTypeIds, offset, NamedLocationIdentity.FINAL_LOCATION);
                return result;
            }
        }

        throw UnreachableNode.unreachable();
    }

    private final SnippetTemplate.SnippetInfo loadITableStartingOffset;
    private final SnippetTemplate.SnippetInfo loadDispatchTableStartingOffset;

    private OpenTypeWorldDispatchTableSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);

        this.loadITableStartingOffset = snippet(providers, OpenTypeWorldDispatchTableSnippets.class, "loadITableStartingOffset", getKilledLocations());
        this.loadDispatchTableStartingOffset = snippet(providers, OpenTypeWorldDispatchTableSnippets.class, "loadDispatchTableStartingOffset", getKilledLocations());

        lowerings.put(LoadOpenTypeWorldDispatchTableStartingOffset.class, new DispatchTableStartingOffsetLowering());
    }

    private static LocationIdentity[] getKilledLocations() {
        return new LocationIdentity[0];
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new OpenTypeWorldDispatchTableSnippets(options, providers, lowerings);
    }

    class DispatchTableStartingOffsetLowering implements NodeLoweringProvider<LoadOpenTypeWorldDispatchTableStartingOffset> {

        @Override
        public void lower(LoadOpenTypeWorldDispatchTableStartingOffset node, LoweringTool tool) {
            SharedMethod target = node.getTarget();
            int vtableStartingOffset = KnownOffsets.singleton().getVTableBaseOffset();
            if (target != null) {
                /*
                 * Update target to point to indirect call target. The indirect call target is
                 * different than the original target when the original target is a method we have
                 * not placed in any virtual/interface table. See SharedMethod#getIndirectCallTarget
                 * and HostedMethod#indirectCallVTableIndex for more information.
                 */
                target = target.getIndirectCallTarget();
                /*
                 * If the target is known, then we know whether to use the class dispatch table or
                 * an interface dispatch table.
                 */
                if (target.getDeclaringClass().isInterface()) {
                    SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(loadITableStartingOffset, node.graph(), tool.getLoweringStage());
                    args.add("hub", node.getHub());
                    args.add("interfaceTypeID", ((SharedType) target.getDeclaringClass()).getTypeID());
                    template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);

                } else {
                    // We know the call uses the class dispatch table, which is always first
                    var graph = node.graph();
                    graph.replaceFixedWithFloating(node, ConstantNode.forLong(vtableStartingOffset, graph));
                }
            } else {
                /*
                 * Otherwise we must search on the interfaceID
                 */
                SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(loadDispatchTableStartingOffset, node.graph(), tool.getLoweringStage());
                args.add("hub", node.getHub());
                args.add("interfaceTypeID", node.getInterfaceTypeID());
                args.add("vtableStartingOffset", vtableStartingOffset);
                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
