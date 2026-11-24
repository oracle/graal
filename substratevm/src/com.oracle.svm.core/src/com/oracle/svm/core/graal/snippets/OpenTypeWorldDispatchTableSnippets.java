/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.hub.DynamicHubUtils.HASHING_ITABLE_SHIFT;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.unknownProbability;

import java.nio.ByteOrder;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.shared.config.ObjectLayout;
import com.oracle.svm.core.config.ObjectLayoutCompilerSupport;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.nodes.LoadOpenTypeWorldDispatchTableStartingOffset;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubUtils;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.jvmci.shared.meta.SharedMethod;
import com.oracle.svm.jvmci.shared.meta.SharedType;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Fold;
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
import org.graalvm.word.impl.ObjectAccess;
import org.graalvm.word.impl.Word;
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
            int offset = (int) ObjectLayoutCompilerSupport.getArrayElementOffset(ImageSingletons.lookup(ObjectLayout.class), JavaKind.Int, numClassTypes + i);
            // GR-51603 can make a floating read
            int checkedInterfaceId = ObjectAccess.readInt(checkedTypeIds, offset, NamedLocationIdentity.FINAL_LOCATION);
            if (checkedInterfaceId == interfaceID) {
                offset = (int) ObjectLayoutCompilerSupport.getArrayElementOffset(ImageSingletons.lookup(ObjectLayout.class), JavaKind.Int, numClassTypes + i + 1);
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
     * starting offset for the given interfaceID. See {@link DynamicHubUtils#hash(int, int)} for
     * the image-wide choice between shift-and-mask and PEXT hashing.
     */
    private static long determineITableStartingOffsetHashed(
                    DynamicHub checkedHub,
                    int interfaceID) {
        ReplacementsUtil.dynamicAssert(interfaceID > 0 && interfaceID <= DynamicHubUtils.MAX_HASHED_INTERFACE_ID, "Interface ID must be in the supported hashing range.");

        int hashParam = checkedHub.getOpenTypeWorldInterfaceHashParam();
        long[] hashTable = checkedHub.getOpenTypeWorldInterfaceHashTable();
        long hashTableEntry = readInterfaceHashTableEntry(hashTable, hashParam, interfaceID);

        // The interface ID occupies the low word.
        ReplacementsUtil.dynamicAssert(interfaceID == (int) hashTableEntry, "InterfaceIDs do not match.");
        return Integer.toUnsignedLong(readInterfaceHashTableOffset(hashTable, hashParam, interfaceID));
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
    private static long determineITableStartingOffsetHashedNonSnippet(
                    DynamicHub checkedHub,
                    int interfaceID) {
        assert interfaceID > 0 && interfaceID <= DynamicHubUtils.MAX_HASHED_INTERFACE_ID : "Interface ID must be in the supported hashing range.";

        int hashParam = checkedHub.getOpenTypeWorldInterfaceHashParam();
        long[] hashTable = checkedHub.getOpenTypeWorldInterfaceHashTable();
        long hashTableEntry = readInterfaceHashTableEntry(hashTable, hashParam, interfaceID);

        // The interface ID occupies the low word.
        assert interfaceID == (int) hashTableEntry : "InterfaceIDs do not match.";
        return Integer.toUnsignedLong(readInterfaceHashTableOffset(hashTable, hashParam, interfaceID));
    }

    /**
     * Returns the itable byte offset, or {@code -1} if the receiver does not implement the
     * interface. The interface ID must be positive and covered by interface hashing. Unlike the
     * unchecked dispatch lookup, this verifies the stored ID before using its offset, allowing
     * receiver validation and dispatch to share a single hash-table read.
     */
    @AlwaysInline("Combine interface receiver validation and dispatch lookup")
    public static long findHashedITableStartingOffset(DynamicHub checkedHub, int interfaceID) {
        assert SubstrateOptions.useInterfaceHashing() && interfaceID > 0 && interfaceID <= SubstrateOptions.interfaceHashingMaxId();
        int hashParam = checkedHub.getOpenTypeWorldInterfaceHashParam();
        long[] hashTable = checkedHub.getOpenTypeWorldInterfaceHashTable();
        long entry = readInterfaceHashTableEntry(hashTable, hashParam, interfaceID);
        if (probability(BranchProbabilityNode.SLOW_PATH_PROBABILITY, (int) entry != interfaceID)) {
            return -1;
        }
        return entry >>> HASHING_ITABLE_SHIFT;
    }

    @Fold
    static int itableOffsetWordOffset() {
        return SubstrateTarget.getArchitecture().getByteOrder() == ByteOrder.LITTLE_ENDIAN ? Integer.BYTES : 0;
    }

    @AlwaysInline("Avoid a helper call for the hash calculation and offset load")
    private static int readInterfaceHashTableOffset(long[] hashTable, int hashParam, int interfaceID) {
        int hash = DynamicHubUtils.hash(interfaceID, hashParam);
        long offset = ObjectLayoutCompilerSupport.getArrayElementOffset(ImageSingletons.lookup(ObjectLayout.class), JavaKind.Long, hash) + itableOffsetWordOffset();
        // The offset occupies the upper word; the table's encoding and stride are unchanged.
        return ObjectAccess.readInt(hashTable, Word.signed(offset), NamedLocationIdentity.FINAL_LOCATION);
    }

    @AlwaysInline("Keep the packed interface entry available to its caller")
    private static long readInterfaceHashTableEntry(long[] hashTable, int hashParam, int interfaceID) {
        int hash = DynamicHubUtils.hash(interfaceID, hashParam);
        long offset = ObjectLayoutCompilerSupport.getArrayElementOffset(ImageSingletons.lookup(ObjectLayout.class), JavaKind.Long, hash);
        return ObjectAccess.readLong(hashTable, Word.signed(offset), NamedLocationIdentity.FINAL_LOCATION);
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
