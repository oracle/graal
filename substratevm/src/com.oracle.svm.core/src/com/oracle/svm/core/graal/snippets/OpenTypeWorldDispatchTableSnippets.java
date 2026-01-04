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

import static com.oracle.svm.core.hub.DynamicHubUtils.HASHING_INTERFACE_MASK;
import static com.oracle.svm.core.hub.DynamicHubUtils.HASHING_ITABLE_SHIFT;
import static com.oracle.svm.core.hub.DynamicHubUtils.HASHING_SHIFT_OFFSET;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.unknownProbability;

import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.nodes.LoadOpenTypeWorldDispatchTableStartingOffset;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubUtils;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.vm.ci.meta.JavaKind;

public final class OpenTypeWorldDispatchTableSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    private static long loadITableStartingOffset(
                    @Snippet.NonNullParameter DynamicHub hub,
                    int interfaceID,
                    @Snippet.ConstantParameter boolean useInterfaceHashing) {
        if (useInterfaceHashing && probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, interfaceID <= SubstrateOptions.interfaceHashingMaxId())) {
            return determineITableStartingOffsetHashed(hub, interfaceID);
        }
        return determineITableStartingOffsetIterative(hub, interfaceID);
    }

    @Snippet
    private static long loadDispatchTableStartingOffset(
                    @Snippet.NonNullParameter DynamicHub hub,
                    int interfaceID, @Snippet.ConstantParameter int vtableStartingOffset,
                    @Snippet.ConstantParameter boolean useInterfaceHashing) {
        if (unknownProbability(interfaceID >= 0)) {
            if (useInterfaceHashing && probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, interfaceID <= SubstrateOptions.interfaceHashingMaxId())) {
                return determineITableStartingOffsetHashed(hub, interfaceID);
            }
            return determineITableStartingOffsetIterative(hub, interfaceID);
        } else {
            // the class dispatch table is always first
            return vtableStartingOffset;
        }
    }

    /**
     * Iterative lookup of itable starting offsets used if
     * {@link SubstrateOptions#useInterfaceHashing()} is disabled or if the interfaceID exceeds
     * {@link SubstrateOptions#interfaceHashingMaxId()}.
     */
    private static long determineITableStartingOffsetIterative(
                    DynamicHub checkedHub,
                    int interfaceID) {
        int numClassTypes = checkedHub.getNumClassTypes();
        int numInterfaceTypes = checkedHub.getNumIterableInterfaceTypes();
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

    /**
     * If {@link SubstrateOptions#useInterfaceHashing()} is enabled, interfaceIDs and itable
     * starting offsets are stored in a hash table (see TypeCheckBuilder for a general
     * documentation). This snippet handles the lookup in the hash table and returns the itable
     * starting offset for the given interfaceID. See {@link DynamicHubUtils#hashParam(int[])} for
     * details on the hashing function and hashing parameter.
     */
    private static int determineITableStartingOffsetHashed(
                    DynamicHub checkedHub,
                    int interfaceID) {
        ReplacementsUtil.dynamicAssert(NumUtil.isUShort(interfaceID), "InterfaceIDs must fit in a short to be used for hashing.");

        // The upper byte of the hashParam holds the shift value, the lower three bytes hold p
        // which is used for bitwise "and": hashParam = shift << HASHING_SHIFT_OFFSET | p.
        int hashParam = checkedHub.getOpenTypeWorldInterfaceHashParam();
        int shift = hashParam >>> HASHING_SHIFT_OFFSET;
        int[] hashTable = checkedHub.getOpenTypeWorldInterfaceHashTable();

        // No need to mask hashParam to get "p". interfaceID fits in a short -> the two upper
        // bytes are 0.
        int hash = (interfaceID >>> shift) & hashParam;
        int offset = (int) ImageSingletons.lookup(ObjectLayout.class).getArrayElementOffset(JavaKind.Int, hash);
        int hashTableEntry = ObjectAccess.readInt(hashTable, offset, NamedLocationIdentity.FINAL_LOCATION);

        // Hashtable entries contain integers which hold the iTableOffset and the interfaceID:
        // hashTableEntry = iTableOffset << HASHING_ITABLE_SHIFT | interfaceID
        ReplacementsUtil.dynamicAssert(interfaceID == (hashTableEntry & HASHING_INTERFACE_MASK), "InterfaceIDs do not match.");
        return (hashTableEntry >>> HASHING_ITABLE_SHIFT);
    }

    public static long determineITableStartingOffset(
                    DynamicHub checkedHub,
                    int interfaceID) {
        if (SubstrateOptions.useInterfaceHashing() && interfaceID <= SubstrateOptions.interfaceHashingMaxId()) {
            // Use the non-snippet version which contains no snippet asserts.
            return determineITableStartingOffsetHashedNonSnippet(checkedHub, interfaceID);
        } else {
            return determineITableStartingOffsetIterative(checkedHub, interfaceID);
        }
    }

    /**
     * IMPORTANT: Has to be identical to {@link #determineITableStartingOffsetHashed} but with
     * "real" {@code assert}s instead of {@link ReplacementsUtil#dynamicAssert}. Required for being
     * called outside of snippets.
     */
    private static int determineITableStartingOffsetHashedNonSnippet(
                    DynamicHub checkedHub,
                    int interfaceID) {
        assert NumUtil.isUShort(interfaceID) : "InterfaceIDs must fit in a short to be used for hashing.";

        // The upper byte of the hashParam holds the shift value, the lower three bytes hold p
        // which is used for bitwise "and": hashParam = shift << HASHING_SHIFT_OFFSET | p.
        int hashParam = checkedHub.getOpenTypeWorldInterfaceHashParam();
        int shift = hashParam >>> HASHING_SHIFT_OFFSET;
        int[] hashTable = checkedHub.getOpenTypeWorldInterfaceHashTable();

        // No need to mask hashParam to get "p". interfaceID fits in a short -> the two upper
        // bytes are 0.
        int hash = (interfaceID >>> shift) & hashParam;
        int offset = (int) ImageSingletons.lookup(ObjectLayout.class).getArrayElementOffset(JavaKind.Int, hash);
        int hashTableEntry = ObjectAccess.readInt(hashTable, offset, NamedLocationIdentity.FINAL_LOCATION);

        // Hashtable entries contain integers which hold the iTableOffset and the interfaceID:
        // hashTableEntry = iTableOffset << HASHING_ITABLE_SHIFT | interfaceID
        assert interfaceID == (hashTableEntry & HASHING_INTERFACE_MASK) : "InterfaceIDs do not match.";
        return (hashTableEntry >>> HASHING_ITABLE_SHIFT);
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
                    args.add("interfaceID", ((SharedType) target.getDeclaringClass()).getInterfaceID());
                    args.add("useInterfaceHashing", SubstrateOptions.useInterfaceHashing());
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
                args.add("interfaceID", node.getInterfaceID());
                args.add("vtableStartingOffset", vtableStartingOffset);
                args.add("useInterfaceHashing", SubstrateOptions.useInterfaceHashing());
                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
