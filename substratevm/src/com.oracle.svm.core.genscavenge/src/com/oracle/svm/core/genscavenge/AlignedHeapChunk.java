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
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ObjectHeader;
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
 * An Object in an AlignedHeapChunk can be pinned, by pinning the whole AlignedHeapChunk, so that
 * promotion does not copy the objects in the AlignedHeapChunk, but moves the whole AlignedHeapChunk
 * from one Space to another, that is, without changing the addresses of any of the Objects in the
 * AlignedHeapChunk.
 *
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
 * The HeapChunk fields can be accessed via methods from HeapChunk, or more type-specifically via
 * methods defined here. But the CardTable and the FirstObjectTable and the start of the Objects are
 * just computed addresses. The two tables each need 1/512th of the size of the space for Objects,
 * so I conservatively compute them as 1/512th of the size of the chunk.
 */
public class AlignedHeapChunk extends HeapChunk {

    /**
     * Additional fields beyond what is in {@link HeapChunk.Header}.
     *
     * This does <em>not</em> include the card table, or the first object table, and certainly does
     * not include the objects. Those fields are accessed via Pointers that are computed below.
     */
    @RawStructure
    public interface AlignedHeader extends HeapChunk.Header<AlignedHeader> {
    }

    /*
     * Access to "fields" defined only by their offset in the chunk (past the named fields).
     */
    /** Where is the start of the card table? */
    static Pointer getCardTableStart(AlignedHeader that) {
        return asPointer(that).add(getCardTableStartOffset());
    }

    /** Where is the limit of the card table. */
    static Pointer getCardTableLimit(AlignedHeader that) {
        return asPointer(that).add(getCardTableLimitOffset());
    }

    /** Where is the start of the first object table? */
    static Pointer getFirstObjectTableStart(AlignedHeader that) {
        return asPointer(that).add(getFirstObjectTableStartOffset());
    }

    /** Where is the limit of the first object table? */
    static Pointer getFirstObjectTableLimit(AlignedHeader that) {
        return asPointer(that).add(getFirstObjectTableLimitOffset());
    }

    /** Where is the start of the Objects? */
    static Pointer getObjectsStart(AlignedHeader that) {
        /* The objects start at the limit of the first object table. */
        return asPointer(that).add(getObjectsStartOffset());
    }

    /*
     * This is currently unused, but it is a book-end with getObjectsStart(AlignedHeader) to bracket
     * the Objects.
     */
    /** Where is the limit of the Objects? */
    @SuppressWarnings("unused")
    private static Pointer getObjectsLimit(AlignedHeader that) {
        /* The objects end at the end of the chunk. */
        return that.getEnd();
    }

    /** A well-named method, similar to the field access methods on HeapChunk. */
    static Pointer getAlignedHeapChunkStart(AlignedHeader that) {
        return getObjectsStart(that);
    }

    /**
     * The overhead of an aligned chunk. All of the overhead is before the start of the objects in
     * the chunk.
     */
    public static UnsignedWord getAlignedHeapOverhead() {
        return getObjectsStartOffset();
    }

    /**
     * Allocate memory within this AlignedHeapChunk. No initialization of the memory happens here.
     */
    static Pointer allocateMemory(AlignedHeader that, UnsignedWord size) {
        Pointer result = WordFactory.nullPointer();
        final UnsignedWord available = availableObjectMemory(that);
        /* Is memory available for the requested size? */
        if (size.belowOrEqual(available)) {
            /* Returned memory is at the start, */
            result = that.getTop();
            final Pointer newTop = result.add(size);
            setTopCarefully(that, newTop);
        }
        return result;
    }

    /** The committed object memory is the space between start and end. */
    static UnsignedWord committedObjectMemoryOfAlignedHeapChunk(AlignedHeader that) {
        return that.getEnd().subtract(getAlignedHeapChunkStart(that));
    }

    /** How much space is used for the objects in an AlignedHeapChunk? */
    protected static UnsignedWord usedObjectMemoryOfAlignedHeapChunk(AlignedHeader that) {
        return that.getTop().subtract(getAlignedHeapChunkStart(that));
    }

    /** How much space is still available for allocation in an AlignedHeapChunk? */
    static UnsignedWord availableObjectMemoryOfAlignedHeapChunk(AlignedHeader that) {
        return that.getEnd().subtract(that.getTop());
    }

    /** Return the associated AlignedHeapChunk for a given Object. */
    static AlignedHeader getEnclosingAlignedHeapChunk(Object obj) {
        /* This is where the alignment magic is used. */
        final Pointer ptr = Word.objectToUntrackedPointer(obj);
        return getEnclosingAlignedHeapChunkFromPointer(ptr);
    }

    private static AlignedHeader getEnclosingAlignedHeapChunkFromPointer(Pointer ptr) {
        final Pointer result = PointerUtils.roundDown(ptr, HeapPolicy.getAlignedHeapChunkAlignment());
        return (AlignedHeader) result;
    }

    /** Clean the remembered set for an AlignedHeapChunk. */
    static void cleanRememberedSetOfAlignedHeapChunk(AlignedHeader that) {
        final Log trace = Log.noopLog().string("[AlignedHeapChunk.cleanRememberedSet:");
        trace.string("  that: ").hex(that);
        final Pointer cardTableStart = getCardTableStart(that);
        final Pointer objectsStart = getAlignedHeapChunkStart(that);
        final Pointer objectsLimit = that.getTop();
        final UnsignedWord memorySize = objectsLimit.subtract(objectsStart);
        final UnsignedWord indexLimit = CardTable.indexLimitForMemorySize(memorySize);
        trace.string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit).string("  indexLimit: ").unsigned(indexLimit);
        CardTable.cleanTableToIndex(cardTableStart, indexLimit);
        trace.string("]").newline();
    }

    /**
     * Initialize the remembered set for a particular object, if this chunk has a remembered set.
     */
    static void setUpRememberedSetForObjectOfAlignedHeapChunk(AlignedHeader that, Object obj) {
        VMOperation.guaranteeInProgress("Should only be called from the collector.");
        /*
         * There is only a remembered set maintained in the old To-Space. Testing against the Young
         * space compiles to a test against a constant.
         */
        final HeapImpl heap = HeapImpl.getHeapImpl();
        if (!heap.isYoungGeneration(that.getSpace())) {
            /*
             * The card remembered set table should already be clean, but the first object table
             * needs to be set up.
             */
            final Pointer fotStart = getFirstObjectTableStart(that);
            final Pointer memoryStart = getAlignedHeapChunkStart(that);
            final Pointer objStart = Word.objectToUntrackedPointer(obj);
            /* Interruptible does not apply because I am in the collector. */
            final Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
            FirstObjectTable.setTableForObject(fotStart, memoryStart, objStart, objEnd);
            /* Note that the object is aligned, and that it has a card remembered set. */
            ObjectHeaderImpl.getObjectHeaderImpl().setCardRememberedSetAligned(obj);
        }
    }

    /** Construct the remembered set for all the objects in this chunk. */
    /*
     * This method must not allocate because I might be in the middle of a collection, or in the
     * middle of detaching a thread, but the @RestrictHeapAccess checker can not prove that because
     * I use an ObjectVisitor which, in other cases, might allocate. Therefore, I whitelist this
     * method.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, reason = "Whitelisted because other ObjectVisitors are allowed to allocate.")
    static void constructRememberedSetOfAlignedHeapChunk(AlignedHeader that) {
        final GCImpl.RememberedSetConstructor constructor = getRememberedSetConstructor();
        constructor.initialize(that);
        walkObjectsOfAlignedHeapChunk(that, constructor);
        constructor.reset();
    }

    /** Retrieve the remembered set constructor that is stored in the Heap object. */
    private static GCImpl.RememberedSetConstructor getRememberedSetConstructor() {
        return HeapImpl.getHeapImpl().getGCImpl().getRememberedSetConstructor();
    }

    /**
     * Dirty the card corresponding to the given Object.
     *
     * This has to be fast, because it is used by the post-write barrier.
     */
    public static void dirtyCardForObjectOfAlignedHeapChunk(Object object) {
        final Pointer objectPointer = Word.objectToUntrackedPointer(object);
        final AlignedHeader chunk = getEnclosingAlignedHeapChunkFromPointer(objectPointer);
        final Pointer cardTableStart = getCardTableStart(chunk);
        final UnsignedWord index = getObjectIndex(chunk, objectPointer);
        CardTable.dirtyEntryAtIndex(cardTableStart, index);
    }

    /** Return the offset of an object within the objects part of a chunk. */
    private static UnsignedWord getObjectOffset(AlignedHeader that, Pointer objectPointer) {
        final Pointer objectsStart = getObjectsStart(that);
        assert objectsStart.belowOrEqual(objectPointer);
        assert objectPointer.belowOrEqual(that.getEnd());
        return objectPointer.subtract(objectsStart);
    }

    /** Return the index of an object within the tables of a chunk. */
    private static UnsignedWord getObjectIndex(AlignedHeader that, Pointer objectPointer) {
        final UnsignedWord offset = getObjectOffset(that, objectPointer);
        return CardTable.memoryOffsetToIndex(offset);
    }

    /** Walk the objects in the given chunk, starting from the first object. */
    static boolean walkObjectsOfAlignedHeapChunk(AlignedHeader that, ObjectVisitor visitor) {
        return walkObjectsFrom(that, getAlignedHeapChunkStart(that), visitor);
    }

    /*
     * Private methods for computing offsets within the chunk.
     */

    @Fold
    static UnsignedWord getHeaderSize() {
        return WordFactory.unsigned(SizeOf.get(AlignedHeader.class));
    }

    /** What is the offset of the start of the card table? */
    @Fold
    static UnsignedWord getCardTableStartOffset() {
        /* The card remembered set table starts right after the header fields. */
        final UnsignedWord headerSize = getHeaderSize();
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    /** How big is the card table? */
    @Fold
    static UnsignedWord getCardTableSize() {
        /* How much space is there in the chunk? */
        final UnsignedWord headerSize = getHeaderSize();
        final UnsignedWord available = HeapPolicy.getAlignedHeapChunkSize().subtract(headerSize);
        /* How big should the table be? */
        final UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(available);
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(requiredSize, alignment);
    }

    /** What is the offset of the limit of the card table? */
    @Fold
    static UnsignedWord getCardTableLimitOffset() {
        final UnsignedWord tableStart = getCardTableStartOffset();
        final UnsignedWord tableSize = getCardTableSize();
        final UnsignedWord tableLimit = tableStart.add(tableSize);
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(tableLimit, alignment);
    }

    /** Where does the first object table start? */
    @Fold
    static UnsignedWord getFirstObjectTableStartOffset() {
        /* The first object table starts at the end of the card remembered set table. */
        final UnsignedWord cardTableLimit = getCardTableLimitOffset();
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(cardTableLimit, alignment);
    }

    /** How big is the first object table? */
    @Fold
    static UnsignedWord getFirstObjectTableSize() {
        /* How much space is there in the chunk? */
        final UnsignedWord headerSize = getHeaderSize();
        final UnsignedWord available = HeapPolicy.getAlignedHeapChunkSize().subtract(headerSize);
        /* How big should the table be? */
        final UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(available);
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(requiredSize, alignment);
    }

    /** What is the limit of the first object table? */
    @Fold
    static UnsignedWord getFirstObjectTableLimitOffset() {
        final UnsignedWord fotStart = getFirstObjectTableStartOffset();
        /* How big should the table be? */
        final UnsignedWord fotSize = getFirstObjectTableSize();
        final UnsignedWord fotLimit = fotStart.add(fotSize);
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(fotLimit, alignment);
    }

    /** Where do the objects start? */
    @Fold
    static UnsignedWord getObjectsStartOffset() {
        final UnsignedWord fotLimit = getFirstObjectTableLimitOffset();
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        final UnsignedWord result = UnsignedUtils.roundUp(fotLimit, alignment);
        return result;
    }

    /*
     * Verification.
     */

    static boolean verifyAlignedHeapChunk(AlignedHeader that) {
        final Log trace = Log.noopLog().string("[AlignedHeapChunk.verify:");
        trace.string("  that: ").hex(that);
        boolean result = true;
        /* Verify the superclass structure. */
        if (result && !verifyHeapChunk(that, getAlignedHeapChunkStart(that))) {
            result = false;
            final Log verifyLog = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[AlignedHeapChunk.verify:");
            verifyLog.string("  identifier: ").hex(that).string("  superclass fails to verify]").newline();
        }
        /* AlignedHeapChunks should not be pinned. */
        if (result && that.getPinned()) {
            result = false;
            final Log verifyLog = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[AlignedHeapChunk.verify:");
            verifyLog.string("  identifier: ").hex(that).string("  isPinned.]").newline();
        }
        /* Verify the object headers. */
        if (result && !verifyHeaders(that)) {
            result = false;
            final Log verifyLog = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[AlignedHeapChunk.verify:");
            verifyLog.string("  identifier: ").hex(that).string("  object headers fail to verify.]").newline();
        }
        /* Verify the remembered set. */
        if (result && !verifyRememberedSet(that)) {
            result = false;
            final Log verifyLog = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[AlignedHeapChunk.verify:");
            verifyLog.string("  identifier: ").hex(that).string("  remembered set fails to verify]").newline();
        }
        trace.string("  returns: ").bool(result);
        trace.string("]").newline();
        return result;
    }

    /** Verify that all the objects have headers that say they are aligned. */
    private static boolean verifyHeaders(AlignedHeader that) {
        final Log trace = Log.noopLog().string("[AlignedHeapChunk.verifyHeaders: ").string("  that: ").hex(that);
        /* Get the Object at the offset, or null. */
        Pointer current = getAlignedHeapChunkStart(that);
        while (current.belowThan(that.getTop())) {
            trace.newline().string("  current: ").hex(current);
            final UnsignedWord header = ObjectHeader.readHeaderFromPointer(current);
            if (!ObjectHeaderImpl.getObjectHeaderImpl().isAlignedHeader(header)) {
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

    /** Verify the remembered set of the given chunk. */
    private static boolean verifyRememberedSet(AlignedHeader that) {
        final Log trace = Log.noopLog().string("[AlignedHeapChunk.verifyRememberedSet:").string("  that: ").hex(that);
        /* Only chunks in the old from space have a remembered set. */
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        if (that.getSpace() == oldGen.getFromSpace()) {
            /* Verify the remembered sets themselves. */
            if (!CardTable.verify(getCardTableStart(that), getFirstObjectTableStart(that), getAlignedHeapChunkStart(that), that.getTop())) {
                final Log verifyLog = heap.getHeapVerifierImpl().getWitnessLog().string("[AlignedHeapChunk.verifyRememberedSet:");
                verifyLog.string("  card table fails to verify").string("]").newline();
                return false;
            }
            if (!FirstObjectTable.verify(getFirstObjectTableStart(that), getAlignedHeapChunkStart(that), that.getTop())) {
                final Log verifyLog = heap.getHeapVerifierImpl().getWitnessLog().string("[AlignedHeapChunk.verifyRememberedSet:");
                verifyLog.string("  first object table fails to verify").string("]").newline();
                return false;
            }
        }
        trace.string("]").newline();
        return true;
    }

    /** Walk the dirty Objects in this chunk, passing each to a Visitor. */
    static boolean walkDirtyObjectsOfAlignedHeapChunk(AlignedHeader that, ObjectVisitor visitor, boolean clean) {
        final Log trace = Log.noopLog().string("[AlignedHeapChunk.walkDirtyObjects:");
        trace.string("  that: ").hex(that).string("  clean: ").bool(clean);
        /* Iterate through the cards looking for dirty cards. */
        final Pointer cardTableStart = getCardTableStart(that);
        final Pointer fotStart = getFirstObjectTableStart(that);
        final Pointer objectsStart = getAlignedHeapChunkStart(that);
        final Pointer objectsLimit = that.getTop();
        final UnsignedWord memorySize = objectsLimit.subtract(objectsStart);
        final UnsignedWord indexLimit = CardTable.indexLimitForMemorySize(memorySize);
        trace.string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit).string("  indexLimit: ").unsigned(indexLimit);
        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            trace.newline().string("  ").string("  index: ").unsigned(index);
            /* If the card is dirty, visit the objects it covers. */
            if (CardTable.isDirtyEntryAtIndex(cardTableStart, index)) {
                final Pointer cardLimit = CardTable.indexToMemoryPointer(objectsStart, index.add(1));
                final Pointer crossingOntoPointer = FirstObjectTable.getPreciseFirstObjectPointer(fotStart, objectsStart, objectsLimit, index);
                final Object crossingOntoObject = crossingOntoPointer.toObject();
                if (trace.isEnabled()) {
                    final Pointer cardStart = CardTable.indexToMemoryPointer(objectsStart, index);
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
                final Pointer impreciseStart = FirstObjectTable.getImpreciseFirstObjectPointer(fotStart, objectsStart, objectsLimit, index);
                /*
                 * Walk the objects to the end of an object, even if that is past cardLimit, because
                 * these are imprecise cards.
                 */
                Pointer ptr = impreciseStart;
                final Pointer walkLimit = PointerUtils.min(cardLimit, objectsLimit);
                trace.string("    ");
                trace.string("  impreciseStart: ").hex(impreciseStart);
                trace.string("  walkLimit: ").hex(walkLimit);
                while (ptr.belowThan(walkLimit)) {
                    trace.newline().string("      ");
                    trace.string("  ptr: ").hex(ptr);
                    final Object obj = ptr.toObject();
                    final Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
                    trace.string("  obj: ").object(obj);
                    trace.string("  objEnd: ").hex(objEnd);
                    /* Visit the object. */
                    if (!visitor.visitObjectInline(obj)) {
                        final Log failureLog = Log.log().string("[AlignedHeapChunk.walkDirtyObjects:");
                        failureLog.string("  visitor.visitObject fails").string("  obj: ").object(obj).string("]").newline();
                        return false;
                    }
                    ptr = objEnd;
                }
                if (clean) {
                    CardTable.cleanEntryAtIndex(cardTableStart, index);
                }
            }
        }
        trace.string("]").newline();
        return true;
    }

    /** Verify that there are only clean cards for the given chunk. */
    static boolean verifyOnlyCleanCards(AlignedHeader that) {
        final Log trace = Log.noopLog().string("[AlignedHeapChunk.verifyOnlyCleanCards:");
        trace.string("  that: ").hex(that);
        boolean result = true;
        /* Iterate through the cards looking for dirty cards. */
        final Pointer cardTableStart = getCardTableStart(that);
        final Pointer objectsStart = getAlignedHeapChunkStart(that);
        final Pointer objectsLimit = that.getTop();
        final UnsignedWord memorySize = objectsLimit.subtract(objectsStart);
        final UnsignedWord indexLimit = CardTable.indexLimitForMemorySize(memorySize);
        trace.string("  objectsStart: ").hex(objectsStart).string("  objectsLimit: ").hex(objectsLimit).string("  indexLimit: ").unsigned(indexLimit);
        for (UnsignedWord index = WordFactory.zero(); index.belowThan(indexLimit); index = index.add(1)) {
            if (CardTable.isDirtyEntryAtIndex(cardTableStart, index)) {
                result = false;
                final Log witness = Log.log().string("[AlignedHeapChunk.verifyOnlyCleanCards:");
                witness.string("  that: ").hex(that).string("  dirty card at index: ").unsigned(index).string("]").newline();
            }
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    @Fold
    public static MemoryWalker.HeapChunkAccess<AlignedHeapChunk.AlignedHeader> getMemoryWalkerAccess() {
        return ImageSingletons.lookup(AlignedHeapChunk.MemoryWalkerAccessImpl.class);
    }

    /** Methods for a MemoryWalker to access an aligned heap chunk. */
    protected static final class MemoryWalkerAccessImpl extends HeapChunk.MemoryWalkerAccessImpl<AlignedHeapChunk.AlignedHeader> {

        /** A private constructor used only to make up the singleton instance. */
        @Platforms(Platform.HOSTED_ONLY.class)
        MemoryWalkerAccessImpl() {
            super();
        }

        @Override
        public boolean isAligned(AlignedHeapChunk.AlignedHeader heapChunk) {
            return true;
        }

        @Override
        public UnsignedWord getAllocationStart(AlignedHeapChunk.AlignedHeader heapChunk) {
            return AlignedHeapChunk.getAlignedHeapChunkStart(heapChunk);
        }
    }

    /** Expose some methods that should be protected. */
    public static final class TestingBackDoor {

        private TestingBackDoor() {
            /* No instances. */
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
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(AlignedHeapChunk.MemoryWalkerAccessImpl.class, new AlignedHeapChunk.MemoryWalkerAccessImpl());
    }
}
