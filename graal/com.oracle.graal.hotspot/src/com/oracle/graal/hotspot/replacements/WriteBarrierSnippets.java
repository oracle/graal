/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

public class WriteBarrierSnippets implements Snippets {

    private static final SnippetCounter.Group countersWriteBarriers = SnippetCounters.getValue() ? new SnippetCounter.Group("WriteBarriers") : null;
    private static final SnippetCounter serialWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialWriteBarrier", "Number of Serial Write Barriers");
    private static final SnippetCounter g1AttemptedPreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1AttemptedPreWriteBarrier", "Number of G1 attempted Pre Write Barriers");
    private static final SnippetCounter g1EffectivePreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectivePreWriteBarrier", "Number of G1 effective Pre Write Barriers");
    private static final SnippetCounter g1ExecutedPreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1ExecutedPreWriteBarrier", "Number of G1 executed Pre Write Barriers");
    private static final SnippetCounter g1AttemptedPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1AttemptedPostWriteBarrier", "Number of attempted G1 Post Write Barriers");
    private static final SnippetCounter g1EffectiveAfterXORPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectiveAfterXORPostWriteBarrier",
                    "Number of effective G1 Post Write Barriers (after passing the XOR test)");
    private static final SnippetCounter g1EffectiveAfterNullPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectiveAfterNullPostWriteBarrier",
                    "Number of effective G1 Post Write Barriers (after passing the NULL test)");
    private static final SnippetCounter g1ExecutedPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1ExecutedPostWriteBarrier", "Number of executed G1 Post Write Barriers");

    public static final LocationIdentity GC_CARD_LOCATION = new NamedLocationIdentity("GC-Card");
    public static final LocationIdentity GC_LOG_LOCATION = new NamedLocationIdentity("GC-Log");
    public static final LocationIdentity GC_INDEX_LOCATION = new NamedLocationIdentity("GC-Index");

    @Snippet
    public static void serialWriteBarrier(Object object, Object location, @ConstantParameter boolean usePrecise) {
        Object fixedObject = FixedValueAnchorNode.getObject(object);
        Pointer oop;
        if (usePrecise) {
            oop = Word.fromArray(fixedObject, SnippetLocationProxyNode.location(location));
        } else {
            oop = Word.fromObject(fixedObject);
        }
        serialWriteBarrierCounter.inc();
        Word base = (Word) oop.unsignedShiftRight(cardTableShift());
        long startAddress = cardTableStart();
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            base = base.add(Word.unsigned(cardTableStart()));
        }
        base.writeByte(displacement, (byte) 0, GC_CARD_LOCATION);
    }

    @Snippet
    public static void serialArrayRangeWriteBarrier(Object object, int startIndex, int length) {
        if (length == 0) {
            return;
        }
        Object dest = FixedValueAnchorNode.getObject(object);
        int cardShift = cardTableShift();
        long cardStart = cardTableStart();
        final int scale = arrayIndexScale(Kind.Object);
        int header = arrayBaseOffset(Kind.Object);
        long dstAddr = GetObjectAddressNode.get(dest);
        long start = (dstAddr + header + (long) startIndex * scale) >>> cardShift;
        long end = (dstAddr + header + ((long) startIndex + length - 1) * scale) >>> cardShift;
        long count = end - start + 1;
        while (count-- > 0) {
            DirectStoreNode.store((start + cardStart) + count, false, Kind.Boolean);
        }
    }

    @Snippet
    public static void g1PreWriteBarrier(Object object, Object expectedObject, Object location, @ConstantParameter boolean doLoad, @ConstantParameter boolean nullCheck,
                    @ConstantParameter Register threadRegister, @ConstantParameter boolean trace) {
        if (nullCheck && object == null) {
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
        }
        Word thread = registerAsWord(threadRegister);
        Object fixedObject = FixedValueAnchorNode.getObject(object);
        verifyOop(fixedObject);
        Object fixedExpectedObject = FixedValueAnchorNode.getObject(expectedObject);
        Word field = (Word) Word.fromArray(fixedObject, SnippetLocationProxyNode.location(location));
        Word previousOop = (Word) Word.fromObject(fixedExpectedObject);
        byte markingValue = thread.readByte(g1SATBQueueMarkingOffset());
        Word bufferAddress = thread.readWord(g1SATBQueueBufferOffset());
        Word indexAddress = thread.add(g1SATBQueueIndexOffset());
        Word indexValue = indexAddress.readWord(0);
        int gcCycle = 0;
        if (trace) {
            gcCycle = (int) Word.unsigned(HotSpotReplacementsUtil.gcTotalCollectionsAddress()).readLong(0);
            log(trace, "[%d] G1-Pre Thread %p Object %p\n", gcCycle, thread.rawValue(), Word.fromObject(fixedObject).rawValue());
            log(trace, "[%d] G1-Pre Thread %p Expected Object %p\n", gcCycle, thread.rawValue(), Word.fromObject(fixedExpectedObject).rawValue());
            log(trace, "[%d] G1-Pre Thread %p Field %p\n", gcCycle, thread.rawValue(), field.rawValue());
            log(trace, "[%d] G1-Pre Thread %p Marking %d\n", gcCycle, thread.rawValue(), markingValue);
            log(trace, "[%d] G1-Pre Thread %p DoLoad %d\n", gcCycle, thread.rawValue(), doLoad ? 1L : 0L);
        }
        g1AttemptedPreWriteBarrierCounter.inc();
        // If the concurrent marker is enabled, the barrier is issued.
        if (probability(NOT_FREQUENT_PROBABILITY, markingValue != (byte) 0)) {
            // If the previous value has to be loaded (before the write), the load is issued.
            // The load is always issued except the cases of CAS and referent field.
            if (probability(LIKELY_PROBABILITY, doLoad)) {
                previousOop = (Word) Word.fromObject(field.readObject(0, BarrierType.NONE));
                if (trace) {
                    log(trace, "[%d] G1-Pre Thread %p Previous Object %p\n ", gcCycle, thread.rawValue(), previousOop.rawValue());
                    verifyOop(previousOop.toObject());
                }
            }
            g1EffectivePreWriteBarrierCounter.inc();
            // If the previous value is null the barrier should not be issued.
            if (probability(FREQUENT_PROBABILITY, previousOop.notEqual(0))) {
                g1ExecutedPreWriteBarrierCounter.inc();
                // If the thread-local SATB buffer is full issue a native call which will
                // initialize a new one and add the entry.
                if (probability(FREQUENT_PROBABILITY, indexValue.notEqual(0))) {
                    Word nextIndex = indexValue.subtract(wordSize());
                    Word logAddress = bufferAddress.add(nextIndex);
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    logAddress.writeWord(0, previousOop, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, nextIndex, GC_INDEX_LOCATION);
                } else {
                    g1PreBarrierStub(G1WBPRECALL, previousOop.toObject());
                }
            }
        }
    }

    @Snippet
    public static void g1PostWriteBarrier(Object object, Object value, Object location, @ConstantParameter boolean usePrecise, @ConstantParameter Register threadRegister,
                    @ConstantParameter boolean trace) {
        Word thread = registerAsWord(threadRegister);
        Object fixedObject = FixedValueAnchorNode.getObject(object);
        Object fixedValue = FixedValueAnchorNode.getObject(value);
        verifyOop(fixedObject);
        verifyOop(fixedValue);
        validateObject(fixedObject, fixedValue);
        Word oop;
        if (usePrecise) {
            oop = (Word) Word.fromArray(fixedObject, SnippetLocationProxyNode.location(location));
        } else {
            oop = (Word) Word.fromObject(fixedObject);
        }
        int gcCycle = 0;
        if (trace) {
            gcCycle = (int) Word.unsigned(HotSpotReplacementsUtil.gcTotalCollectionsAddress()).readLong(0);
            log(trace, "[%d] G1-Post Thread: %p Object: %p\n", gcCycle, thread.rawValue(), Word.fromObject(fixedObject).rawValue());
            log(trace, "[%d] G1-Post Thread: %p Field: %p\n", gcCycle, thread.rawValue(), oop.rawValue());
        }
        Word writtenValue = (Word) Word.fromObject(fixedValue);
        Word bufferAddress = thread.readWord(g1CardQueueBufferOffset());
        Word indexAddress = thread.add(g1CardQueueIndexOffset());
        Word indexValue = thread.readWord(g1CardQueueIndexOffset());
        // The result of the xor reveals whether the installed pointer crosses heap regions.
        // In case it does the write barrier has to be issued.
        Word xorResult = (oop.xor(writtenValue)).unsignedShiftRight(logOfHeapRegionGrainBytes());

        // Calculate the address of the card to be enqueued to the
        // thread local card queue.
        Word cardBase = oop.unsignedShiftRight(cardTableShift());
        long startAddress = cardTableStart();
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            cardBase = cardBase.add(Word.unsigned(cardTableStart()));
        }
        Word cardAddress = cardBase.add(displacement);

        g1AttemptedPostWriteBarrierCounter.inc();
        if (probability(FREQUENT_PROBABILITY, xorResult.notEqual(0))) {
            g1EffectiveAfterXORPostWriteBarrierCounter.inc();

            // If the written value is not null continue with the barrier addition.
            if (probability(FREQUENT_PROBABILITY, writtenValue.notEqual(0))) {
                byte cardByte = cardAddress.readByte(0);
                g1EffectiveAfterNullPostWriteBarrierCounter.inc();

                // If the card is already dirty, (hence already enqueued) skip the insertion.
                if (probability(NOT_FREQUENT_PROBABILITY, cardByte != (byte) 0)) {
                    log(trace, "[%d] G1-Post Thread: %p Card: %p \n", gcCycle, thread.rawValue(), Word.unsigned(cardByte).rawValue());
                    cardAddress.writeByte(0, (byte) 0, GC_CARD_LOCATION);
                    g1ExecutedPostWriteBarrierCounter.inc();

                    // If the thread local card queue is full, issue a native call which will
                    // initialize a new one and add the card entry.
                    if (probability(FREQUENT_PROBABILITY, indexValue.notEqual(0))) {
                        Word nextIndex = indexValue.subtract(wordSize());
                        Word logAddress = bufferAddress.add(nextIndex);
                        // Log the object to be scanned as well as update
                        // the card queue's next index.
                        logAddress.writeWord(0, cardAddress, GC_LOG_LOCATION);
                        indexAddress.writeWord(0, nextIndex, GC_INDEX_LOCATION);
                    } else {
                        g1PostBarrierStub(G1WBPOSTCALL, cardAddress);
                    }
                }
            }
        }
    }

    @Snippet
    public static void g1ArrayRangePreWriteBarrier(Object object, int startIndex, int length, @ConstantParameter Register threadRegister) {
        Word thread = registerAsWord(threadRegister);
        byte markingValue = thread.readByte(g1SATBQueueMarkingOffset());
        // If the concurrent marker is not enabled or the vector length is zero, return.
        if (markingValue == (byte) 0 || length == 0) {
            return;
        }
        Object dest = FixedValueAnchorNode.getObject(object);
        Word bufferAddress = thread.readWord(g1SATBQueueBufferOffset());
        Word indexAddress = thread.add(g1SATBQueueIndexOffset());
        long dstAddr = GetObjectAddressNode.get(dest);
        long indexValue = indexAddress.readWord(0).rawValue();
        final int scale = arrayIndexScale(Kind.Object);
        int header = arrayBaseOffset(Kind.Object);

        for (int i = startIndex; i < length; i++) {
            long address = dstAddr + header + (i * scale);
            Pointer oop = Word.fromObject(Word.unsigned(address).readObject(0, BarrierType.NONE));
            verifyOop(oop.toObject());
            if (oop.notEqual(0)) {
                if (indexValue != 0) {
                    indexValue = indexValue - wordSize();
                    Word logAddress = bufferAddress.add(Word.unsigned(indexValue));
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    logAddress.writeWord(0, oop, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, Word.unsigned(indexValue), GC_INDEX_LOCATION);
                } else {
                    g1PreBarrierStub(G1WBPRECALL, oop.toObject());
                }
            }
        }
    }

    @Snippet
    public static void g1ArrayRangePostWriteBarrier(Object object, int startIndex, int length, @ConstantParameter Register threadRegister) {
        if (length == 0) {
            return;
        }
        Object dest = FixedValueAnchorNode.getObject(object);
        Word thread = registerAsWord(threadRegister);
        Word bufferAddress = thread.readWord(g1CardQueueBufferOffset());
        Word indexAddress = thread.add(g1CardQueueIndexOffset());
        long indexValue = thread.readWord(g1CardQueueIndexOffset()).rawValue();

        int cardShift = cardTableShift();
        long cardStart = cardTableStart();
        final int scale = arrayIndexScale(Kind.Object);
        int header = arrayBaseOffset(Kind.Object);
        long dstAddr = GetObjectAddressNode.get(dest);
        long start = (dstAddr + header + (long) startIndex * scale) >>> cardShift;
        long end = (dstAddr + header + ((long) startIndex + length - 1) * scale) >>> cardShift;
        long count = end - start + 1;

        while (count-- > 0) {
            Word cardAddress = Word.unsigned((start + cardStart) + count);
            byte cardByte = cardAddress.readByte(0);
            // If the card is already dirty, (hence already enqueued) skip the insertion.
            if (cardByte != (byte) 0) {
                cardAddress.writeByte(0, (byte) 0, GC_CARD_LOCATION);
                // If the thread local card queue is full, issue a native call which will
                // initialize a new one and add the card entry.
                if (indexValue != 0) {
                    indexValue = indexValue - wordSize();
                    Word logAddress = bufferAddress.add(Word.unsigned(indexValue));
                    // Log the object to be scanned as well as update
                    // the card queue's next index.
                    logAddress.writeWord(0, cardAddress, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, Word.unsigned(indexValue), GC_INDEX_LOCATION);
                } else {
                    g1PostBarrierStub(G1WBPOSTCALL, cardAddress);
                }
            }
        }
    }

    public static final ForeignCallDescriptor G1WBPRECALL = new ForeignCallDescriptor("write_barrier_pre", void.class, Object.class);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void g1PreBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    public static final ForeignCallDescriptor G1WBPOSTCALL = new ForeignCallDescriptor("write_barrier_post", void.class, Word.class);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void g1PostBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word card);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo serialWriteBarrier = snippet(WriteBarrierSnippets.class, "serialWriteBarrier");
        private final SnippetInfo serialArrayRangeWriteBarrier = snippet(WriteBarrierSnippets.class, "serialArrayRangeWriteBarrier");
        private final SnippetInfo g1PreWriteBarrier = snippet(WriteBarrierSnippets.class, "g1PreWriteBarrier");
        private final SnippetInfo g1ReferentReadBarrier = snippet(WriteBarrierSnippets.class, "g1PreWriteBarrier");
        private final SnippetInfo g1PostWriteBarrier = snippet(WriteBarrierSnippets.class, "g1PostWriteBarrier");
        private final SnippetInfo g1ArrayRangePreWriteBarrier = snippet(WriteBarrierSnippets.class, "g1ArrayRangePreWriteBarrier");
        private final SnippetInfo g1ArrayRangePostWriteBarrier = snippet(WriteBarrierSnippets.class, "g1ArrayRangePostWriteBarrier");

        private final CompressEncoding oopEncoding;

        public Templates(HotSpotProviders providers, TargetDescription target, CompressEncoding oopEncoding) {
            super(providers, providers.getSnippetReflection(), target);
            this.oopEncoding = oopEncoding;
        }

        public void lower(SerialWriteBarrier writeBarrier, LoweringTool tool) {
            if (writeBarrier.alwaysNull()) {
                writeBarrier.graph().removeFixed(writeBarrier);
                return;
            }
            Arguments args = new Arguments(serialWriteBarrier, writeBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", writeBarrier.getObject());
            args.add("location", writeBarrier.getLocation());
            args.addConst("usePrecise", writeBarrier.usePrecise());
            template(args).instantiate(providers.getMetaAccess(), writeBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(SerialArrayRangeWriteBarrier arrayRangeWriteBarrier, LoweringTool tool) {
            Arguments args = new Arguments(serialArrayRangeWriteBarrier, arrayRangeWriteBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            template(args).instantiate(providers.getMetaAccess(), arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1PreWriteBarrier writeBarrierPre, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1PreWriteBarrier, writeBarrierPre.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", writeBarrierPre.getObject());

            ValueNode expected = writeBarrierPre.getExpectedObject();
            if (expected != null && expected.stamp() instanceof NarrowOopStamp) {
                assert oopEncoding != null;
                expected = CompressionNode.uncompress(expected, oopEncoding);
            }
            args.add("expectedObject", expected);

            args.add("location", writeBarrierPre.getLocation());
            args.addConst("doLoad", writeBarrierPre.doLoad());
            args.addConst("nullCheck", writeBarrierPre.getNullCheck());
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", traceBarrier());
            template(args).instantiate(providers.getMetaAccess(), writeBarrierPre, DEFAULT_REPLACER, args);
        }

        public void lower(G1ReferentFieldReadBarrier readBarrier, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1ReferentReadBarrier, readBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", readBarrier.getObject());

            ValueNode expected = readBarrier.getExpectedObject();
            if (expected != null && expected.stamp() instanceof NarrowOopStamp) {
                assert oopEncoding != null;
                expected = CompressionNode.uncompress(expected, oopEncoding);
            }

            args.add("expectedObject", expected);
            args.add("location", readBarrier.getLocation());
            args.addConst("doLoad", readBarrier.doLoad());
            args.addConst("nullCheck", false);
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", traceBarrier());
            template(args).instantiate(providers.getMetaAccess(), readBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1PostWriteBarrier writeBarrierPost, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = writeBarrierPost.graph();
            if (writeBarrierPost.alwaysNull()) {
                graph.removeFixed(writeBarrierPost);
                return;
            }
            Arguments args = new Arguments(g1PostWriteBarrier, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("object", writeBarrierPost.getObject());

            ValueNode value = writeBarrierPost.getValue();
            if (value.stamp() instanceof NarrowOopStamp) {
                assert oopEncoding != null;
                value = CompressionNode.uncompress(value, oopEncoding);
            }
            args.add("value", value);

            args.add("location", writeBarrierPost.getLocation());
            args.addConst("usePrecise", writeBarrierPost.usePrecise());
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", traceBarrier());
            template(args).instantiate(providers.getMetaAccess(), writeBarrierPost, DEFAULT_REPLACER, args);
        }

        public void lower(G1ArrayRangePreWriteBarrier arrayRangeWriteBarrier, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1ArrayRangePreWriteBarrier, arrayRangeWriteBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            args.addConst("threadRegister", registers.getThreadRegister());
            template(args).instantiate(providers.getMetaAccess(), arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1ArrayRangePostWriteBarrier arrayRangeWriteBarrier, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1ArrayRangePostWriteBarrier, arrayRangeWriteBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            args.addConst("threadRegister", registers.getThreadRegister());
            template(args).instantiate(providers.getMetaAccess(), arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }
    }

    /**
     * Log method of debugging purposes.
     */
    public static void log(boolean enabled, String format, long value) {
        if (enabled) {
            Log.printf(format, value);
        }
    }

    public static void log(boolean enabled, String format, long value1, long value2) {
        if (enabled) {
            Log.printf(format, value1, value2);
        }
    }

    public static void log(boolean enabled, String format, long value1, long value2, long value3) {
        if (enabled) {
            Log.printf(format, value1, value2, value3);
        }
    }

    public static boolean traceBarrier() {
        return GraalOptions.GCDebugStartCycle.getValue() > 0 && ((int) Word.unsigned(HotSpotReplacementsUtil.gcTotalCollectionsAddress()).readLong(0) > GraalOptions.GCDebugStartCycle.getValue());
    }

    /**
     * Validation helper method which performs sanity checks on write operations. The addresses of
     * both the object and the value being written are checked in order to determine if they reside
     * in a valid heap region. If an object is stale, an invalid access is performed in order to
     * prematurely crash the VM and debug the stack trace of the faulty method.
     */
    public static void validateObject(Object parent, Object child) {
        if (verifyOops() && child != null && !validateOop(VALIDATE_OBJECT, parent, child)) {
            log(true, "Verification ERROR, Parent: %p Child: %p\n", Word.fromObject(parent).rawValue(), Word.fromObject(child).rawValue());
            DirectObjectStoreNode.storeObject(null, 0, 0, null, LocationIdentity.ANY_LOCATION);
        }
    }

    public static final ForeignCallDescriptor VALIDATE_OBJECT = new ForeignCallDescriptor("validate_object", boolean.class, Word.class, Word.class);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native boolean validateOop(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object parent, Object object);

}
