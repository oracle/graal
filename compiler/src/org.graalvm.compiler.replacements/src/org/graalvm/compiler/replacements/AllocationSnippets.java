/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.replacements.ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED;
import static org.graalvm.compiler.replacements.nodes.ExplodeLoopNode.explodeLoop;

import org.graalvm.compiler.nodes.PrefetchAllocateNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.nodes.ExplodeLoopNode;
import org.graalvm.compiler.replacements.nodes.ZeroMemoryNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

/**
 * Snippets used for implementing NEW, ANEWARRAY and NEWARRAY.
 */
public abstract class AllocationSnippets implements Snippets {
    protected Object allocateInstanceImpl(Word hub,
                    UnsignedWord size,
                    FillContent fillContents,
                    boolean emitMemoryBarrier,
                    boolean constantSize,
                    AllocationProfilingData profilingData) {
        Object result;
        Word tlabInfo = getTLABInfo();
        Word top = readTlabTop(tlabInfo);
        Word end = readTlabEnd(tlabInfo);
        Word newTop = top.add(size);
        if (useTLAB() && probability(FAST_PATH_PROBABILITY, shouldAllocateInTLAB(size, false)) && probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
            writeTlabTop(tlabInfo, newTop);
            emitPrefetchAllocate(newTop, false);
            result = formatObject(hub, size, top, fillContents, emitMemoryBarrier, constantSize, profilingData.snippetCounters);
        } else {
            profilingData.snippetCounters.stub.inc();
            result = callNewInstanceStub(hub);
        }
        profileAllocation(profilingData, size);
        return verifyOop(result);
    }

    public Object allocateArrayImpl(Word hub,
                    int length,
                    int arrayBaseOffset,
                    int log2ElementSize,
                    FillContent fillContents,
                    int fillStartOffset,
                    boolean emitMemoryBarrier,
                    boolean maybeUnroll,
                    boolean supportsBulkZeroing,
                    boolean supportsOptimizedFilling,
                    AllocationProfilingData profilingData) {
        Word thread = getTLABInfo();
        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);
        ReplacementsUtil.dynamicAssert(end.subtract(top).belowOrEqual(Integer.MAX_VALUE), "TLAB is too large");

        // A negative array length will result in an array size larger than the largest possible
        // TLAB. Therefore, this case will always end up in the stub call.
        UnsignedWord allocationSize = arrayAllocationSize(length, arrayBaseOffset, log2ElementSize);
        Word newTop = top.add(allocationSize);

        Object result;
        if (useTLAB() && probability(FAST_PATH_PROBABILITY, shouldAllocateInTLAB(allocationSize, true)) && probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
            writeTlabTop(thread, newTop);
            emitPrefetchAllocate(newTop, true);
            result = formatArray(hub, allocationSize, length, top, fillContents, emitMemoryBarrier, fillStartOffset, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling,
                            profilingData.snippetCounters);
        } else {
            profilingData.snippetCounters.stub.inc();
            result = callNewArrayStub(hub, length);
        }
        profileAllocation(profilingData, allocationSize);
        return verifyOop(result);
    }

    protected Object newMultiArrayImpl(Word hub, int rank, int[] dimensions) {
        Word dims = DimensionsNode.allocaDimsArray(rank);
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < rank; i++) {
            dims.writeInt(i * 4, dimensions[i], LocationIdentity.init());
        }
        return callNewMultiArrayStub(hub, rank, dims);
    }

    protected UnsignedWord arrayAllocationSize(int length, int arrayBaseOffset, int log2ElementSize) {
        int alignment = objectAlignment();
        return WordFactory.unsigned(arrayAllocationSize(length, arrayBaseOffset, log2ElementSize, alignment));
    }

    /**
     * We do an unsigned multiplication so that a negative array length will result in an array size
     * greater than Integer.MAX_VALUE.
     */
    public static long arrayAllocationSize(int length, int arrayBaseOffset, int log2ElementSize, int alignment) {
        long size = ((length & 0xFFFFFFFFL) << log2ElementSize) + arrayBaseOffset + (alignment - 1);
        long mask = ~(alignment - 1);
        return size & mask;
    }

    /**
     * Maximum number of long stores to emit when zeroing an object with a constant size. Larger
     * objects have their bodies initialized in a loop.
     */
    private static final int MAX_UNROLLED_OBJECT_ZEROING_STORES = 8;

    /**
     * Zero uninitialized memory in a newly allocated object, unrolling as necessary and ensuring
     * that stores are aligned.
     *
     * @param memory beginning of object which is being zeroed
     * @param startOffset offset to begin zeroing (inclusive). Does not have to be word-aligned.
     * @param endOffset offset to stop zeroing (exclusive). Does not have to be word-aligned.
     * @param isEndOffsetConstant is {@code endOffset} known to be constant in the snippet
     * @param manualUnroll maximally unroll zeroing
     * @param supportsBulkZeroing whether bulk zeroing is supported by the backend
     * @param supportsOptimizedFilling whether optimized memory filling is supported by the backend
     */
    private void zeroMemory(Word memory,
                    int startOffset,
                    UnsignedWord endOffset,
                    boolean isEndOffsetConstant,
                    boolean manualUnroll,
                    boolean supportsBulkZeroing,
                    boolean supportsOptimizedFilling,
                    AllocationSnippetCounters snippetCounters) {
        fillMemory(0, memory, startOffset, endOffset, isEndOffsetConstant, manualUnroll, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
    }

    private void fillMemory(long value,
                    Word memory,
                    int startOffset,
                    UnsignedWord endOffset,
                    boolean isEndOffsetConstant,
                    boolean manualUnroll,
                    boolean supportsBulkZeroing,
                    boolean supportsOptimizedFilling,
                    AllocationSnippetCounters snippetCounters) {
        ReplacementsUtil.dynamicAssert(endOffset.and(0x7).equal(0), "unaligned object size");
        UnsignedWord offset = WordFactory.unsigned(startOffset);
        if (probability(SLOW_PATH_PROBABILITY, offset.and(0x7).notEqual(0))) {
            memory.writeInt(offset, (int) value, LocationIdentity.init());
            offset = offset.add(4);
        }
        ReplacementsUtil.dynamicAssert(offset.and(0x7).equal(0), "unaligned offset");
        UnsignedWord remainingSize = endOffset.subtract(offset);
        if (probability(LIKELY_PROBABILITY, manualUnroll && remainingSize.unsignedDivide(8).belowOrEqual(MAX_UNROLLED_OBJECT_ZEROING_STORES))) {
            ReplacementsUtil.staticAssert(!isEndOffsetConstant, "size shouldn't be constant at instantiation time");
            fillMemoryAlignedUnrollable(value, memory, offset, endOffset, supportsOptimizedFilling, snippetCounters);
        } else {
            fillMemoryAligned(value, memory, offset, endOffset, isEndOffsetConstant, remainingSize, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
        }
    }

    @SuppressWarnings("unused")
    protected void fillMemoryAlignedUnrollable(
                    long value,
                    Word memory,
                    UnsignedWord fromOffset,
                    UnsignedWord endOffset,
                    boolean supportsOptimizedFilling,
                    AllocationSnippetCounters snippetCounters) {
        // This case handles arrays of constant length. Instead of having a snippet variant for
        // each length, generate a chain of stores of maximum length. Once it's inlined the
        // break statement will trim excess stores.
        snippetCounters.unrolledInit.inc();

        explodeLoop();
        UnsignedWord offset = fromOffset;
        for (int i = 0; i < MAX_UNROLLED_OBJECT_ZEROING_STORES; i++, offset = offset.add(8)) {
            if (offset.equal(endOffset)) {
                break;
            }
            memory.initializeLong(offset, value, LocationIdentity.init());
        }
    }

    @SuppressWarnings("unused")
    protected void fillMemoryAligned(
                    long value,
                    Word memory,
                    UnsignedWord fromOffset,
                    UnsignedWord endOffset,
                    boolean isEndOffsetConstant,
                    UnsignedWord remainingSize,
                    boolean supportsBulkZeroing,
                    boolean supportsOptimizedFilling,
                    AllocationSnippetCounters snippetCounters) {
        if (supportsBulkZeroing && value == 0 && probability(SLOW_PATH_PROBABILITY, remainingSize.aboveOrEqual(getMinimalBulkZeroingSize()))) {
            snippetCounters.bulkInit.inc();
            ZeroMemoryNode.zero(memory.add(fromOffset), remainingSize.rawValue(), true, LocationIdentity.init());
        } else {
            if (isEndOffsetConstant && remainingSize.unsignedDivide(8).belowOrEqual(MAX_UNROLLED_OBJECT_ZEROING_STORES)) {
                snippetCounters.unrolledInit.inc();
                explodeLoop();
            } else {
                snippetCounters.loopInit.inc();
            }
            UnsignedWord offset = fromOffset;
            for (; injectBranchProbability(FAST_PATH_PROBABILITY, offset.belowThan(endOffset)); offset = offset.add(8)) {
                memory.initializeLong(offset, value, LocationIdentity.init());
            }
        }
    }

    /**
     * Fill uninitialized memory with garbage value in a newly allocated object, unrolling as
     * necessary and ensuring that stores are aligned.
     *
     * @param memory beginning of object which is being zeroed
     * @param startOffset offset to begin filling garbage value (inclusive). Does not have to be
     *            word-aligned.
     * @param endOffset offset to stop filling garbage value (exclusive). Does not have to be
     *            word-aligned.
     * @param isEndOffsetConstant is {@code endOffset} known to be constant in the snippet
     * @param manualUnroll maximally unroll zeroing
     * @param supportsOptimizedFilling whether optimized memory filling is supported by the backend
     */
    private void fillWithGarbage(Word memory,
                    int startOffset,
                    UnsignedWord endOffset,
                    boolean isEndOffsetConstant,
                    boolean manualUnroll,
                    boolean supportsOptimizedFilling,
                    AllocationSnippetCounters snippetCounters) {
        fillMemory(0xfefefefefefefefeL, memory, startOffset, endOffset, isEndOffsetConstant, manualUnroll, false, supportsOptimizedFilling, snippetCounters);
    }

    /**
     * Formats some allocated memory with an object header and zeroes out the rest.
     */
    public Object formatObject(Word hub,
                    UnsignedWord size,
                    Word memory,
                    FillContent fillContents,
                    boolean emitMemoryBarrier,
                    boolean constantSize,
                    AllocationSnippetCounters snippetCounters) {
        initializeObjectHeader(memory, hub, false);
        int headerSize = instanceHeaderSize();
        fillContents(memory, fillContents, headerSize, size, constantSize, false, false, false, snippetCounters);
        emitMemoryBarrierIf(emitMemoryBarrier);
        return memory.toObjectNonNull();
    }

    /**
     * Formats some allocated memory with an object header and zeroes out the rest.
     */
    public Object formatArray(Word hub,
                    UnsignedWord allocationSize,
                    int length,
                    Word memory,
                    FillContent fillContents,
                    boolean emitMemoryBarrier,
                    int fillStartOffset,
                    boolean maybeUnroll,
                    boolean supportsBulkZeroing,
                    boolean supportsOptimizedFilling,
                    AllocationSnippetCounters snippetCounters) {
        /*
         * For TLAB allocations, the initialization order does not matter. Therefore, it is also not
         * necessary to use STORE_RELEASE semantics when storing the hub into the newly allocated
         * object. This is a major difference to the slow-path allocation where the initialization
         * order and the STORE_RELEASE semantics are crucial for concurrent GCs (the slow-path
         * allocation can directly allocate in the old generation).
         */
        initializeObjectHeader(memory, hub, true);
        memory.writeInt(arrayLengthOffset(), length, LocationIdentity.init());
        fillContents(memory, fillContents, fillStartOffset, allocationSize, false, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
        emitMemoryBarrierIf(emitMemoryBarrier);
        return memory.toObjectNonNull();
    }

    private void fillContents(Word memory, FillContent fillContents, int startOffset, UnsignedWord endOffset, boolean isEndOffsetConstant, boolean maybeUnroll, boolean supportsBulkZeroing,
                    boolean supportsOptimizedFilling, AllocationSnippetCounters snippetCounters) {
        if (fillContents == FillContent.WITH_ZEROES) {
            zeroMemory(memory, startOffset, endOffset, isEndOffsetConstant, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
        } else if (REPLACEMENTS_ASSERTIONS_ENABLED && fillContents == FillContent.WITH_GARBAGE_IF_ASSERTIONS_ENABLED) {
            fillWithGarbage(memory, startOffset, endOffset, isEndOffsetConstant, maybeUnroll, supportsOptimizedFilling, snippetCounters);
        }
    }

    protected void emitMemoryBarrierIf(boolean emitMemoryBarrier) {
        if (emitMemoryBarrier) {
            MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.init());
        }
    }

    public void emitPrefetchAllocate(Word address, boolean isArray) {
        if (getPrefetchStyle() > 0) {
            // Insert a prefetch for each allocation only on the fast-path
            // Generate several prefetch instructions.
            int lines = getPrefetchLines(isArray);
            int stepSize = getPrefetchStepSize();
            int distance = getPrefetchDistance();
            ExplodeLoopNode.explodeLoop();
            for (int i = 0; i < lines; i++) {
                PrefetchAllocateNode.prefetch(OffsetAddressNode.address(address, distance));
                distance += stepSize;
            }
        }
    }

    protected abstract int getPrefetchStyle();

    protected abstract int getPrefetchLines(boolean isArray);

    protected abstract int getPrefetchStepSize();

    protected abstract int getPrefetchDistance();

    public abstract boolean useTLAB();

    protected abstract boolean shouldAllocateInTLAB(UnsignedWord allocationSize, boolean isArray);

    public abstract Word getTLABInfo();

    public abstract Word readTlabTop(Word tlabInfo);

    public abstract Word readTlabEnd(Word tlabInfo);

    public abstract void writeTlabTop(Word tlabInfo, Word newTop);

    protected abstract int instanceHeaderSize();

    public abstract void initializeObjectHeader(Word memory, Word hub, boolean isArray);

    protected abstract Object callNewInstanceStub(Word hub);

    protected abstract Object callNewArrayStub(Word hub, int length);

    protected abstract Object callNewMultiArrayStub(Word hub, int rank, Word dims);

    protected abstract int getMinimalBulkZeroingSize();

    protected abstract void profileAllocation(AllocationProfilingData profilingData, UnsignedWord size);

    protected abstract Object verifyOop(Object obj);

    public abstract int arrayLengthOffset();

    protected abstract int objectAlignment();

    public enum FillContent {
        DO_NOT_FILL,
        WITH_ZEROES,
        WITH_GARBAGE_IF_ASSERTIONS_ENABLED;

        public static FillContent fromBoolean(boolean fillContents) {
            if (fillContents) {
                return WITH_ZEROES;
            }
            return WITH_GARBAGE_IF_ASSERTIONS_ENABLED;
        }
    }

    public static class AllocationProfilingData {
        public final AllocationSnippetCounters snippetCounters;

        public AllocationProfilingData(AllocationSnippetCounters snippetCounters) {
            this.snippetCounters = snippetCounters;
        }
    }

    public static class AllocationSnippetCounters {
        public AllocationSnippetCounters(SnippetCounter.Group.Factory factory) {
            Group allocations = factory.createSnippetCounterGroup("Allocations");
            unrolledInit = new SnippetCounter(allocations, "tlabSeqInit", "TLAB alloc with unrolled zeroing");
            loopInit = new SnippetCounter(allocations, "tlabLoopInit", "TLAB alloc with zeroing in a loop");
            bulkInit = new SnippetCounter(allocations, "tlabBulkInit", "TLAB alloc with bulk zeroing");
            stub = new SnippetCounter(allocations, "stub", "alloc and zeroing via stub");
        }

        public final SnippetCounter unrolledInit;
        public final SnippetCounter loopInit;
        public final SnippetCounter bulkInit;
        public final SnippetCounter stub;
    }
}
