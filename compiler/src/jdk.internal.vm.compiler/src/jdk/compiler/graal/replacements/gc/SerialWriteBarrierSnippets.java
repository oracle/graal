/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.replacements.gc;

import static jdk.compiler.graal.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.compiler.graal.nodes.extended.BranchProbabilityNode.probability;

import jdk.compiler.graal.api.directives.GraalDirectives;
import jdk.compiler.graal.api.replacements.Snippet;
import jdk.compiler.graal.api.replacements.Snippet.ConstantParameter;
import jdk.compiler.graal.nodes.gc.SerialArrayRangeWriteBarrier;
import jdk.compiler.graal.nodes.gc.SerialWriteBarrier;
import jdk.compiler.graal.nodes.memory.address.AddressNode.Address;
import jdk.compiler.graal.nodes.memory.address.OffsetAddressNode;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.replacements.SnippetCounter;
import jdk.compiler.graal.replacements.SnippetTemplate;
import jdk.compiler.graal.replacements.Snippets;
import jdk.compiler.graal.replacements.nodes.AssertionNode;
import jdk.compiler.graal.word.Word;
import org.graalvm.word.Pointer;

public abstract class SerialWriteBarrierSnippets extends WriteBarrierSnippets implements Snippets {
    static class Counters {
        Counters(SnippetCounter.Group.Factory factory) {
            SnippetCounter.Group countersWriteBarriers = factory.createSnippetCounterGroup("Serial WriteBarriers");
            serialWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialWriteBarrier", "Number of Serial Write Barriers");
        }

        final SnippetCounter serialWriteBarrierCounter;
    }

    @Snippet
    public void serialImpreciseWriteBarrier(Object object, @ConstantParameter Counters counters, @ConstantParameter boolean verifyOnly) {
        if (verifyBarrier()) {
            verifyNotArray(object);
        }
        serialWriteBarrier(Word.objectToTrackedPointer(object), counters, verifyOnly);
    }

    @Snippet
    public void serialPreciseWriteBarrier(Address address, @ConstantParameter Counters counters, @ConstantParameter boolean verifyOnly) {
        serialWriteBarrier(Word.fromAddress(address), counters, verifyOnly);
    }

    @Snippet
    public void serialArrayRangeWriteBarrier(Address address, long length, @ConstantParameter int elementStride) {
        if (probability(NOT_FREQUENT_PROBABILITY, length == 0)) {
            return;
        }

        int cardShift = cardTableShift();
        Word cardTableAddress = cardTableAddress();
        Word start = cardTableAddress.add(getPointerToFirstArrayElement(address, length, elementStride).unsignedShiftRight(cardShift));
        Word end = cardTableAddress.add(getPointerToLastArrayElement(address, length, elementStride).unsignedShiftRight(cardShift));

        Word cur = start;
        do {
            cur.writeByte(0, dirtyCardValue(), GC_CARD_LOCATION);
            cur = cur.add(1);
        } while (GraalDirectives.injectIterationCount(10, cur.belowOrEqual(end)));
    }

    private void serialWriteBarrier(Pointer ptr, Counters counters, boolean verifyOnly) {
        if (!verifyOnly) {
            counters.serialWriteBarrierCounter.inc();
        }

        Word base = cardTableAddress().add(ptr.unsignedShiftRight(cardTableShift()));
        if (verifyOnly) {
            byte cardValue = base.readByte(0, GC_CARD_LOCATION);
            AssertionNode.dynamicAssert(cardValue == dirtyCardValue(), "card must be dirty");
        } else {
            base.writeByte(0, dirtyCardValue(), GC_CARD_LOCATION);
        }
    }

    protected abstract Word cardTableAddress();

    protected abstract int cardTableShift();

    protected abstract boolean verifyBarrier();

    protected abstract byte dirtyCardValue();

    public static class SerialWriteBarrierLowerer {
        private final Counters counters;

        public SerialWriteBarrierLowerer(SnippetCounter.Group.Factory factory) {
            this.counters = new Counters(factory);
        }

        public void lower(SnippetTemplate.AbstractTemplates templates, SnippetTemplate.SnippetInfo preciseSnippet, SnippetTemplate.SnippetInfo impreciseSnippet, SerialWriteBarrier barrier,
                        LoweringTool tool) {
            SnippetTemplate.Arguments args;
            if (barrier.usePrecise()) {
                args = new SnippetTemplate.Arguments(preciseSnippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("address", barrier.getAddress());
            } else {
                args = new SnippetTemplate.Arguments(impreciseSnippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
                OffsetAddressNode address = (OffsetAddressNode) barrier.getAddress();
                args.add("object", address.getBase());
            }
            args.addConst("counters", counters);
            args.addConst("verifyOnly", barrier.getVerifyOnly());

            templates.template(tool, barrier, args).instantiate(tool.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        public void lower(SnippetTemplate.AbstractTemplates templates, SnippetTemplate.SnippetInfo snippet, SerialArrayRangeWriteBarrier barrier, LoweringTool tool) {
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("address", barrier.getAddress());
            args.add("length", barrier.getLengthAsLong());
            args.addConst("elementStride", barrier.getElementStride());

            templates.template(tool, barrier, args).instantiate(tool.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
