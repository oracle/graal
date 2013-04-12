/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.hotspot.nodes.DirectCompareAndSwapNode.*;
import static com.oracle.graal.hotspot.replacements.HotSpotSnippetUtils.*;
import static com.oracle.graal.hotspot.replacements.NewObjectSnippets.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.word.*;

/**
 * Stub implementing the fast path for TLAB refill during instance class allocation. This stub is
 * called from the {@linkplain NewObjectSnippets inline} allocation code when TLAB allocation fails.
 * If this stub fails to refill the TLAB or allocate the object, it calls out to the HotSpot C++
 * runtime for to complete the allocation.
 */
public class NewInstanceStub extends Stub {

    public NewInstanceStub(final HotSpotRuntime runtime, Replacements replacements, TargetDescription target, HotSpotRuntimeCallTarget linkage) {
        super(runtime, replacements, target, linkage, "newInstance");
    }

    @Override
    protected Arguments makeArguments(SnippetInfo stub) {
        HotSpotResolvedObjectType intArrayType = (HotSpotResolvedObjectType) runtime.lookupJavaType(int[].class);

        Arguments args = new Arguments(stub);
        args.add("hub", null);
        args.addConst("intArrayHub", intArrayType.klass());
        args.addConst("log", Boolean.getBoolean("graal.logNewInstanceStub"));
        return args;
    }

    /**
     * Re-attempts allocation after an initial TLAB allocation failed or was skipped (e.g., due to
     * -XX:-UseTLAB).
     * 
     * @param hub the hub of the object to be allocated
     * @param intArrayHub the hub for {@code int[].class}
     */
    @Snippet
    private static Object newInstance(Word hub, @ConstantParameter Word intArrayHub, @ConstantParameter boolean log) {
        int sizeInBytes = hub.readInt(klassInstanceSizeOffset(), FINAL_LOCATION);
        if (!forceSlowPath() && inlineContiguousAllocationSupported()) {
            if (hub.readInt(klassStateOffset(), CLASS_STATE_LOCATION) == klassStateFullyInitialized()) {
                Word memory = refillAllocate(intArrayHub, sizeInBytes, log);
                if (memory.notEqual(0)) {
                    Word prototypeMarkWord = hub.readWord(prototypeMarkWordOffset(), PROTOTYPE_MARK_WORD_LOCATION);
                    initializeObjectHeader(memory, prototypeMarkWord, hub);
                    for (int offset = 2 * wordSize(); offset < sizeInBytes; offset += wordSize()) {
                        memory.writeWord(offset, Word.zero(), ANY_LOCATION);
                    }
                    return verifyOop(memory.toObject());
                }
            }
        }
        return verifyOop(NewInstanceSlowStubCall.call(hub));
    }

    /**
     * Attempts to refill the current thread's TLAB and retries the allocation.
     * 
     * @param intArrayHub the hub for {@code int[].class}
     * @param sizeInBytes the size of the allocation
     * @param log specifies if logging is enabled
     * @return the newly allocated, uninitialized chunk of memory, or {@link Word#zero()} if the
     *         operation was unsuccessful
     */
    static Word refillAllocate(Word intArrayHub, int sizeInBytes, boolean log) {
        if (!useTLAB()) {
            return edenAllocate(Word.unsigned(sizeInBytes), log);
        }
        Word intArrayMarkWord = Word.unsigned(tlabIntArrayMarkWord());
        int alignmentReserveInBytes = tlabAlignmentReserveInHeapWords() * wordSize();

        Word thread = thread();
        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);

        // calculate amount of free space
        Word tlabFreeSpaceInBytes = end.subtract(top);

        log(log, "refillTLAB: thread=%p\n", thread);
        log(log, "refillTLAB: top=%p\n", top);
        log(log, "refillTLAB: end=%p\n", end);
        log(log, "refillTLAB: tlabFreeSpaceInBytes=%d\n", tlabFreeSpaceInBytes);

        Word tlabFreeSpaceInWords = tlabFreeSpaceInBytes.unsignedShiftRight(log2WordSize());

        // Retain TLAB and allocate object in shared space if
        // the amount free in the TLAB is too large to discard.
        Word refillWasteLimit = thread.readWord(tlabRefillWasteLimitOffset(), TLAB_REFILL_WASTE_LIMIT_LOCATION);
        if (tlabFreeSpaceInWords.belowOrEqual(refillWasteLimit)) {
            if (tlabStats()) {
                // increment number of refills
                thread.writeInt(tlabNumberOfRefillsOffset(), thread.readInt(tlabNumberOfRefillsOffset(), TLAB_NOF_REFILLS_LOCATION) + 1, TLAB_NOF_REFILLS_LOCATION);
                log(log, "thread: %p -- number_of_refills %d\n", thread, thread.readInt(tlabNumberOfRefillsOffset(), TLAB_NOF_REFILLS_LOCATION));
                // accumulate wastage
                Word wastage = thread.readWord(tlabFastRefillWasteOffset(), TLAB_FAST_REFILL_WASTE_LOCATION).add(tlabFreeSpaceInWords);
                log(log, "thread: %p -- accumulated wastage %d\n", thread, wastage);
                thread.writeWord(tlabFastRefillWasteOffset(), wastage, TLAB_FAST_REFILL_WASTE_LOCATION);
            }

            // if TLAB is currently allocated (top or end != null) then
            // fill [top, end + alignment_reserve) with array object
            if (top.notEqual(0)) {
                int headerSize = arrayBaseOffset(Kind.Int);
                // just like the HotSpot assembler stubs, assumes that tlabFreeSpaceInInts fits in
                // an int
                int tlabFreeSpaceInInts = (int) tlabFreeSpaceInBytes.rawValue() >>> 2;
                int length = ((alignmentReserveInBytes - headerSize) >>> 2) + tlabFreeSpaceInInts;
                NewObjectSnippets.formatArray(intArrayHub, -1, length, headerSize, top, intArrayMarkWord, false);

                Word allocated = thread.readWord(threadAllocatedBytesOffset(), TLAB_THREAD_ALLOCATED_BYTES_LOCATION);
                allocated = allocated.add(top.subtract(readTlabStart(thread)));
                thread.writeWord(threadAllocatedBytesOffset(), allocated, TLAB_THREAD_ALLOCATED_BYTES_LOCATION);
            }

            // refill the TLAB with an eden allocation
            Word tlabRefillSizeInWords = thread.readWord(threadTlabSizeOffset(), TLAB_SIZE_LOCATION);
            Word tlabRefillSizeInBytes = tlabRefillSizeInWords.multiply(wordSize());
            // allocate new TLAB, address returned in top
            top = edenAllocate(tlabRefillSizeInBytes, log);
            if (top.notEqual(0)) {
                end = top.add(tlabRefillSizeInBytes.subtract(alignmentReserveInBytes));
                initializeTlab(thread, top, end);

                return allocate(sizeInBytes);
            } else {
                return Word.zero();
            }
        } else {
            // Retain TLAB
            Word newRefillWasteLimit = refillWasteLimit.add(tlabRefillWasteIncrement());
            thread.writeWord(tlabRefillWasteLimitOffset(), newRefillWasteLimit, TLAB_REFILL_WASTE_LIMIT_LOCATION);
            log(log, "refillTLAB: retaining TLAB - newRefillWasteLimit=%p\n", newRefillWasteLimit);

            if (tlabStats()) {
                thread.writeInt(tlabSlowAllocationsOffset(), thread.readInt(tlabSlowAllocationsOffset(), TLAB_SLOW_ALLOCATIONS_LOCATION) + 1, TLAB_SLOW_ALLOCATIONS_LOCATION);
            }

            return edenAllocate(Word.unsigned(sizeInBytes), log);
        }
    }

    /**
     * Attempts to allocate a chunk of memory from Eden space.
     * 
     * @param sizeInBytes the size of the chunk to allocate
     * @param log specifies if logging is enabled
     * @return the allocated chunk or {@link Word#zero()} if allocation fails
     */
    static Word edenAllocate(Word sizeInBytes, boolean log) {
        Word heapTopAddress = Word.unsigned(heapTopAddress());
        Word heapEndAddress = Word.unsigned(heapEndAddress());

        while (true) {
            Word heapTop = heapTopAddress.readWord(0, HEAP_TOP_LOCATION);
            Word newHeapTop = heapTop.add(sizeInBytes);
            if (newHeapTop.belowOrEqual(heapTop)) {
                return Word.zero();
            }

            Word heapEnd = heapEndAddress.readWord(0, HEAP_END_LOCATION);
            if (newHeapTop.aboveThan(heapEnd)) {
                return Word.zero();
            }

            if (compareAndSwap(heapTopAddress, 0, heapTop, newHeapTop, HEAP_TOP_LOCATION).equal(heapTop)) {
                return heapTop;
            }
        }
    }

    @Fold
    private static boolean forceSlowPath() {
        return Boolean.getBoolean("graal.newInstanceStub.forceSlowPath");
    }

    static void log(boolean enabled, String format, long value) {
        if (enabled) {
            Log.printf(format, value);
        }
    }

    static void log(boolean enabled, String format, WordBase value) {
        if (enabled) {
            Log.printf(format, value.rawValue());
        }
    }

    static void log(boolean enabled, String format, long v1, long v2) {
        if (enabled) {
            Log.printf(format, v1, v2);
        }
    }

    static void log(boolean enabled, String format, Word v1, long v2) {
        if (enabled) {
            Log.printf(format, v1.rawValue(), v2);
        }
    }

    static void log(boolean enabled, String format, Word v1, Word v2) {
        if (enabled) {
            Log.printf(format, v1.rawValue(), v2.rawValue());
        }
    }

    static void log(boolean enabled, String format, long v1, long v2, long v3) {
        if (enabled) {
            Log.printf(format, v1, v2, v3);
        }
    }
}
