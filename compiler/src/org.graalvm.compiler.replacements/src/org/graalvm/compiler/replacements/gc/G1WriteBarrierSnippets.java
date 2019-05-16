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

import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.extended.NullCheckNode;
import org.graalvm.compiler.nodes.gc.G1ArrayRangePostWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1ArrayRangePreWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1PostWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1PreWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1ReferentFieldReadBarrier;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode.Address;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.type.NarrowOopStamp;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.compiler.replacements.nodes.CStringConstant;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

public abstract class G1WriteBarrierSnippets extends WriteBarrierSnippets implements Snippets {

    public static final LocationIdentity GC_LOG_LOCATION = NamedLocationIdentity.mutable("GC-Log");
    public static final LocationIdentity GC_INDEX_LOCATION = NamedLocationIdentity.mutable("GC-Index");

    public static class Counters {
        Counters(SnippetCounter.Group.Factory factory) {
            Group countersWriteBarriers = factory.createSnippetCounterGroup("G1 WriteBarriers");
            g1AttemptedPreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1AttemptedPreWriteBarrier", "Number of attempted G1 Pre Write Barriers");
            g1EffectivePreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectivePreWriteBarrier", "Number of effective G1 Pre Write Barriers");
            g1ExecutedPreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1ExecutedPreWriteBarrier", "Number of executed G1 Pre Write Barriers");
            g1AttemptedPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1AttemptedPostWriteBarrier", "Number of attempted G1 Post Write Barriers");
            g1EffectiveAfterXORPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectiveAfterXORPostWriteBarrier",
                            "Number of effective G1 Post Write Barriers (after passing the XOR test)");
            g1EffectiveAfterNullPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectiveAfterNullPostWriteBarrier",
                            "Number of effective G1 Post Write Barriers (after passing the NULL test)");
            g1ExecutedPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1ExecutedPostWriteBarrier", "Number of executed G1 Post Write Barriers");
        }

        final SnippetCounter g1AttemptedPreWriteBarrierCounter;
        final SnippetCounter g1EffectivePreWriteBarrierCounter;
        final SnippetCounter g1ExecutedPreWriteBarrierCounter;
        final SnippetCounter g1AttemptedPostWriteBarrierCounter;
        final SnippetCounter g1EffectiveAfterXORPostWriteBarrierCounter;
        final SnippetCounter g1EffectiveAfterNullPostWriteBarrierCounter;
        final SnippetCounter g1ExecutedPostWriteBarrierCounter;
    }

    @Snippet
    public void g1PreWriteBarrier(Address address, Object object, Object expectedObject, @ConstantParameter boolean doLoad, @ConstantParameter boolean nullCheck,
                    @ConstantParameter int traceStartCycle, @ConstantParameter Counters counters) {
        if (nullCheck) {
            NullCheckNode.nullCheck(address);
        }
        Word thread = getThread();
        verifyOop(object);
        Object fixedExpectedObject = FixedValueAnchorNode.getObject(expectedObject);
        Word field = Word.fromAddress(address);
        Pointer previousOop = Word.objectToTrackedPointer(fixedExpectedObject);
        byte markingValue = thread.readByte(satbQueueMarkingOffset());

        boolean trace = isTracingActive(traceStartCycle);
        int gcCycle = 0;
        if (trace) {
            Pointer gcTotalCollectionsAddress = WordFactory.pointer(gcTotalCollectionsAddress());
            gcCycle = (int) gcTotalCollectionsAddress.readLong(0);
            log(trace, "[%d] G1-Pre Thread %p Object %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(object).rawValue());
            log(trace, "[%d] G1-Pre Thread %p Expected Object %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(fixedExpectedObject).rawValue());
            log(trace, "[%d] G1-Pre Thread %p Field %p\n", gcCycle, thread.rawValue(), field.rawValue());
            log(trace, "[%d] G1-Pre Thread %p Marking %d\n", gcCycle, thread.rawValue(), markingValue);
            log(trace, "[%d] G1-Pre Thread %p DoLoad %d\n", gcCycle, thread.rawValue(), doLoad ? 1L : 0L);
        }

        counters.g1AttemptedPreWriteBarrierCounter.inc();
        // If the concurrent marker is enabled, the barrier is issued.
        if (probability(NOT_FREQUENT_PROBABILITY, markingValue != (byte) 0)) {
            // If the previous value has to be loaded (before the write), the load is issued.
            // The load is always issued except the cases of CAS and referent field.
            if (probability(LIKELY_PROBABILITY, doLoad)) {
                previousOop = Word.objectToTrackedPointer(field.readObject(0, BarrierType.NONE));
                if (trace) {
                    log(trace, "[%d] G1-Pre Thread %p Previous Object %p\n ", gcCycle, thread.rawValue(), previousOop.rawValue());
                    verifyOop(previousOop.toObject());
                }
            }
            counters.g1EffectivePreWriteBarrierCounter.inc();
            // If the previous value is null the barrier should not be issued.
            if (probability(FREQUENT_PROBABILITY, previousOop.notEqual(0))) {
                counters.g1ExecutedPreWriteBarrierCounter.inc();
                // If the thread-local SATB buffer is full issue a native call which will
                // initialize a new one and add the entry.
                Word indexAddress = thread.add(satbQueueIndexOffset());
                Word indexValue = indexAddress.readWord(0);
                if (probability(FREQUENT_PROBABILITY, indexValue.notEqual(0))) {
                    Word bufferAddress = thread.readWord(satbQueueBufferOffset());
                    Word nextIndex = indexValue.subtract(wordSize());
                    Word logAddress = bufferAddress.add(nextIndex);
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    logAddress.writeWord(0, previousOop, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, nextIndex, GC_INDEX_LOCATION);
                } else {
                    g1PreBarrierStub(previousOop);
                }
            }
        }
    }

    @Snippet
    public void g1PostWriteBarrier(Address address, Object object, Object value, @ConstantParameter boolean usePrecise, @ConstantParameter int traceStartCycle,
                    @ConstantParameter Counters counters) {
        Word thread = getThread();
        Object fixedValue = FixedValueAnchorNode.getObject(value);
        verifyOop(object);
        verifyOop(fixedValue);
        validateObject(object, fixedValue);

        Pointer oop;
        if (usePrecise) {
            oop = Word.fromAddress(address);
        } else {
            if (verifyBarrier()) {
                verifyNotArray(object);
            }
            oop = Word.objectToTrackedPointer(object);
        }

        boolean trace = isTracingActive(traceStartCycle);
        int gcCycle = 0;
        if (trace) {
            Pointer gcTotalCollectionsAddress = WordFactory.pointer(gcTotalCollectionsAddress());
            gcCycle = (int) gcTotalCollectionsAddress.readLong(0);
            log(trace, "[%d] G1-Post Thread: %p Object: %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(object).rawValue());
            log(trace, "[%d] G1-Post Thread: %p Field: %p\n", gcCycle, thread.rawValue(), oop.rawValue());
        }
        Pointer writtenValue = Word.objectToTrackedPointer(fixedValue);
        // The result of the xor reveals whether the installed pointer crosses heap regions.
        // In case it does the write barrier has to be issued.
        final int logOfHeapRegionGrainBytes = logOfHeapRegionGrainBytes();
        UnsignedWord xorResult = (oop.xor(writtenValue)).unsignedShiftRight(logOfHeapRegionGrainBytes);

        counters.g1AttemptedPostWriteBarrierCounter.inc();
        if (probability(FREQUENT_PROBABILITY, xorResult.notEqual(0))) {
            counters.g1EffectiveAfterXORPostWriteBarrierCounter.inc();
            // If the written value is not null continue with the barrier addition.
            if (probability(FREQUENT_PROBABILITY, writtenValue.notEqual(0))) {
                // Calculate the address of the card to be enqueued to the
                // thread local card queue.
                UnsignedWord cardBase = oop.unsignedShiftRight(cardTableShift());
                final long startAddress = cardTableAddress();
                int displacement = 0;
                if (((int) startAddress) == startAddress && isCardTableAddressConstant()) {
                    displacement = (int) startAddress;
                } else {
                    cardBase = cardBase.add(WordFactory.unsigned(startAddress));
                }
                Word cardAddress = (Word) cardBase.add(displacement);

                byte cardByte = cardAddress.readByte(0, GC_CARD_LOCATION);
                counters.g1EffectiveAfterNullPostWriteBarrierCounter.inc();

                // If the card is already dirty, (hence already enqueued) skip the insertion.
                if (probability(NOT_FREQUENT_PROBABILITY, cardByte != youngCardValue())) {
                    MembarNode.memoryBarrier(STORE_LOAD, GC_CARD_LOCATION);
                    byte cardByteReload = cardAddress.readByte(0, GC_CARD_LOCATION);
                    if (probability(NOT_FREQUENT_PROBABILITY, cardByteReload != dirtyCardValue())) {
                        log(trace, "[%d] G1-Post Thread: %p Card: %p \n", gcCycle, thread.rawValue(), WordFactory.unsigned((int) cardByte).rawValue());
                        cardAddress.writeByte(0, (byte) 0, GC_CARD_LOCATION);
                        counters.g1ExecutedPostWriteBarrierCounter.inc();

                        // If the thread local card queue is full, issue a native call which will
                        // initialize a new one and add the card entry.
                        Word indexValue = thread.readWord(cardQueueIndexOffset());
                        if (probability(FREQUENT_PROBABILITY, indexValue.notEqual(0))) {
                            Word bufferAddress = thread.readWord(cardQueueBufferOffset());
                            Word nextIndex = indexValue.subtract(wordSize());
                            Word logAddress = bufferAddress.add(nextIndex);
                            Word indexAddress = thread.add(cardQueueIndexOffset());
                            // Log the object to be scanned as well as update
                            // the card queue's next index.
                            logAddress.writeWord(0, cardAddress, GC_LOG_LOCATION);
                            indexAddress.writeWord(0, nextIndex, GC_INDEX_LOCATION);
                        } else {
                            g1PostBarrierStub(cardAddress);
                        }
                    }
                }
            }
        }
    }

    @Snippet
    public void g1ArrayRangePreWriteBarrier(Address address, int length, @ConstantParameter int elementStride) {
        Word thread = getThread();
        byte markingValue = thread.readByte(satbQueueMarkingOffset());
        // If the concurrent marker is not enabled or the vector length is zero, return.
        if (markingValue == (byte) 0 || length == 0) {
            return;
        }
        Word bufferAddress = thread.readWord(satbQueueBufferOffset());
        Word indexAddress = thread.add(satbQueueIndexOffset());
        long indexValue = indexAddress.readWord(0).rawValue();
        final int scale = objectArrayIndexScale();
        long start = getPointerToFirstArrayElement(address, length, elementStride);

        for (int i = 0; i < length; i++) {
            Word arrElemPtr = WordFactory.pointer(start + i * scale);
            Pointer oop = Word.objectToTrackedPointer(arrElemPtr.readObject(0, BarrierType.NONE));
            verifyOop(oop.toObject());
            if (oop.notEqual(0)) {
                if (indexValue != 0) {
                    indexValue = indexValue - wordSize();
                    Word logAddress = bufferAddress.add(WordFactory.unsigned(indexValue));
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    logAddress.writeWord(0, oop, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, WordFactory.unsigned(indexValue), GC_INDEX_LOCATION);
                } else {
                    g1PreBarrierStub(oop);
                }
            }
        }
    }

    @Snippet
    public void g1ArrayRangePostWriteBarrier(Address address, int length, @ConstantParameter int elementStride) {
        if (length == 0) {
            return;
        }
        Word thread = getThread();
        Word bufferAddress = thread.readWord(cardQueueBufferOffset());
        Word indexAddress = thread.add(cardQueueIndexOffset());
        long indexValue = thread.readWord(cardQueueIndexOffset()).rawValue();

        int cardShift = cardTableShift();
        final long cardStart = cardTableAddress();
        long start = getPointerToFirstArrayElement(address, length, elementStride) >>> cardShift;
        long end = getPointerToLastArrayElement(address, length, elementStride) >>> cardShift;
        long count = end - start + 1;

        while (count-- > 0) {
            Word cardAddress = WordFactory.unsigned((start + cardStart) + count);
            byte cardByte = cardAddress.readByte(0, GC_CARD_LOCATION);
            // If the card is already dirty, (hence already enqueued) skip the insertion.
            if (probability(NOT_FREQUENT_PROBABILITY, cardByte != youngCardValue())) {
                MembarNode.memoryBarrier(STORE_LOAD, GC_CARD_LOCATION);
                byte cardByteReload = cardAddress.readByte(0, GC_CARD_LOCATION);
                if (probability(NOT_FREQUENT_PROBABILITY, cardByteReload != dirtyCardValue())) {
                    cardAddress.writeByte(0, (byte) 0, GC_CARD_LOCATION);
                    // If the thread local card queue is full, issue a native call which will
                    // initialize a new one and add the card entry.
                    if (indexValue != 0) {
                        indexValue = indexValue - wordSize();
                        Word logAddress = bufferAddress.add(WordFactory.unsigned(indexValue));
                        // Log the object to be scanned as well as update
                        // the card queue's next index.
                        logAddress.writeWord(0, cardAddress, GC_LOG_LOCATION);
                        indexAddress.writeWord(0, WordFactory.unsigned(indexValue), GC_INDEX_LOCATION);
                    } else {
                        g1PostBarrierStub(cardAddress);
                    }
                }
            }
        }
    }

    protected abstract Word getThread();

    protected abstract int wordSize();

    protected abstract int objectArrayIndexScale();

    protected abstract int satbQueueMarkingOffset();

    protected abstract int satbQueueBufferOffset();

    protected abstract int satbQueueIndexOffset();

    protected abstract int cardQueueBufferOffset();

    protected abstract int cardQueueIndexOffset();

    protected abstract byte dirtyCardValue();

    protected abstract byte youngCardValue();

    protected abstract long cardTableAddress();

    protected abstract boolean isCardTableAddressConstant();

    protected abstract int cardTableShift();

    protected abstract int logOfHeapRegionGrainBytes();

    protected abstract ForeignCallDescriptor preWriteBarrierCallDescriptor();

    protected abstract ForeignCallDescriptor postWriteBarrierCallDescriptor();

    // the data below is only needed for the verification logic
    protected abstract boolean verifyOops();

    protected abstract boolean verifyBarrier();

    protected abstract long gcTotalCollectionsAddress();

    protected abstract ForeignCallDescriptor verifyOopCallDescriptor();

    protected abstract ForeignCallDescriptor validateObjectCallDescriptor();

    protected abstract ForeignCallDescriptor printfCallDescriptor();

    private boolean isTracingActive(int traceStartCycle) {
        return traceStartCycle > 0 && ((Pointer) WordFactory.pointer(gcTotalCollectionsAddress())).readLong(0) > traceStartCycle;
    }

    private void log(boolean enabled, String format, long value1, long value2, long value3) {
        if (enabled) {
            printf(printfCallDescriptor(), CStringConstant.cstring(format), value1, value2, value3);
        }
    }

    /**
     * Validation helper method which performs sanity checks on write operations. The addresses of
     * both the object and the value being written are checked in order to determine if they reside
     * in a valid heap region. If an object is stale, an invalid access is performed in order to
     * prematurely crash the VM and debug the stack trace of the faulty method.
     */
    private void validateObject(Object parent, Object child) {
        if (verifyOops() && child != null) {
            Word parentWord = Word.objectToTrackedPointer(parent);
            Word childWord = Word.objectToTrackedPointer(child);
            boolean success = validateOop(validateObjectCallDescriptor(), parentWord, childWord);
            AssertionNode.assertion(false, success, "Verification ERROR, Parent: %p Child: %p\n", parentWord.rawValue(), childWord.rawValue());
        }
    }

    private void verifyOop(Object object) {
        if (verifyOops()) {
            verifyOopStub(verifyOopCallDescriptor(), object);
        }
    }

    private void g1PreBarrierStub(Pointer previousOop) {
        g1PreBarrierStub(preWriteBarrierCallDescriptor(), previousOop.toObject());
    }

    private void g1PostBarrierStub(Word cardAddress) {
        g1PostBarrierStub(postWriteBarrierCallDescriptor(), cardAddress);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native Object verifyOopStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native boolean validateOop(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word parent, Word object);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void g1PreBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void g1PostBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word card);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void printf(@ConstantNodeParameter ForeignCallDescriptor logPrintf, Word format, long v1, long v2, long v3);

    public abstract static class G1WriteBarrierLowerer {
        private final Counters counters;

        public G1WriteBarrierLowerer(Group.Factory factory) {
            this.counters = new Counters(factory);
        }

        public void lower(AbstractTemplates templates, SnippetInfo snippet, G1PreWriteBarrier barrier, LoweringTool tool) {
            Arguments args = new Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            AddressNode address = barrier.getAddress();
            args.add("address", address);
            if (address instanceof OffsetAddressNode) {
                args.add("object", ((OffsetAddressNode) address).getBase());
            } else {
                args.add("object", null);
            }

            ValueNode expected = barrier.getExpectedObject();
            if (expected != null && expected.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp) {
                expected = uncompress(expected);
            }
            args.add("expectedObject", expected);

            args.addConst("doLoad", barrier.doLoad());
            args.addConst("nullCheck", barrier.getNullCheck());
            args.addConst("traceStartCycle", traceStartCycle(barrier.graph()));
            args.addConst("counters", counters);

            templates.template(barrier, args).instantiate(templates.getProviders().getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        public void lower(AbstractTemplates templates, SnippetInfo snippet, G1ReferentFieldReadBarrier barrier, LoweringTool tool) {
            Arguments args = new Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            AddressNode address = barrier.getAddress();
            args.add("address", address);
            if (address instanceof OffsetAddressNode) {
                args.add("object", ((OffsetAddressNode) address).getBase());
            } else {
                args.add("object", null);
            }

            ValueNode expected = barrier.getExpectedObject();
            if (expected != null && expected.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp) {
                expected = uncompress(expected);
            }

            args.add("expectedObject", expected);
            args.addConst("doLoad", barrier.doLoad());
            args.addConst("nullCheck", false);
            args.addConst("traceStartCycle", traceStartCycle(barrier.graph()));
            args.addConst("counters", counters);

            templates.template(barrier, args).instantiate(templates.getProviders().getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        public void lower(AbstractTemplates templates, SnippetInfo snippet, G1PostWriteBarrier barrier, LoweringTool tool) {
            if (barrier.alwaysNull()) {
                barrier.graph().removeFixed(barrier);
                return;
            }

            Arguments args = new Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            AddressNode address = barrier.getAddress();
            args.add("address", address);
            if (address instanceof OffsetAddressNode) {
                args.add("object", ((OffsetAddressNode) address).getBase());
            } else {
                assert barrier.usePrecise() : "found imprecise barrier that's not an object access " + barrier;
                args.add("object", null);
            }

            ValueNode value = barrier.getValue();
            if (value.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp) {
                value = uncompress(value);
            }
            args.add("value", value);

            args.addConst("usePrecise", barrier.usePrecise());
            args.addConst("traceStartCycle", traceStartCycle(barrier.graph()));
            args.addConst("counters", counters);

            templates.template(barrier, args).instantiate(templates.getProviders().getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        public void lower(AbstractTemplates templates, SnippetInfo snippet, G1ArrayRangePreWriteBarrier barrier, LoweringTool tool) {
            Arguments args = new Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("address", barrier.getAddress());
            args.add("length", barrier.getLength());
            args.addConst("elementStride", barrier.getElementStride());

            templates.template(barrier, args).instantiate(templates.getProviders().getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        public void lower(AbstractTemplates templates, SnippetInfo snippet, G1ArrayRangePostWriteBarrier barrier, LoweringTool tool) {
            Arguments args = new Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("address", barrier.getAddress());
            args.add("length", barrier.getLength());
            args.addConst("elementStride", barrier.getElementStride());

            templates.template(barrier, args).instantiate(templates.getProviders().getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        private static int traceStartCycle(StructuredGraph graph) {
            return GraalOptions.GCDebugStartCycle.getValue(graph.getOptions());
        }

        protected abstract ValueNode uncompress(ValueNode value);
    }
}
