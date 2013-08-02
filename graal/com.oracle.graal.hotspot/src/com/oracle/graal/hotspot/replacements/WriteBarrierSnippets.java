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

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

public class WriteBarrierSnippets implements Snippets {

    private static final SnippetCounter.Group countersWriteBarriers = SnippetCounters.getValue() ? new SnippetCounter.Group("WriteBarriers") : null;
    private static final SnippetCounter serialFieldWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialFieldWriteBarrier", "Number of Serial Field Write Barriers");
    private static final SnippetCounter serialArrayWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialArrayWriteBarrier", "Number of Serial Array Write Barriers");

    @Snippet
    public static void serialWriteBarrier(Object object, Object location, @ConstantParameter boolean usePrecise, @ConstantParameter boolean alwaysNull) {
        // No barriers are added if we are always storing a null.
        if (alwaysNull) {
            return;
        }
        Object fixedObject = FixedValueAnchorNode.getObject(object);
        Pointer oop;
        if (usePrecise) {
            oop = Word.fromArray(fixedObject, location);
            serialArrayWriteBarrierCounter.inc();
        } else {
            oop = Word.fromObject(fixedObject);
            serialFieldWriteBarrierCounter.inc();
        }
        Word base = (Word) oop.unsignedShiftRight(cardTableShift());
        long startAddress = cardTableStart();
        int displacement = 0;
        if (((int) startAddress) == startAddress) {
            displacement = (int) startAddress;
        } else {
            base = base.add(Word.unsigned(cardTableStart()));
        }
        base.writeByte(displacement, (byte) 0);
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
                    @ConstantParameter boolean trace) {
        if (nullCheck && object == null) {
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
        }
        Word thread = thread();
        Object fixedObject = FixedValueAnchorNode.getObject(object);
        verifyOop(fixedObject);
        Object fixedExpectedObject = FixedValueAnchorNode.getObject(expectedObject);
        Word field = (Word) Word.fromArray(fixedObject, location);
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
        // If the concurrent marker is enabled, the barrier is issued.
        if (probability(NOT_LIKELY_PROBABILITY, markingValue != (byte) 0)) {
            // If the previous value has to be loaded (before the write), the load is issued.
            // The load is always issued except the cases of CAS and referent field.
            if (probability(LIKELY_PROBABILITY, doLoad)) {
                previousOop = (Word) Word.fromObject(field.readObject(0, BarrierType.NONE, true));
                if (trace) {
                    log(trace, "[%d] G1-Pre Thread %p Previous Object %p\n ", gcCycle, thread.rawValue(), previousOop.rawValue());
                    verifyOop(previousOop.toObject());
                }
            }
            // If the previous value is null the barrier should not be issued.
            if (probability(FREQUENT_PROBABILITY, previousOop.notEqual(0))) {
                // If the thread-local SATB buffer is full issue a native call which will
                // initialize a new one and add the entry.
                if (probability(FREQUENT_PROBABILITY, indexValue.notEqual(0))) {
                    Word nextIndex = indexValue.subtract(wordSize());
                    Word logAddress = bufferAddress.add(nextIndex);
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    logAddress.writeWord(0, previousOop);
                    indexAddress.writeWord(0, nextIndex);
                } else {
                    g1PreBarrierStub(G1WBPRECALL, previousOop.toObject());
                }
            }
        }
    }

    @Snippet
    public static void g1PostWriteBarrier(Object object, Object value, Object location, @ConstantParameter boolean usePrecise, @ConstantParameter boolean alwaysNull, @ConstantParameter boolean trace) {
        // No barriers are added if we are always storing a null.
        if (alwaysNull) {
            return;
        }
        Word thread = thread();
        Object fixedObject = FixedValueAnchorNode.getObject(object);
        Object fixedValue = FixedValueAnchorNode.getObject(value);
        verifyOop(fixedObject);
        verifyOop(fixedValue);
        validateObject(fixedObject, fixedValue);
        Word oop;
        if (usePrecise) {
            oop = (Word) Word.fromArray(fixedObject, location);
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

        if (probability(LIKELY_PROBABILITY, xorResult.notEqual(0))) {
            // If the written value is not null continue with the barrier addition.
            if (probability(FREQUENT_PROBABILITY, writtenValue.notEqual(0))) {
                byte cardByte = cardAddress.readByte(0);
                // If the card is already dirty, (hence already enqueued) skip the insertion.
                if (probability(LIKELY_PROBABILITY, cardByte != (byte) 0)) {
                    log(trace, "[%d] G1-Post Thread: %p Card: %p \n", gcCycle, thread.rawValue(), Word.unsigned(cardByte).rawValue());
                    cardAddress.writeByte(0, (byte) 0);
                    // If the thread local card queue is full, issue a native call which will
                    // initialize a new one and add the card entry.
                    if (probability(FREQUENT_PROBABILITY, indexValue.notEqual(0))) {
                        Word nextIndex = indexValue.subtract(wordSize());
                        Word logAddress = bufferAddress.add(nextIndex);
                        // Log the object to be scanned as well as update
                        // the card queue's next index.
                        logAddress.writeWord(0, cardAddress);
                        indexAddress.writeWord(0, nextIndex);
                    } else {
                        g1PostBarrierStub(G1WBPOSTCALL, cardAddress);
                    }
                }
            }
        }
    }

    @Snippet
    public static void g1ArrayRangePreWriteBarrier(Object object, int startIndex, int length) {
        Word thread = thread();
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
            Pointer oop = Word.fromObject(Word.unsigned(address).readObject(0, BarrierType.NONE, true));
            verifyOop(oop.toObject());
            if (oop.notEqual(0)) {
                if (indexValue != 0) {
                    indexValue = indexValue - wordSize();
                    Word logAddress = bufferAddress.add(Word.unsigned(indexValue));
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    logAddress.writeWord(0, oop);
                    indexAddress.writeWord(0, Word.unsigned(indexValue));
                } else {
                    g1PreBarrierStub(G1WBPRECALL, oop.toObject());
                }
            }
        }
    }

    @Snippet
    public static void g1ArrayRangePostWriteBarrier(Object object, int startIndex, int length) {
        if (length == 0) {
            return;
        }
        Object dest = FixedValueAnchorNode.getObject(object);
        Word thread = thread();
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
                cardAddress.writeByte(0, (byte) 0);
                // If the thread local card queue is full, issue a native call which will
                // initialize a new one and add the card entry.
                if (indexValue != 0) {
                    indexValue = indexValue - wordSize();
                    Word logAddress = bufferAddress.add(Word.unsigned(indexValue));
                    // Log the object to be scanned as well as update
                    // the card queue's next index.
                    logAddress.writeWord(0, cardAddress);
                    indexAddress.writeWord(0, Word.unsigned(indexValue));
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
    private static native void g1PostBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word card);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo serialWriteBarrier = snippet(WriteBarrierSnippets.class, "serialWriteBarrier");
        private final SnippetInfo serialArrayRangeWriteBarrier = snippet(WriteBarrierSnippets.class, "serialArrayRangeWriteBarrier");
        private final SnippetInfo g1PreWriteBarrier = snippet(WriteBarrierSnippets.class, "g1PreWriteBarrier");

        private final SnippetInfo g1ReferentReadBarrier = snippet(WriteBarrierSnippets.class, "g1PreWriteBarrier");
        private final SnippetInfo g1PostWriteBarrier = snippet(WriteBarrierSnippets.class, "g1PostWriteBarrier");
        private final SnippetInfo g1ArrayRangePreWriteBarrier = snippet(WriteBarrierSnippets.class, "g1ArrayRangePreWriteBarrier");
        private final SnippetInfo g1ArrayRangePostWriteBarrier = snippet(WriteBarrierSnippets.class, "g1ArrayRangePostWriteBarrier");

        public Templates(CodeCacheProvider runtime, Replacements replacements, TargetDescription target) {
            super(runtime, replacements, target);
        }

        public void lower(SerialWriteBarrier writeBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(serialWriteBarrier);
            args.add("object", writeBarrier.getObject());
            args.add("location", writeBarrier.getLocation());
            args.addConst("usePrecise", writeBarrier.usePrecise());
            args.addConst("alwaysNull", writeBarrier.getValue().objectStamp().alwaysNull());
            template(args).instantiate(runtime, writeBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(SerialArrayRangeWriteBarrier arrayRangeWriteBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(serialArrayRangeWriteBarrier);
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            template(args).instantiate(runtime, arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1PreWriteBarrier writeBarrierPre, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(g1PreWriteBarrier);
            args.add("object", writeBarrierPre.getObject());
            args.add("expectedObject", writeBarrierPre.getExpectedObject());
            args.add("location", writeBarrierPre.getLocation());
            args.addConst("doLoad", writeBarrierPre.doLoad());
            args.addConst("nullCheck", writeBarrierPre.getNullCheck());
            args.addConst("trace", traceBarrier());
            template(args).instantiate(runtime, writeBarrierPre, DEFAULT_REPLACER, args);
        }

        public void lower(G1ReferentFieldReadBarrier readBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(g1ReferentReadBarrier);
            args.add("object", readBarrier.getObject());
            args.add("expectedObject", readBarrier.getExpectedObject());
            args.add("location", readBarrier.getLocation());
            args.addConst("doLoad", readBarrier.doLoad());
            args.addConst("nullCheck", false);
            args.addConst("trace", traceBarrier());
            template(args).instantiate(runtime, readBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1PostWriteBarrier writeBarrierPost, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(g1PostWriteBarrier);
            args.add("object", writeBarrierPost.getObject());
            args.add("value", writeBarrierPost.getValue());
            args.add("location", writeBarrierPost.getLocation());
            args.addConst("usePrecise", writeBarrierPost.usePrecise());
            args.addConst("alwaysNull", writeBarrierPost.getValue().objectStamp().alwaysNull());
            args.addConst("trace", traceBarrier());
            template(args).instantiate(runtime, writeBarrierPost, DEFAULT_REPLACER, args);
        }

        public void lower(G1ArrayRangePreWriteBarrier arrayRangeWriteBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(g1ArrayRangePreWriteBarrier);
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            template(args).instantiate(runtime, arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1ArrayRangePostWriteBarrier arrayRangeWriteBarrier, @SuppressWarnings("unused") LoweringTool tool) {
            Arguments args = new Arguments(g1ArrayRangePostWriteBarrier);
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            template(args).instantiate(runtime, arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }
    }

    /**
     * Log method of debugging purposes.
     */
    private static void log(boolean enabled, String format, long value) {
        if (enabled) {
            Log.printf(format, value);
        }
    }

    private static void log(boolean enabled, String format, long value1, long value2) {
        if (enabled) {
            Log.printf(format, value1, value2);
        }
    }

    private static void log(boolean enabled, String format, long value1, long value2, long value3) {
        if (enabled) {
            Log.printf(format, value1, value2, value3);
        }
    }

    private static boolean traceBarrier() {
        return GraalOptions.GCDebugStartCycle.getValue() > 0 && ((int) Word.unsigned(HotSpotReplacementsUtil.gcTotalCollectionsAddress()).readLong(0) > GraalOptions.GCDebugStartCycle.getValue());
    }

    /**
     * Validation helper method which performs sanity checks on write operations. The addresses of
     * both the object and the value being written are checked in order to determine if they reside
     * in a valid heap region. If an object is stale, an invalid access is performed in order to
     * prematurely crash the VM and debug the stack trace of the faulty method.
     */
    private static void validateObject(Object parent, Object child) {
        if (child != null && !validateOop(VALIDATEOBJECT, parent, child)) {
            log(true, "Verification ERROR, Parent: %p Child: %p\n", Word.fromObject(parent).rawValue(), Word.fromObject(child).rawValue());
            DirectObjectStoreNode.storeWord(null, 0, 0, Word.zero());
        }
    }

    public static final ForeignCallDescriptor VALIDATEOBJECT = new ForeignCallDescriptor("validate_object", boolean.class, Word.class, Word.class);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native boolean validateOop(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object parent, Object object);

}
