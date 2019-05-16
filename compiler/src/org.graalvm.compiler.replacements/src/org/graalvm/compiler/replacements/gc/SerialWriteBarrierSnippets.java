/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.gc;

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.nodes.gc.SerialArrayRangeWriteBarrier;
import org.graalvm.compiler.nodes.gc.SerialWriteBarrier;
import org.graalvm.compiler.nodes.memory.address.AddressNode.Address;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.DirectStoreNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;

public abstract class SerialWriteBarrierSnippets extends WriteBarrierSnippets implements Snippets {

    static class Counters {
        Counters(SnippetCounter.Group.Factory factory) {
            Group countersWriteBarriers = factory.createSnippetCounterGroup("Serial WriteBarriers");
            serialWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialWriteBarrier", "Number of Serial Write Barriers");
        }

        final SnippetCounter serialWriteBarrierCounter;
    }

    @Snippet
    public void serialImpreciseWriteBarrier(Object object, @ConstantParameter Counters counters) {
        if (verifyBarrier()) {
            verifyNotArray(object);
        }
        serialWriteBarrier(Word.objectToTrackedPointer(object), counters);
    }

    @Snippet
    public void serialPreciseWriteBarrier(Address address, @ConstantParameter Counters counters) {
        serialWriteBarrier(Word.fromAddress(address), counters);
    }

    @Snippet
    public void serialArrayRangeWriteBarrier(Address address, int length, @ConstantParameter int elementStride) {
        if (length == 0) {
            return;
        }
        int cardShift = cardTableShift();
        final long cardStart = cardTableAddress();
        long start = getPointerToFirstArrayElement(address, length, elementStride) >>> cardShift;
        long end = getPointerToLastArrayElement(address, length, elementStride) >>> cardShift;
        long count = end - start + 1;
        while (count-- > 0) {
            DirectStoreNode.storeBoolean((start + cardStart) + count, false, JavaKind.Boolean);
        }
    }

    private void serialWriteBarrier(Pointer ptr, Counters counters) {
        counters.serialWriteBarrierCounter.inc();
        final long startAddress = cardTableAddress();
        Word base = (Word) ptr.unsignedShiftRight(cardTableShift());
        if (((int) startAddress) == startAddress && isCardTableAddressConstant()) {
            base.writeByte((int) startAddress, (byte) 0, GC_CARD_LOCATION);
        } else {
            base.writeByte(WordFactory.unsigned(startAddress), (byte) 0, GC_CARD_LOCATION);
        }
    }

    protected abstract boolean isCardTableAddressConstant();

    protected abstract long cardTableAddress();

    protected abstract int cardTableShift();

    protected abstract boolean verifyBarrier();

    public static class SerialWriteBarrierLowerer {
        private final Counters counters;

        public SerialWriteBarrierLowerer(Group.Factory factory) {
            this.counters = new Counters(factory);
        }

        public void lower(AbstractTemplates templates, SnippetInfo preciseSnippet, SnippetInfo impreciseSnippet, SerialWriteBarrier barrier, LoweringTool tool) {
            Arguments args;
            if (barrier.usePrecise()) {
                args = new Arguments(preciseSnippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("address", barrier.getAddress());
            } else {
                args = new Arguments(impreciseSnippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
                OffsetAddressNode address = (OffsetAddressNode) barrier.getAddress();
                args.add("object", address.getBase());
            }
            args.addConst("counters", counters);

            templates.template(barrier, args).instantiate(templates.getProviders().getMetaAccess(), barrier, DEFAULT_REPLACER, args);
        }

        public void lower(AbstractTemplates templates, SnippetInfo snippet, SerialArrayRangeWriteBarrier barrier, LoweringTool tool) {
            Arguments args = new Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("address", barrier.getAddress());
            args.add("length", barrier.getLength());
            args.addConst("elementStride", barrier.getElementStride());

            templates.template(barrier, args).instantiate(templates.getProviders().getMetaAccess(), barrier, DEFAULT_REPLACER, args);
        }
    }
}
