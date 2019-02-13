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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.CardTable;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.CounterFeature;

/** Methods in this class are snippets. */
public class BarrierSnippets extends SubstrateTemplates implements Snippets {

    public static class Options {
        @Option(help = "Instrument write barriers with counters")//
        public static final HostedOptionKey<Boolean> CountWriteBarriers = new HostedOptionKey<>(false);
    }

    @Fold
    protected static BarrierSnippetCounters counters() {
        return ImageSingletons.lookup(BarrierSnippetCounters.class);
    }

    protected static BarrierSnippets factory(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection) {
        return new BarrierSnippets(options, factories, providers, snippetReflection);
    }

    /** The entry point for registering lowerings. */
    public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        lowerings.put(PostWriteBarrierNode.class, new PostWriteBarrierLowering());
    }

    /**
     * Given an object, dirty the card for the object.
     *
     * @param object The object to which the write has been done.
     */
    @Snippet
    public static void postWriteBarrierSnippet(Object object) {
        counters().postWriteBarrier.inc();

        // Get the Object to which the write happened.
        final Object fixedObject = FixedValueAnchorNode.getObject(object);
        // Get the ObjectHeader from the Object.
        final UnsignedWord objectHeader = ObjectHeader.readHeaderFromObject(fixedObject);
        // Does the ObjectHeader indicate that I need a barrier?
        final boolean needsBarrier = ObjectHeaderImpl.hasRememberedSet(objectHeader);
        if (BranchProbabilityNode.probability(BranchProbabilityNode.FREQUENT_PROBABILITY, !needsBarrier)) {
            // Most likely (?): expect that no barrier is needed.
            return;
        }
        // The object needs a write-barrier. Is it aligned or unaligned?
        final boolean unaligned = ObjectHeaderImpl.isHeapObjectUnaligned(objectHeader);
        if (BranchProbabilityNode.probability(BranchProbabilityNode.LIKELY_PROBABILITY, !unaligned)) {
            // Next most likely (?): aligned objects.
            counters().postWriteBarrierAligned.inc();
            AlignedHeapChunk.dirtyCardForObjectOfAlignedHeapChunk(fixedObject);
            return;
        }
        // Least likely (?): object needs a write-barrier and is unaligned.
        counters().postWriteBarrierUnaligned.inc();
        UnalignedHeapChunk.dirtyCardForObjectOfUnalignedHeapChunk(fixedObject);
        return;
    }

    protected BarrierSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection) {
        super(options, factories, providers, snippetReflection);
    }

    /*
     * *Not* a static class: references fields of the containing BarrierSnippet instance.
     */
    protected class PostWriteBarrierLowering implements NodeLoweringProvider<PostWriteBarrierNode> {

        private final SnippetInfo postWriteBarrierSnippetInfo = snippet(BarrierSnippets.class, "postWriteBarrierSnippet", CardTable.CARD_REMEMBERED_SET_LOCATION);

        /**
         * Turn a CardRememberedSetNodes.PostWriteBarrierNode into an instantiation of the body of
         * CardRememberedSetSnippets.BarrierSnippets.postWriteBarrierSnippet, with operands.
         */
        @Override
        public void lower(PostWriteBarrierNode barrier, LoweringTool tool) {
            Arguments args = new Arguments(postWriteBarrierSnippetInfo, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            OffsetAddressNode address = (OffsetAddressNode) barrier.getAddress();
            args.add("object", address.getBase());
            template(barrier, args).instantiate(providers.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
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
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(CounterFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(BarrierSnippetCounters.class, new BarrierSnippetCounters());
    }
}
