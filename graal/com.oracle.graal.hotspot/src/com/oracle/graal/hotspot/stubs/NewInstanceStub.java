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
import static com.oracle.graal.hotspot.nodes.NewInstanceStubCall.*;
import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.hotspot.snippets.NewObjectSnippets.*;
import static com.oracle.graal.snippets.nodes.DirectObjectStoreNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.snippets.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.*;
import com.oracle.graal.snippets.SnippetTemplate.Key;

/**
 * Stub implementing the fast path for TLAB refill during instance class allocation.
 * This stub is called from the {@linkplain NewObjectSnippets inline} allocation
 * code when TLAB allocation fails. If this stub fails to refill the TLAB
 * or allocate the object, it calls out to the HotSpot C++ runtime for
 * to complete the allocation.
 */
public class NewInstanceStub extends Stub {

    public NewInstanceStub(final HotSpotRuntime runtime, Assumptions assumptions, TargetDescription target) {
        super(runtime, assumptions, target, NEW_INSTANCE);
    }

    @Override
    protected void populateKey(Key key) {
        HotSpotResolvedObjectType intArrayType = (HotSpotResolvedObjectType) runtime.lookupJavaType(int[].class);
        Constant intArrayHub = intArrayType.klass();
        key.add("intArrayHub", intArrayHub).add("log", Boolean.getBoolean("graal.logNewInstanceStub"));
    }

    /**
     * Re-attempts allocation after an initial TLAB allocation failed or was skipped (e.g., due to -XX:-UseTLAB).
     *
     * @param hub the hub of the object to be allocated
     * @param intArrayHub the hub for {@code int[].class}
     */
    @Snippet
    private static Object newInstance(
                    @Parameter("hub") Word hub,
                    @ConstantParameter("intArrayHub") Word intArrayHub,
                    @ConstantParameter("log") boolean log) {
        int sizeInBytes = hub.readInt(klassInstanceSizeOffset());
        if (!forceSlowPath() && inlineContiguousAllocationSupported()) {
            if (hub.readInt(klassStateOffset()) == klassStateFullyInitialized()) {
                Word memory = refillAllocate(intArrayHub, sizeInBytes, log);
                if (memory != Word.zero()) {
                    Word prototypeMarkWord = hub.readWord(prototypeMarkWordOffset());
                    storeWord(memory, 0, markOffset(), prototypeMarkWord);
                    storeWord(memory, 0, hubOffset(), hub);
                    for (int offset = 2 * wordSize(); offset < sizeInBytes; offset += wordSize()) {
                        storeWord(memory, 0, offset, Word.zero());
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
     * @return the newly allocated, uninitialized chunk of memory, or {@link Word#zero()} if the operation was unsuccessful
     */
    static Word refillAllocate(Word intArrayHub, int sizeInBytes, boolean log) {

        Word intArrayMarkWord = Word.fromLong(tlabIntArrayMarkWord());
        int alignmentReserveInBytes = tlabAlignmentReserveInHeapWords() * wordSize();

        Word thread = thread();
        Word top = thread.readWord(threadTlabTopOffset());
        Word end = thread.readWord(threadTlabEndOffset());

        // calculate amount of free space
        Word tlabFreeSpaceInBytes = end.minus(top);

        log(log, "refillTLAB: thread=%p\n", thread.toLong());
        log(log, "refillTLAB: top=%p\n", top.toLong());
        log(log, "refillTLAB: end=%p\n", end.toLong());
        log(log, "refillTLAB: tlabFreeSpaceInBytes=%d\n", tlabFreeSpaceInBytes.toLong());

        // a DIV or SHR operations on Words would be handy here...
        Word tlabFreeSpaceInWords = Word.fromLong(tlabFreeSpaceInBytes.toLong() >>> log2WordSize());

        // Retain TLAB and allocate object in shared space if
        // the amount free in the TLAB is too large to discard.
        Word refillWasteLimit = thread.readWord(tlabRefillWasteLimitOffset());
        if (tlabFreeSpaceInWords.belowOrEqual(refillWasteLimit)) {
            if (tlabStats()) {
                // increment number of refills
                storeInt(thread, 0, tlabNumberOfRefillsOffset(), thread.readInt(tlabNumberOfRefillsOffset()) + 1);
                log(log, "thread: %p -- number_of_refills %d\n", thread.toLong(), thread.readInt(tlabNumberOfRefillsOffset()));
                // accumulate wastage
                Word wastage = thread.readWord(tlabFastRefillWasteOffset()).plus(tlabFreeSpaceInWords);
                log(log, "thread: %p -- accumulated wastage %d\n", thread.toLong(), wastage.toLong());
                storeWord(thread, 0, tlabFastRefillWasteOffset(), wastage);
            }

            // if TLAB is currently allocated (top or end != null) then
            // fill [top, end + alignment_reserve) with array object
            if (top != Word.zero()) {
                int headerSize = arrayBaseOffset(Kind.Int);
                // just like the HotSpot assembler stubs, assumes that tlabFreeSpaceInInts fits in an int
                int tlabFreeSpaceInInts = (int) tlabFreeSpaceInBytes.toLong() >>> 2;
                int length = ((alignmentReserveInBytes - headerSize) >>> 2) + tlabFreeSpaceInInts;
                NewObjectSnippets.formatArray(intArrayHub, -1, length, headerSize, top, intArrayMarkWord, false);

                Word allocated = thread.readWord(threadAllocatedBytesOffset());
                allocated = allocated.plus(top.minus(thread.readWord(threadTlabStartOffset())));
                storeWord(thread, 0, threadAllocatedBytesOffset(), allocated);
            }

            // refill the TLAB with an eden allocation
            Word tlabRefillSizeInWords = thread.readWord(threadTlabSizeOffset());
            Word tlabRefillSizeInBytes = Word.fromLong(tlabRefillSizeInWords.toLong() * wordSize());
            // allocate new TLAB, address returned in top
            top = edenAllocate(tlabRefillSizeInBytes, log);
            if (top != Word.zero()) {
                storeWord(thread, 0, threadTlabStartOffset(), top);
                storeWord(thread, 0, threadTlabTopOffset(), top);

                end = top.plus(tlabRefillSizeInBytes.minus(alignmentReserveInBytes));
                storeWord(thread, 0, threadTlabEndOffset(), end);

                return allocate(sizeInBytes);
            } else {
                return Word.zero();
            }
        } else {
            // Retain TLAB
            Word newRefillWasteLimit = refillWasteLimit.plus(tlabRefillWasteIncrement());
            storeWord(thread, 0, tlabRefillWasteLimitOffset(), newRefillWasteLimit);
            log(log, "refillTLAB: retaining TLAB - newRefillWasteLimit=%p\n", newRefillWasteLimit.toLong());

            if (tlabStats()) {
                storeInt(thread, 0, tlabSlowAllocationsOffset(), thread.readInt(tlabSlowAllocationsOffset()) + 1);
            }

            return edenAllocate(Word.fromInt(sizeInBytes), log);
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
        Word heapTopAddress = Word.fromLong(heapTopAddress());
        Word heapEndAddress = Word.fromLong(heapEndAddress());

        while (true) {
            Word heapTop = heapTopAddress.readWord(0);
            Word newHeapTop = heapTop.plus(sizeInBytes);
            if (newHeapTop.belowOrEqual(heapTop)) {
                return Word.zero();
            }

            Word heapEnd = heapEndAddress.readWord(0);
            if (newHeapTop.above(heapEnd)) {
                return Word.zero();
            }

            if (compareAndSwap(heapTopAddress, 0, heapTop, newHeapTop) == heapTop) {
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
    static void log(boolean enabled, String format, long v1, long v2) {
        if (enabled) {
            Log.printf(format, v1, v2);
        }
    }
    static void log(boolean enabled, String format, long v1, long v2, long v3) {
        if (enabled) {
            Log.printf(format, v1, v2, v3);
        }
    }
}
