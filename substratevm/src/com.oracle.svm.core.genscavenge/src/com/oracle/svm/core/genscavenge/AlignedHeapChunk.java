/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * An AlignedHeapChunk can hold many Objects.
 * <p>
 * This is the key to the chunk-allocated heap: Because these chunks are allocated on aligned
 * boundaries, I can map from a Pointer to (or into) an Object to the AlignedChunk that contains it.
 * From there I can get to the meta-data the AlignedChunk contains, without a table lookup on the
 * Pointer.
 * <p>
 * Most allocation within a AlignedHeapChunk is via fast-path allocation snippets, but a slow-path
 * allocation method is available.
 * <p>
 * Objects in a AlignedHeapChunk have to be promoted by copying from their current HeapChunk to a
 * destination HeapChunk.
 * <p>
 * An AlignedHeapChunk is laid out:
 *
 * <pre>
 * +===============+-------+--------+----------------------+
 * | AlignedHeader | Card  | First  | Object ...           |
 * | Fields        | Table | Object |                      |
 * |               |       | Table  |                      |
 * +===============+-------+--------+----------------------+
 * </pre>
 *
 * The CardTable and the FirstObjectTable and the start of the Objects are just computed addresses.
 * The two tables each need the same fraction of the size of the space for Objects. I conservatively
 * compute them as a fraction of the size of the entire chunk.
 */
public final class AlignedHeapChunk {
    private AlignedHeapChunk() { // all static
    }

    /**
     * Additional fields beyond what is in {@link HeapChunk.Header}.
     *
     * This does <em>not</em> include the card table, or the first object table, and certainly does
     * not include the objects. Those fields are accessed via Pointers that are computed below.
     */
    @RawStructure
    public interface AlignedHeader extends HeapChunk.Header<AlignedHeader> {
    }

    static Pointer getCardTableStart(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getCardTableStartOffset());
    }

    static Pointer getCardTableLimit(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getCardTableLimitOffset());
    }

    static Pointer getFirstObjectTableStart(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getFirstObjectTableStartOffset());
    }

    static Pointer getFirstObjectTableLimit(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getFirstObjectTableLimitOffset());
    }

    static Pointer getObjectsStart(AlignedHeader that) {
        return HeapChunk.asPointer(that).add(getObjectsStartOffset());
    }

    /** Allocate uninitialized memory within this AlignedHeapChunk. */
    static Pointer allocateMemory(AlignedHeader that, UnsignedWord size) {
        Pointer result = WordFactory.nullPointer();
        UnsignedWord available = HeapChunk.availableObjectMemory(that);
        if (size.belowOrEqual(available)) {
            result = HeapChunk.getTopPointer(that);
            Pointer newTop = result.add(size);
            HeapChunk.setTopPointerCarefully(that, newTop);
        }
        return result;
    }

    static UnsignedWord getCommittedObjectMemory(AlignedHeader that) {
        return HeapChunk.getEndOffset(that).subtract(getObjectsStartOffset());
    }

    static AlignedHeader getEnclosingChunk(Object obj) {
        Pointer ptr = Word.objectToUntrackedPointer(obj);
        return getEnclosingChunkFromObjectPointer(ptr);
    }

    static AlignedHeader getEnclosingChunkFromObjectPointer(Pointer ptr) {
        return (AlignedHeader) PointerUtils.roundDown(ptr, HeapPolicy.getAlignedHeapChunkAlignment());
    }

    static void cleanRememberedSet(AlignedHeader that) {
        Pointer cardTableStart = getCardTableStart(that);
        UnsignedWord indexLimit = getCardTableIndexLimitForCurrentTop(that);
        CardTable.cleanTableToIndex(cardTableStart, indexLimit);
    }

    private static UnsignedWord getCardTableIndexLimitForCurrentTop(AlignedHeader that) {
        UnsignedWord memorySize = HeapChunk.getTopOffset(that).subtract(getObjectsStartOffset());
        return CardTable.indexLimitForMemorySize(memorySize);
    }

    @AlwaysInline("GC performance")
    static void setUpRememberedSetForObject(AlignedHeader that, Object obj) {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        assert !HeapChunk.getSpace(that).isYoungSpace();
        /*
         * The card remembered set table should already be clean, but the first object table needs
         * to be set up.
         */
        Pointer fotStart = getFirstObjectTableStart(that);
        Pointer memoryStart = getObjectsStart(that);
        Pointer objStart = Word.objectToUntrackedPointer(obj);
        Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
        FirstObjectTable.setTableForObject(fotStart, memoryStart, objStart, objEnd);
        ObjectHeaderImpl.setRememberedSetBit(obj);
    }

    /** Construct the remembered set for all the objects in this chunk. */
    /*
     * This method must not allocate because I might be in the middle of a collection, or in the
     * middle of detaching a thread, but the @RestrictHeapAccess checker can not prove that because
     * I use an ObjectVisitor which, in other cases, might allocate. Therefore, I whitelist this
     * method.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, reason = "Whitelisted because other ObjectVisitors are allowed to allocate.")
    static void constructRememberedSet(AlignedHeader that) {
        GCImpl.RememberedSetConstructor constructor = GCImpl.getGCImpl().getRememberedSetConstructor();
        constructor.initialize(that);
        HeapChunk.walkObjectsFromInline(that, getObjectsStart(that), constructor);
        constructor.reset();
    }

    /**
     * Dirty the card corresponding to the given Object. This has to be fast, because it is used by
     * the post-write barrier.
     */
    public static void dirtyCardForObject(Object object, boolean verifyOnly) {
        Pointer objectPointer = Word.objectToUntrackedPointer(object);
        AlignedHeader chunk = getEnclosingChunkFromObjectPointer(objectPointer);
        Pointer cardTableStart = getCardTableStart(chunk);
        UnsignedWord index = getObjectIndex(chunk, objectPointer);
        if (verifyOnly) {
            AssertionNode.assertion(false, CardTable.isDirtyEntryAtIndexUnchecked(cardTableStart, index), "card must be dirty", "", "", 0L, 0L);
        } else {
            CardTable.dirtyEntryAtIndex(cardTableStart, index);
        }
    }

    /** Return the offset of an object within the objects part of a chunk. */
    private static UnsignedWord getObjectOffset(AlignedHeader that, Pointer objectPointer) {
        Pointer objectsStart = getObjectsStart(that);
        return objectPointer.subtract(objectsStart);
    }

    /** Return the index of an object within the tables of a chunk. */
    private static UnsignedWord getObjectIndex(AlignedHeader that, Pointer objectPointer) {
        UnsignedWord offset = getObjectOffset(that, objectPointer);
        return CardTable.memoryOffsetToIndex(offset);
    }

    static boolean walkObjects(AlignedHeader that, ObjectVisitor visitor) {
        return HeapChunk.walkObjectsFrom(that, getObjectsStart(that), visitor);
    }

    @AlwaysInline("GC performance")
    static boolean walkObjectsInline(AlignedHeader that, ObjectVisitor visitor) {
        return HeapChunk.walkObjectsFromInline(that, getObjectsStart(that), visitor);
    }

    @Fold
    static UnsignedWord getHeaderSize() {
        return WordFactory.unsigned(SizeOf.get(AlignedHeader.class));
    }

    @Fold
    static UnsignedWord getCardTableStartOffset() {
        UnsignedWord headerSize = getHeaderSize();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Fold
    static UnsignedWord getCardTableSize() {
        UnsignedWord headerSize = getHeaderSize();
        UnsignedWord available = HeapPolicy.getAlignedHeapChunkSize().subtract(headerSize);
        UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(available);
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(requiredSize, alignment);
    }

    @Fold
    static UnsignedWord getCardTableLimitOffset() {
        UnsignedWord tableStart = getCardTableStartOffset();
        UnsignedWord tableSize = getCardTableSize();
        UnsignedWord tableLimit = tableStart.add(tableSize);
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(tableLimit, alignment);
    }

    @Fold
    static UnsignedWord getFirstObjectTableStartOffset() {
        UnsignedWord cardTableLimit = getCardTableLimitOffset();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(cardTableLimit, alignment);
    }

    @Fold
    static UnsignedWord getFirstObjectTableSize() {
        return getCardTableSize();
    }

    @Fold
    static UnsignedWord getFirstObjectTableLimitOffset() {
        UnsignedWord fotStart = getFirstObjectTableStartOffset();
        UnsignedWord fotSize = getFirstObjectTableSize();
        UnsignedWord fotLimit = fotStart.add(fotSize);
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(fotLimit, alignment);
    }

    @Fold
    static UnsignedWord getObjectsStartOffset() {
        UnsignedWord fotLimit = getFirstObjectTableLimitOffset();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(fotLimit, alignment);
    }

    static boolean verify(AlignedHeader that) {
        Log trace = Log.noopLog().string("[AlignedHeapChunk.verify:");
        trace.string("  that: ").hex(that);
        boolean result = true;
        if (result && !HeapChunk.verifyObjects(that, getObjectsStart(that))) {
            result = false;
            Log verifyLog = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[AlignedHeapChunk.verify:");
            verifyLog.string("  identifier: ").hex(that).string("  superclass fails to verify]").newline();
        }
        if (result && !verifyObjectHeaders(that)) {
            result = false;
            Log verifyLog = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[AlignedHeapChunk.verify:");
            verifyLog.string("  identifier: ").hex(that).string("  object headers fail to verify.]").newline();
        }
        if (result && !verifyRememberedSet(that)) {
            result = false;
            Log verifyLog = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[AlignedHeapChunk.verify:");
            verifyLog.string("  identifier: ").hex(that).string("  remembered set fails to verify]").newline();
        }
        trace.string("  returns: ").bool(result);
        trace.string("]").newline();
        return result;
    }

    /** Verify that all the objects have headers that say they are aligned. */
    private static boolean verifyObjectHeaders(AlignedHeader that) {
        Log trace = Log.noopLog().string("[AlignedHeapChunk.verifyObjectHeaders: ").string("  that: ").hex(that);
        Pointer current = getObjectsStart(that);
        while (current.belowThan(HeapChunk.getTopPointer(that))) {
            trace.newline().string("  current: ").hex(current);
            UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(current);
            if (!ObjectHeaderImpl.isAlignedHeader(current, header)) {
                trace.string("  does not have an aligned header: ").hex(header).string("  returns: false").string("]").newline();
                return false;
            }
            /*
             * Step over the object. This does not deal with forwarded objects, but I have already
             * checked that the header is an aligned header.
             */
            current = LayoutEncoding.getObjectEnd(current.toObject());
        }
        trace.string("  returns: true]").newline();
        return true;
    }

    private static boolean verifyRememberedSet(AlignedHeader that) {
        Log trace = Log.noopLog().string("[AlignedHeapChunk.verifyRememberedSet:").string("  that: ").hex(that);
        /* Only chunks in the old from space have a remembered set. */
        HeapImpl heap = HeapImpl.getHeapImpl();
        OldGeneration oldGen = heap.getOldGeneration();
        if (HeapChunk.getSpace(that) == oldGen.getFromSpace()) {
            if (!CardTable.verify(getCardTableStart(that), getFirstObjectTableStart(that), getObjectsStart(that), HeapChunk.getTopPointer(that))) {
                Log verifyLog = heap.getHeapVerifier().getWitnessLog().string("[AlignedHeapChunk.verifyRememberedSet:");
                verifyLog.string("  card table fails to verify").string("]").newline();
                return false;
            }
            if (!FirstObjectTable.verify(getFirstObjectTableStart(that), getObjectsStart(that), HeapChunk.getTopPointer(that))) {
                Log verifyLog = heap.getHeapVerifier().getWitnessLog().string("[AlignedHeapChunk.verifyRememberedSet:");
                verifyLog.string("  first object table fails to verify").string("]").newline();
                return false;
            }
        }
        trace.string("]").newline();
        return true;
    }

    static boolean walkDirtyObjects(AlignedHeader that, ObjectVisitor visitor, boolean clean) {
        Log trace = Log.noopLog().string("[AlignedHeapChunk.walkDirtyObjects:");
        trace.string("  that: ").hex(that).string("  clean: ").bool(clean);
        /* Iterate through the cards looking for dirty cards. */
        Pointer cardTableStart = getCardTableStart(that);
        Pointer fotStart = getFirstObjectTableStart(that);
        Pointer objectsStart = getObjectsStart(that);
        Pointer objectsLimit = HeapChunk.getTopPointer(that);
        UnsignedWord memorySize = objectsLimit.subtract(objectsStart);
        UnsignedWord indexLimit = CardTable.indexLimitForMemorySize(memorySize);
        trace.string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit).string("  indexLimit: ").unsigned(indexLimit);
        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            trace.newline().string("  ").string("  index: ").unsigned(index);
            /* If the card is dirty, visit the objects it covers. */
            if (CardTable.isDirtyEntryAtIndex(cardTableStart, index)) {
                if (clean) {
                    CardTable.cleanEntryAtIndex(cardTableStart, index);
                }
                Pointer cardLimit = CardTable.indexToMemoryPointer(objectsStart, index.add(1));
                Pointer crossingOntoPointer = FirstObjectTable.getPreciseFirstObjectPointer(fotStart, objectsStart, objectsLimit, index);
                Object crossingOntoObject = crossingOntoPointer.toObject();
                if (trace.isEnabled()) {
                    Pointer cardStart = CardTable.indexToMemoryPointer(objectsStart, index);
                    trace.string("    ").string("  cardStart: ").hex(cardStart);
                    trace.string("  cardLimit: ").hex(cardLimit);
                    trace.string("  crossingOntoObject: ").object(crossingOntoObject);
                    trace.string("  end: ").hex(LayoutEncoding.getObjectEnd(crossingOntoObject));
                    if (LayoutEncoding.isArray(crossingOntoObject)) {
                        trace.string("  array length: ").signed(KnownIntrinsics.readArrayLength(crossingOntoObject));
                    }
                }
                trace.newline();
                /*
                 * Iterate through the objects on that card. Find the start of the
                 * imprecisely-marked card.
                 */
                Pointer impreciseStart = FirstObjectTable.getImpreciseFirstObjectPointer(fotStart, objectsStart, objectsLimit, index);
                /*
                 * Walk the objects to the end of an object, even if that is past cardLimit, because
                 * these are imprecise cards.
                 */
                Pointer ptr = impreciseStart;
                Pointer walkLimit = PointerUtils.min(cardLimit, objectsLimit);
                trace.string("    ");
                trace.string("  impreciseStart: ").hex(impreciseStart);
                trace.string("  walkLimit: ").hex(walkLimit);
                while (ptr.belowThan(walkLimit)) {
                    trace.newline().string("      ");
                    trace.string("  ptr: ").hex(ptr);
                    Object obj = ptr.toObject();
                    Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
                    trace.string("  obj: ").object(obj);
                    trace.string("  objEnd: ").hex(objEnd);
                    if (!visitor.visitObjectInline(obj)) {
                        Log failureLog = Log.log().string("[AlignedHeapChunk.walkDirtyObjects:");
                        failureLog.string("  visitor.visitObject fails").string("  obj: ").object(obj).string("]").newline();
                        return false;
                    }
                    ptr = objEnd;
                }
            }
        }
        trace.string("]").newline();
        return true;
    }

    static boolean verifyOnlyCleanCards(AlignedHeader that) {
        Log trace = Log.noopLog().string("[AlignedHeapChunk.verifyOnlyCleanCards:");
        trace.string("  that: ").hex(that);
        boolean result = true;
        Pointer cardTableStart = getCardTableStart(that);
        UnsignedWord indexLimit = getCardTableIndexLimitForCurrentTop(that);
        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            if (CardTable.isDirtyEntryAtIndex(cardTableStart, index)) {
                result = false;
                Log witness = Log.log().string("[AlignedHeapChunk.verifyOnlyCleanCards:");
                witness.string("  that: ").hex(that).string("  dirty card at index: ").unsigned(index).string("]").newline();
            }
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    @Fold
    static MemoryWalker.HeapChunkAccess<AlignedHeapChunk.AlignedHeader> getMemoryWalkerAccess() {
        return ImageSingletons.lookup(AlignedHeapChunk.MemoryWalkerAccessImpl.class);
    }

    /** Methods for a {@link MemoryWalker} to access an aligned heap chunk. */
    static final class MemoryWalkerAccessImpl extends HeapChunk.MemoryWalkerAccessImpl<AlignedHeapChunk.AlignedHeader> {

        @Platforms(Platform.HOSTED_ONLY.class)
        MemoryWalkerAccessImpl() {
        }

        @Override
        public boolean isAligned(AlignedHeapChunk.AlignedHeader heapChunk) {
            return true;
        }

        @Override
        public UnsignedWord getAllocationStart(AlignedHeapChunk.AlignedHeader heapChunk) {
            return getObjectsStart(heapChunk);
        }
    }

    public static final class TestingBackDoor {

        private TestingBackDoor() {
        }

        public static UnsignedWord getFirstObjectTableStartOffset() {
            return AlignedHeapChunk.getFirstObjectTableStartOffset();
        }

        public static UnsignedWord getCardTableStartOffset() {
            return AlignedHeapChunk.getCardTableStartOffset();
        }

        public static UnsignedWord getObjectsStartOffset() {
            return AlignedHeapChunk.getObjectsStartOffset();
        }
    }
}

@AutomaticFeature
class AlignedHeapChunkMemoryWalkerAccessFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.UseCardRememberedSetHeap.getValue();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(AlignedHeapChunk.MemoryWalkerAccessImpl.class, new AlignedHeapChunk.MemoryWalkerAccessImpl());
    }
}
