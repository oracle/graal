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

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.nodes.DirectCompareAndSwapNode.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.replacements.NewObjectSnippets.*;
import static com.oracle.graal.hotspot.stubs.StubUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
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
 * called via {@link NewInstanceStubCall} from the {@linkplain NewObjectSnippets inline} allocation
 * code when TLAB allocation fails. If this stub fails to refill the TLAB or allocate the object, it
 * calls out to the HotSpot C++ runtime for to complete the allocation.
 */
public class NewInstanceStub extends SnippetStub {

    public NewInstanceStub(final HotSpotRuntime runtime, Replacements replacements, TargetDescription target, HotSpotForeignCallLinkage linkage) {
        super(runtime, replacements, target, linkage);
    }

    @Override
    protected Arguments makeArguments(SnippetInfo stub) {
        HotSpotResolvedObjectType intArrayType = (HotSpotResolvedObjectType) runtime.lookupJavaType(int[].class);

        // RuntimeStub cannot (currently) support oops or metadata embedded in the code so we
        // convert the hub (i.e., Klass*) for int[] to be a naked word. This should be safe since
        // the int[] class will never be unloaded.
        Constant intArrayHub = intArrayType.klass();
        intArrayHub = Constant.forIntegerKind(graalRuntime().getTarget().wordKind, intArrayHub.asLong(), null);

        Arguments args = new Arguments(stub);
        args.add("hub", null);
        args.addConst("intArrayHub", intArrayHub);
        return args;
    }

    @Fold
    private static boolean logging() {
        return Boolean.getBoolean("graal.logNewInstanceStub");
    }

    /**
     * Re-attempts allocation after an initial TLAB allocation failed or was skipped (e.g., due to
     * -XX:-UseTLAB).
     * 
     * @param hub the hub of the object to be allocated
     * @param intArrayHub the hub for {@code int[].class}
     */
    @Snippet
    private static Object newInstance(Word hub, @ConstantParameter Word intArrayHub) {
        int sizeInBytes = hub.readInt(klassInstanceSizeOffset(), LocationIdentity.FINAL_LOCATION);
        if (!forceSlowPath() && inlineContiguousAllocationSupported()) {
            if (hub.readInt(klassStateOffset(), CLASS_STATE_LOCATION) == klassStateFullyInitialized()) {
                Word memory = refillAllocate(intArrayHub, sizeInBytes, logging());
                if (memory.notEqual(0)) {
                    Word prototypeMarkWord = hub.readWord(prototypeMarkWordOffset(), PROTOTYPE_MARK_WORD_LOCATION);
                    initializeObjectHeader(memory, prototypeMarkWord, hub);
                    for (int offset = 2 * wordSize(); offset < sizeInBytes; offset += wordSize()) {
                        memory.writeWord(offset, Word.zero(), ANY_LOCATION);
                    }
                    return verifyObject(memory.toObject());
                }
            }
        }

        if (logging()) {
            printf("newInstance: calling new_instance_c\n");
        }

        newInstanceC(NEW_INSTANCE_C, thread(), hub);
        handlePendingException(true);
        return verifyObject(getAndClearObjectResult(thread()));
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

        if (log) {
            printf("refillTLAB: thread=%p\n", thread.rawValue());
            printf("refillTLAB: top=%p\n", top.rawValue());
            printf("refillTLAB: end=%p\n", end.rawValue());
            printf("refillTLAB: tlabFreeSpaceInBytes=%d\n", tlabFreeSpaceInBytes.rawValue());
        }

        Word tlabFreeSpaceInWords = tlabFreeSpaceInBytes.unsignedShiftRight(log2WordSize());

        // Retain TLAB and allocate object in shared space if
        // the amount free in the TLAB is too large to discard.
        Word refillWasteLimit = thread.readWord(tlabRefillWasteLimitOffset(), TLAB_REFILL_WASTE_LIMIT_LOCATION);
        if (tlabFreeSpaceInWords.belowOrEqual(refillWasteLimit)) {
            if (tlabStats()) {
                // increment number of refills
                thread.writeInt(tlabNumberOfRefillsOffset(), thread.readInt(tlabNumberOfRefillsOffset(), TLAB_NOF_REFILLS_LOCATION) + 1, TLAB_NOF_REFILLS_LOCATION);
                if (log) {
                    printf("thread: %p -- number_of_refills %d\n", thread.rawValue(), thread.readInt(tlabNumberOfRefillsOffset(), TLAB_NOF_REFILLS_LOCATION));
                }
                // accumulate wastage
                Word wastage = thread.readWord(tlabFastRefillWasteOffset(), TLAB_FAST_REFILL_WASTE_LOCATION).add(tlabFreeSpaceInWords);
                if (log) {
                    printf("thread: %p -- accumulated wastage %d\n", thread.rawValue(), wastage.rawValue());
                }
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
            if (log) {
                printf("refillTLAB: retaining TLAB - newRefillWasteLimit=%p\n", newRefillWasteLimit.rawValue());
            }

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

    public static final ForeignCallDescriptor NEW_INSTANCE_C = descriptorFor(NewInstanceStub.class, "newInstanceC");

    @NodeIntrinsic(StubForeignCallNode.class)
    public static native void newInstanceC(@ConstantNodeParameter ForeignCallDescriptor newInstanceC, Word thread, Word hub);
}
