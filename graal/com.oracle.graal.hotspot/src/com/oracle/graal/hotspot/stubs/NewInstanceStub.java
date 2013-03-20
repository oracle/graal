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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.snippets.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Fold;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.graal.word.*;

/**
 * Stub implementing the fast path for TLAB refill during instance class allocation. This stub is
 * called from the {@linkplain NewObjectSnippets inline} allocation code when TLAB allocation fails.
 * If this stub fails to refill the TLAB or allocate the object, it calls out to the HotSpot C++
 * runtime for to complete the allocation.
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
     * Re-attempts allocation after an initial TLAB allocation failed or was skipped (e.g., due to
     * -XX:-UseTLAB).
     * 
     * @param hub the hub of the object to be allocated
     * @param intArrayHub the hub for {@code int[].class}
     */
    @Snippet
    private static Object newInstance(@Parameter("hub") Word hub, @ConstantParameter("intArrayHub") Word intArrayHub, @ConstantParameter("log") boolean log) {
        int sizeInBytes = hub.readInt(klassInstanceSizeOffset());
        if (!forceSlowPath() && inlineContiguousAllocationSupported()) {
            if (hub.readInt(klassStateOffset()) == klassStateFullyInitialized()) {
                Word memory = refillAllocate(intArrayHub, sizeInBytes, log);
                if (memory.notEqual(0)) {
                    Word prototypeMarkWord = hub.readWord(prototypeMarkWordOffset());
                    memory.writeWord(markOffset(), prototypeMarkWord);
                    memory.writeWord(hubOffset(), hub);
                    for (int offset = 2 * wordSize(); offset < sizeInBytes; offset += wordSize()) {
                        memory.writeWord(offset, Word.zero());
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
        if (useG1GC()) {
            return Word.zero();
        }
        if (!useTLAB()) {
            return edenAllocate(Word.unsigned(sizeInBytes), log);
        }
        Word intArrayMarkWord = Word.unsigned(tlabIntArrayMarkWord());
        int alignmentReserveInBytes = tlabAlignmentReserveInHeapWords() * wordSize();

        Word thread = thread();
        Word top = thread.readWord(threadTlabTopOffset());
        Word end = thread.readWord(threadTlabEndOffset());

        // calculate amount of free space
        Word tlabFreeSpaceInBytes = end.subtract(top);

        log(log, "refillTLAB: thread=%p\n", thread);
        log(log, "refillTLAB: top=%p\n", top);
        log(log, "refillTLAB: end=%p\n", end);
        log(log, "refillTLAB: tlabFreeSpaceInBytes=%d\n", tlabFreeSpaceInBytes);

        Word tlabFreeSpaceInWords = tlabFreeSpaceInBytes.unsignedShiftRight(log2WordSize());

        // Retain TLAB and allocate object in shared space if
        // the amount free in the TLAB is too large to discard.
        Word refillWasteLimit = thread.readWord(tlabRefillWasteLimitOffset());
        if (tlabFreeSpaceInWords.belowOrEqual(refillWasteLimit)) {
            if (tlabStats()) {
                // increment number of refills
                thread.writeInt(tlabNumberOfRefillsOffset(), thread.readInt(tlabNumberOfRefillsOffset()) + 1);
                log(log, "thread: %p -- number_of_refills %d\n", thread, thread.readInt(tlabNumberOfRefillsOffset()));
                // accumulate wastage
                Word wastage = thread.readWord(tlabFastRefillWasteOffset()).add(tlabFreeSpaceInWords);
                log(log, "thread: %p -- accumulated wastage %d\n", thread, wastage);
                thread.writeWord(tlabFastRefillWasteOffset(), wastage);
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

                Word allocated = thread.readWord(threadAllocatedBytesOffset());
                allocated = allocated.add(top.subtract(thread.readWord(threadTlabStartOffset())));
                thread.writeWord(threadAllocatedBytesOffset(), allocated);
            }

            // refill the TLAB with an eden allocation
            Word tlabRefillSizeInWords = thread.readWord(threadTlabSizeOffset());
            Word tlabRefillSizeInBytes = tlabRefillSizeInWords.multiply(wordSize());
            // allocate new TLAB, address returned in top
            top = edenAllocate(tlabRefillSizeInBytes, log);
            if (top.notEqual(0)) {
                thread.writeWord(threadTlabStartOffset(), top);
                thread.writeWord(threadTlabTopOffset(), top);

                end = top.add(tlabRefillSizeInBytes.subtract(alignmentReserveInBytes));
                thread.writeWord(threadTlabEndOffset(), end);

                return allocate(sizeInBytes);
            } else {
                return Word.zero();
            }
        } else {
            // Retain TLAB
            Word newRefillWasteLimit = refillWasteLimit.add(tlabRefillWasteIncrement());
            thread.writeWord(tlabRefillWasteLimitOffset(), newRefillWasteLimit);
            log(log, "refillTLAB: retaining TLAB - newRefillWasteLimit=%p\n", newRefillWasteLimit);

            if (tlabStats()) {
                thread.writeInt(tlabSlowAllocationsOffset(), thread.readInt(tlabSlowAllocationsOffset()) + 1);
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
            Word heapTop = heapTopAddress.readWord(0);
            Word newHeapTop = heapTop.add(sizeInBytes);
            if (newHeapTop.belowOrEqual(heapTop)) {
                return Word.zero();
            }

            Word heapEnd = heapEndAddress.readWord(0);
            if (newHeapTop.aboveThan(heapEnd)) {
                return Word.zero();
            }

            if (compareAndSwap(heapTopAddress, 0, heapTop, newHeapTop).equal(heapTop)) {
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
