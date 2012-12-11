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
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
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
        key.add("intArrayHub", intArrayHub);
    }

    /**
     * Re-attempts allocation after an initial TLAB allocation failed or was skipped (e.g., due to -XX:-UseTLAB).
     *
     * @param hub the hub of the object to be allocated
     * @param intArrayHub the hub for {@code int[].class}
     */
    @Snippet
    private static Object newInstance(@Parameter("hub") Word hub, @ConstantParameter("intArrayHub") Word intArrayHub) {
        int sizeInBytes = loadIntFromWord(hub, klassInstanceSizeOffset());
        logf("newInstance: size %d\n", sizeInBytes);
        if (inlineContiguousAllocationSupported()) {
            if (loadIntFromWord(hub, klassStateOffset()) == klassStateFullyInitialized()) {
                Word memory;
                if (refillTLAB(intArrayHub, Word.fromLong(tlabIntArrayMarkWord()), tlabAlignmentReserveInHeapWords() * wordSize())) {
                    memory = allocate(sizeInBytes);
                } else {
                    logf("newInstance: allocating directly in eden\n", 0L);
                    memory = edenAllocate(Word.fromInt(sizeInBytes));
                }
                if (memory != Word.zero()) {
                    logf("newInstance: allocated new object at %p\n", memory.toLong());
                    Word prototypeMarkWord = loadWordFromWord(hub, prototypeMarkWordOffset());
                    storeWord(memory, 0, markOffset(), prototypeMarkWord);
                    storeWord(memory, 0, hubOffset(), hub);
                    for (int offset = 2 * wordSize(); offset < sizeInBytes; offset += wordSize()) {
                        storeWord(memory, 0, offset, Word.zero());
                    }
                    return verifyOop(memory.toObject());
                }
            }
        }
        logf("newInstance: calling new_instance_slow", 0L);
        return verifyOop(NewInstanceSlowStubCall.call(hub));
    }

    /**
     * Attempts to refill the current thread's TLAB.
     *
     * @param intArrayHub the hub for {@code int[].class}
     * @param intArrayMarkWord the mark word for the int array placed in the left over TLAB space
     * @param alignmentReserveInBytes the amount of extra bytes to reserve in a new TLAB
     * @return whether or not a new TLAB was allocated
     */
    static boolean refillTLAB(Word intArrayHub, Word intArrayMarkWord, int alignmentReserveInBytes) {

        Word thread = thread();
        Word top = loadWordFromWord(thread, threadTlabTopOffset());
        Word end = loadWordFromWord(thread, threadTlabEndOffset());

        // calculate amount of free space
        Word tlabFreeSpaceInBytes = end.minus(top);

        logf("refillTLAB: thread=%p\n", thread.toLong());
        logf("refillTLAB: top=%p\n", top.toLong());
        logf("refillTLAB: end=%p\n", end.toLong());
        logf("refillTLAB: tlabFreeSpaceInBytes=%d\n", tlabFreeSpaceInBytes.toLong());

        // a DIV or SHR operations on Words would be handy here...
        Word tlabFreeSpaceInWords = Word.fromLong(tlabFreeSpaceInBytes.toLong() >>> log2WordSize());

        // Retain TLAB and allocate object in shared space if
        // the amount free in the TLAB is too large to discard.
        if (tlabFreeSpaceInWords.belowOrEqual(loadWordFromWord(thread, tlabRefillWasteLimitOffset()))) {
            logf("refillTLAB: discarding TLAB\n", 0L);

            if (tlabStats()) {
                // increment number of refills
                storeInt(thread, 0, tlabNumberOfRefillsOffset(), loadIntFromWord(thread, tlabNumberOfRefillsOffset()) + 1);
                // accumulate wastage
                storeWord(thread, 0, tlabFastRefillWasteOffset(), loadWordFromWord(thread, tlabFastRefillWasteOffset()).plus(tlabFreeSpaceInWords));
            }

            // if TLAB is currently allocated (top or end != null) then
            // fill [top, end + alignment_reserve) with array object
            if (top != Word.zero()) {
                int headerSize = arrayBaseOffset(Kind.Int);
                // just like the HotSpot assembler stubs, assumes that tlabFreeSpaceInInts fits in an int
                int tlabFreeSpaceInInts = (int) tlabFreeSpaceInBytes.toLong() >>> 2;
                int length = ((alignmentReserveInBytes - headerSize) >>> 2) + tlabFreeSpaceInInts;
                logf("refillTLAB: alignmentReserveInBytes %d\n", alignmentReserveInBytes);
                logf("refillTLAB: headerSize %d\n", headerSize);
                logf("refillTLAB: filler.length %d\n", length);
                NewObjectSnippets.formatArray(intArrayHub, -1, length, headerSize, top, intArrayMarkWord, false);

                Word allocated = loadWordFromWord(thread, threadAllocatedBytesOffset());
                allocated = allocated.plus(top.minus(loadWordFromWord(thread, threadTlabStartOffset())));
                storeWord(thread, 0, threadAllocatedBytesOffset(), allocated);
            }

            // refill the TLAB with an eden allocation
            Word tlabRefillSizeInWords = loadWordFromWord(thread, threadTlabSizeOffset());
            Word tlabRefillSizeInBytes = Word.fromLong(tlabRefillSizeInWords.toLong() * wordSize());
            // allocate new TLAB, address returned in top
            top = edenAllocate(tlabRefillSizeInBytes);
            if (top != Word.zero()) {
                storeWord(thread, 0, threadTlabStartOffset(), top);
                storeWord(thread, 0, threadTlabTopOffset(), top);

                end = top.plus(tlabRefillSizeInBytes.minus(alignmentReserveInBytes));
                storeWord(thread, 0, threadTlabEndOffset(), end);
                logf("refillTLAB: top'=%p\n", top.toLong());
                logf("refillTLAB: start'=%p\n", top.toLong());
                logf("refillTLAB: end'=%p\n", end.toLong());
                return true;
            } else {
                return false;
            }
        } else {
            // Retain TLAB
            Word newRefillWasteLimit = loadWordFromWord(thread, tlabRefillWasteLimitOffset()).plus(tlabRefillWasteIncrement());
            storeWord(thread, 0, tlabRefillWasteLimitOffset(), newRefillWasteLimit);
            logf("refillTLAB: retaining TLAB - newRefillWasteLimit=%p\n", newRefillWasteLimit.toLong());

            if (tlabStats()) {
                storeInt(thread, 0, tlabSlowAllocationsOffset(), loadIntFromWord(thread, tlabSlowAllocationsOffset()) + 1);
            }

            return false;
        }
    }

    /**
     * Attempts to allocate a chunk of memory from Eden space.
     *
     * @param sizeInBytes the size of the chunk to allocate
     * @return the allocated chunk or {@link Word#zero()} if allocation fails
     */
    static Word edenAllocate(Word sizeInBytes) {
        Word heapTopAddress = Word.fromLong(heapTopAddress());
        Word heapEndAddress = Word.fromLong(heapEndAddress());
        logf("edenAllocate: heapTopAddress %p\n", heapTopAddress.toLong());
        logf("edenAllocate: heapEndAddress %p\n", heapEndAddress.toLong());

        while (true) {
            Word heapTop = loadWordFromWord(heapTopAddress, 0);
            Word newHeapTop = heapTop.plus(sizeInBytes);
            logf("edenAllocate: heapTop %p\n", heapTop.toLong());
            logf("edenAllocate: newHeapTop %p\n", newHeapTop.toLong());
            if (newHeapTop.belowOrEqual(heapTop)) {
                logf("edenAllocate: fail 1\n", 0L);
                return Word.zero();
            }

            Word heapEnd = loadWordFromWord(heapEndAddress, 0);
            logf("edenAllocate: heapEnd %p\n", heapEnd.toLong());
            if (newHeapTop.above(heapEnd)) {
                logf("edenAllocate: fail 2\n", 0L);
                return Word.zero();
            }

            if (compareAndSwap(heapTopAddress, 0, heapTop, newHeapTop) == heapTop) {
                logf("edenAllocate: success %p\n", heapTop.toLong());
                return heapTop;
            }
        }
    }

    private static final boolean LOGGING_ENABLED = Boolean.getBoolean("graal.logNewInstanceStub");

    private static void logf(String format, long value) {
        if (LOGGING_ENABLED) {
            Log.printf(format, value);
        }
    }
}
