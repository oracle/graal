/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import java.util.Map;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.SerialGCOptions;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.StoredContinuation;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.BreakpointNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.FixedValueAnchorNode;
import jdk.graal.compiler.nodes.gc.SerialArrayRangeWriteBarrier;
import jdk.graal.compiler.nodes.gc.SerialWriteBarrier;
import jdk.graal.compiler.nodes.gc.WriteBarrier;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class BarrierSnippets extends SubstrateTemplates implements Snippets {
    /** A LocationIdentity to distinguish card locations from other locations. */
    public static final LocationIdentity CARD_REMEMBERED_SET_LOCATION = NamedLocationIdentity.mutable("CardRememberedSet");

    private final SnippetInfo postWriteBarrierSnippet;

    BarrierSnippets(OptionValues options, Providers providers) {
        super(options, providers);

        this.postWriteBarrierSnippet = snippet(providers, BarrierSnippets.class, "postWriteBarrierSnippet", CARD_REMEMBERED_SET_LOCATION);
    }

    public void registerLowerings(MetaAccessProvider metaAccess, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        PostWriteBarrierLowering lowering = new PostWriteBarrierLowering(metaAccess);
        lowerings.put(SerialWriteBarrier.class, lowering);
        // write barriers are currently always imprecise
        lowerings.put(SerialArrayRangeWriteBarrier.class, lowering);
    }

    @Snippet
    public static void postWriteBarrierSnippet(Object object, @ConstantParameter boolean alwaysAlignedChunk, @ConstantParameter boolean verifyOnly) {
        Object fixedObject = FixedValueAnchorNode.getObject(object);
        UnsignedWord objectHeader = ObjectHeader.readHeaderFromObject(fixedObject);

        if (SerialGCOptions.VerifyWriteBarriers.getValue() && alwaysAlignedChunk) {
            /*
             * To increase verification coverage, we do the verification before checking if a
             * barrier is needed at all. And in addition to verifying that the object is in an
             * aligned chunk, we also verify that it is not an array at all because most arrays are
             * small and therefore in an aligned chunk.
             */

            if (BranchProbabilityNode.probability(BranchProbabilityNode.SLOW_PATH_PROBABILITY, ObjectHeaderImpl.isUnalignedHeader(objectHeader))) {
                BreakpointNode.breakpoint();
            }
            if (BranchProbabilityNode.probability(BranchProbabilityNode.SLOW_PATH_PROBABILITY, object == null)) {
                BreakpointNode.breakpoint();
            }
            if (BranchProbabilityNode.probability(BranchProbabilityNode.SLOW_PATH_PROBABILITY, object.getClass().isArray())) {
                BreakpointNode.breakpoint();
            }
        }

        boolean needsBarrier = RememberedSet.get().hasRememberedSet(objectHeader);
        if (BranchProbabilityNode.probability(BranchProbabilityNode.FREQUENT_PROBABILITY, !needsBarrier)) {
            return;
        }

        if (!alwaysAlignedChunk) {
            boolean unaligned = ObjectHeaderImpl.isUnalignedHeader(objectHeader);
            if (BranchProbabilityNode.probability(BranchProbabilityNode.NOT_LIKELY_PROBABILITY, unaligned)) {
                RememberedSet.get().dirtyCardForUnalignedObject(fixedObject, verifyOnly);
                return;
            }
        }

        RememberedSet.get().dirtyCardForAlignedObject(fixedObject, verifyOnly);
    }

    private class PostWriteBarrierLowering implements NodeLoweringProvider<WriteBarrier> {
        private final ResolvedJavaType storedContinuationType;

        PostWriteBarrierLowering(MetaAccessProvider metaAccess) {
            storedContinuationType = metaAccess.lookupJavaType(StoredContinuation.class);
        }

        @Override
        public void lower(WriteBarrier barrier, LoweringTool tool) {
            Arguments args = new Arguments(postWriteBarrierSnippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            OffsetAddressNode address = (OffsetAddressNode) barrier.getAddress();

            /*
             * We know that instances (in contrast to arrays) are always in aligned chunks, except
             * for StoredContinuation objects, but these are immutable and do not need barriers.
             *
             * Note that arrays can be assigned to values that have the type java.lang.Object, so
             * that case is excluded. Arrays can also implement some interfaces, like Serializable.
             * For simplicity, we exclude all interface types.
             */
            ResolvedJavaType baseType = StampTool.typeOrNull(address.getBase());
            assert baseType == null || !storedContinuationType.isAssignableFrom(baseType) : "StoredContinuation should be effectively immutable and references only be written by GC";
            boolean alwaysAlignedChunk = baseType != null && !baseType.isArray() && !baseType.isJavaLangObject() && !baseType.isInterface();

            args.add("object", address.getBase());
            args.addConst("alwaysAlignedChunk", alwaysAlignedChunk);
            args.addConst("verifyOnly", getVerifyOnly(barrier));

            template(tool, barrier, args).instantiate(tool.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        private static boolean getVerifyOnly(WriteBarrier barrier) {
            if (barrier instanceof SerialWriteBarrier) {
                return ((SerialWriteBarrier) barrier).getVerifyOnly();
            }
            return false;
        }
    }
}
