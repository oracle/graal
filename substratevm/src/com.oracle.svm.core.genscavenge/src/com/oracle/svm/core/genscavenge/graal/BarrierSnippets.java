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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.gc.SerialArrayRangeWriteBarrier;
import org.graalvm.compiler.nodes.gc.SerialWriteBarrier;
import org.graalvm.compiler.nodes.gc.WriteBarrier;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.CounterFeature;

import jdk.vm.ci.meta.ResolvedJavaType;

public class BarrierSnippets extends SubstrateTemplates implements Snippets {
    /** A LocationIdentity to distinguish card locations from other locations. */
    public static final LocationIdentity CARD_REMEMBERED_SET_LOCATION = NamedLocationIdentity.mutable("CardRememberedSet");

    public static class Options {
        @Option(help = "Instrument write barriers with counters")//
        public static final HostedOptionKey<Boolean> CountWriteBarriers = new HostedOptionKey<>(false);

        @Option(help = "Verify write barriers")//
        public static final HostedOptionKey<Boolean> VerifyWriteBarriers = new HostedOptionKey<>(false);
    }

    @Fold
    static BarrierSnippetCounters counters() {
        return ImageSingletons.lookup(BarrierSnippetCounters.class);
    }

    BarrierSnippets(OptionValues options, Providers providers) {
        super(options, providers);
    }

    public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        PostWriteBarrierLowering lowering = new PostWriteBarrierLowering();
        lowerings.put(SerialWriteBarrier.class, lowering);
        // write barriers are currently always imprecise
        lowerings.put(SerialArrayRangeWriteBarrier.class, lowering);
    }

    @Snippet
    public static void postWriteBarrierSnippet(Object object, @ConstantParameter boolean alwaysAlignedChunk, @ConstantParameter boolean verifyOnly) {
        counters().postWriteBarrier.inc();

        Object fixedObject = FixedValueAnchorNode.getObject(object);
        UnsignedWord objectHeader = ObjectHeaderImpl.readHeaderFromObject(fixedObject);

        if (Options.VerifyWriteBarriers.getValue() && alwaysAlignedChunk) {
            /*
             * To increase verification coverage, we do the verification before checking if a
             * barrier is needed at all. And in addition to verifying that the object is in an
             * aligned chunk, we also verify that it is not an array at all because most arrays are
             * small and therefore in an aligned chunk.
             */
            if (ObjectHeaderImpl.isUnalignedHeader(objectHeader) || object == null || object.getClass().isArray()) {
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
                counters().postWriteBarrierUnaligned.inc();
                RememberedSet.get().dirtyCardForUnalignedObject(fixedObject, verifyOnly);
                return;
            }
        }

        counters().postWriteBarrierAligned.inc();
        RememberedSet.get().dirtyCardForAlignedObject(fixedObject, verifyOnly);
    }

    private class PostWriteBarrierLowering implements NodeLoweringProvider<WriteBarrier> {
        private final SnippetInfo postWriteBarrierSnippet = snippet(BarrierSnippets.class, "postWriteBarrierSnippet", CARD_REMEMBERED_SET_LOCATION);

        @Override
        public void lower(WriteBarrier barrier, LoweringTool tool) {
            Arguments args = new Arguments(postWriteBarrierSnippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            OffsetAddressNode address = (OffsetAddressNode) barrier.getAddress();

            /*
             * We know that instances (in contrast to arrays) are always in aligned chunks. There is
             * no code anywhere that would allocate an instance into an unaligned chunk.
             *
             * Note that arrays can be assigned to values that have the type java.lang.Object, so
             * that case is excluded. Arrays can also implement some interfaces, like Serializable.
             * For simplicity, we exclude all interface types.
             */
            ResolvedJavaType baseType = StampTool.typeOrNull(address.getBase());
            boolean alwaysAlignedChunk = baseType != null && !baseType.isArray() && !baseType.isJavaLangObject() && !baseType.isInterface();

            args.add("object", address.getBase());
            args.addConst("alwaysAlignedChunk", alwaysAlignedChunk);
            args.addConst("verifyOnly", getVerifyOnly(barrier));

            template(barrier, args).instantiate(providers.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        private boolean getVerifyOnly(WriteBarrier barrier) {
            if (barrier instanceof SerialWriteBarrier) {
                return ((SerialWriteBarrier) barrier).getVerifyOnly();
            }
            return false;
        }
    }

    public static final class TestingBackDoor {
        private TestingBackDoor() {
        }

        public static long getPostWriteBarrierCount() {
            return counters().postWriteBarrier.getValue();
        }

        public static long getPostWriteBarrierAlignedCount() {
            return counters().postWriteBarrierAligned.getValue();
        }

        public static long getPostWriteBarrierUnalignedCount() {
            return counters().postWriteBarrierUnaligned.getValue();
        }
    }
}

class BarrierSnippetCounters {
    private final Counter.Group counters = new Counter.Group(BarrierSnippets.Options.CountWriteBarriers, "WriteBarriers");
    final Counter postWriteBarrier = new Counter(counters, "postWriteBarrier", "post-write barriers");
    final Counter postWriteBarrierAligned = new Counter(counters, "postWriteBarrierAligned", "aligned object path of post-write barriers");
    final Counter postWriteBarrierUnaligned = new Counter(counters, "postWriteBarrierUnaligned", "unaligned object path of post-write barriers");
}

@AutomaticFeature
class BarrierSnippetCountersFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.UseSerialGC.getValue() && SubstrateOptions.useRememberedSet();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(CounterFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(BarrierSnippetCounters.class, new BarrierSnippetCounters());
    }
}
