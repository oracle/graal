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
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.UnsignedUtils;

/**
 * An UnalignedHeapChunk holds exactly one Object.
 * <p>
 * An UnalignedHeapChunk does not have a way to map from a Pointer to (or into) the Object they
 * contain to the UnalignedHeapChunk that contains them.
 * <p>
 * An Object in a UnalignedHeapChunk needs to have a bit set in its DynamicHub to identify it as an
 * Object in a UnalignedHeapChunk, so things like write-barriers don't try to update meta-data. Also
 * so things like the getEnclosingHeapChunk(Object) can tell that the object is in an
 * UnalignedHeapChunk.
 * <p>
 * Only a slow-path allocation method is available for UnalignedHeapChunks. This is acceptable
 * because UnalignedHeapChunks are for large objects, so the cost of initializing the object dwarfs
 * the cost of slow-path allocation.
 * <p>
 * The Object in a UnalignedHeapChunk can be promoted from one Space to another by moving the
 * UnalignedHeapChunk from one Space to the other, rather than copying the Object out of the
 * HeapChunk in one Space and into a destination HeapChunk in the other Space. That saves some
 * amount of copying cost for these large objects.
 *
 * An UnalignedHeapChunk is laid out:
 *
 * <pre>
 * +=================+-------+-------------------------------------+
 * | UnalignedHeader | Card  | Object                              |
 * | Fields          | Table |                                     |
 * +=================+-------+-------------------------------------+
 * </pre>
 *
 * The HeapChunk fields and the isPinned field can be accessed as declared fields, but the card
 * "table" and the location of the Object are just computed as Pointers.
 *
 * In this implementation, I am only implementing precise card remembered sets, so I only need one
 * entry for the whole Object. But for consistency I am treating it as a 1-element table.
 */
public class UnalignedHeapChunk extends HeapChunk {

    /**
     * Additional fields beyond what is in {@link HeapChunk.Header}.
     *
     * This does <em>not</em> include the card remembered set table and certainly does not include
     * the object. Those fields are accessed via Pointers that are computed below.
     */
    @RawStructure
    public interface UnalignedHeader extends HeapChunk.Header<UnalignedHeader> {
    }

    /*
     * Access to fields defined only by their offset in the chunk (past the Header).
     */

    /** Where is the start of the card table? */
    protected static Pointer getCardTableStart(UnalignedHeader that) {
        return asPointer(that).add(getCardTableStartOffset());
    }

    /** Where is the limit of the card table. */
    protected static Pointer getCardTableLimit(UnalignedHeader that) {
        return asPointer(that).add(getCardTableLimitOffset());
    }

    /** Where is the start of the Object? */
    protected static Pointer getObjectStart(UnalignedHeader that) {
        return asPointer(that).add(getObjectStartOffset());
    }

    /** A well-named method, similar to the field access methods on HeapChunk. */
    protected static Pointer getUnalignedStart(UnalignedHeader that) {
        return getObjectStart(that);
    }

    /**
     * The overhead of an unaligned chunk. All of the overhead is before the start of the object in
     * the chunk.
     */
    public static UnsignedWord getUnalignedHeapOverhead() {
        return getObjectStartOffset();
    }

    @SuppressWarnings("unused")
    // This is currently unused, but it is the other book-end with
    // getObjectsStart(UnalignedHeader that) to bracket the Objects.
    /** Where is the limit of the Objects? */
    private static Pointer getObjectsLimit(UnalignedHeader that) {
        // The objects end at the end of the chunk.
        return that.getEnd();
    }

    /** How large an UnalignedHeapChunk is needed to hold an object of the given size? */
    protected static UnsignedWord getChunkSizeForObject(UnsignedWord objectSize) {
        final UnsignedWord objectStart = getObjectStartOffset();
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        final UnsignedWord result = UnsignedUtils.roundUp(objectStart.add(objectSize), alignment);
        return result;
    }

    /*
     * Methods on UnalignedHeapChunk.
     */

    /**
     * Allocate memory within this AlignedHeapChunk. No initialization of the memory happens here.
     */
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    public static Pointer allocateMemory(UnalignedHeader that, UnsignedWord size) {
        final UnsignedWord available = availableObjectMemory(that);
        // Is memory available for the requested size?
        Pointer result = WordFactory.nullPointer();
        if (size.belowOrEqual(available)) {
            // Returned memory is at the start,
            result = that.getTop();
            final Pointer newTop = result.add(size);
            setTopCarefully(that, newTop);
        }
        return result;
    }

    /** Map from an object to a header for the enclosing chunk. */
    protected static UnalignedHeader getEnclosingUnalignedHeapChunk(Object obj) {
        final Pointer objPointer = Word.objectToUntrackedPointer(obj);
        return getEnclosingUnalignedHeapChunkFromPointer(objPointer);
    }

    /** Map from a Pointer to an object to the enclosing chunk. */
    private static UnalignedHeader getEnclosingUnalignedHeapChunkFromPointer(Pointer objPointer) {
        // This only works because there is only one object in an unaligned chunk.
        // Where does the object start in an unaligned chunk?
        final UnsignedWord startOffset = getObjectStartOffset();
        // Back the Object pointer up to the beginning of the UnalignedHeapChunk.
        final Pointer chunkPointer = objPointer.subtract(startOffset);
        final UnalignedHeader result = (UnalignedHeader) chunkPointer;
        return result;
    }

    /** Walk the objects in the given chunk, starting from the first object. */
    public static boolean walkObjectsOfUnalignedHeapChunk(UnalignedHeader that, ObjectVisitor visitor) {
        return walkObjectsFrom(that, getUnalignedStart(that), visitor);
    }

    /**
     * Dirty the card corresponding to the given Object.
     *
     * This has to be fast, because it is used by the post-write barrier.
     */
    public static void dirtyCardForObjectOfUnalignedHeapChunk(Object obj) {
        final UnalignedHeader chunk = getEnclosingUnalignedHeapChunk(obj);
        final Pointer rememberedSetStart = getCardTableStart(chunk);
        final UnsignedWord objectIndex = getObjectIndex();
        CardTable.dirtyEntryAtIndex(rememberedSetStart, objectIndex);
    }

    /** Verify that there are only clean cards in the remembered set of the given chunk. */
    static boolean verifyOnlyCleanCardsOfUnalignedHeapChunk(UnalignedHeader that) {
        final Log trace = Log.noopLog().string("[UnalignedHeapChunk.verifyOnlyCleanCards:");
        trace.string("  that: ").hex(that);
        boolean result = true;
        // Iterate through the cards looking for dirty cards.
        final Pointer rememberedSetStart = getCardTableStart(that);
        final UnsignedWord objectIndex = getObjectIndex();
        if (CardTable.isDirtyEntryAtIndex(rememberedSetStart, objectIndex)) {
            result = false;
            final Log witness = Log.log().string("[UnalignedHeapChunk.verifyOnlyCleanCards:");
            witness.string("  that: ").hex(that).string("  dirty card at index: ").unsigned(objectIndex).string("]").newline();
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    /** Walk the dirty Objects in this chunk, passing each to a Visitor. */
    public static boolean walkDirtyObjectsOfUnalignedHeapChunk(UnalignedHeader that, ObjectVisitor visitor, boolean clean) {
        final Log trace = Log.noopLog().string("[UnalignedHeapChunk.walkDirtyObjects:");
        trace.string("  clean: ").bool(clean).string("  that: ").hex(that).string("  ");
        boolean result = true;
        final Pointer rememberedSetStart = getCardTableStart(that);
        final UnsignedWord objectIndex = getObjectIndex();
        trace.string("  rememberedSetStart: ").hex(rememberedSetStart).string("  objectIndex: ").unsigned(objectIndex);
        // If the card for this chunk is dirty, visit the object.
        if (CardTable.isDirtyEntryAtIndex(rememberedSetStart, objectIndex)) {
            final Pointer objectsStart = getUnalignedStart(that);
            final Object obj = objectsStart.toObject();
            trace.string("  obj: ").object(obj);
            // Visit the object.
            if (!visitor.visitObjectInline(obj)) {
                result = false;
            }
            if (clean) {
                CardTable.cleanEntryAtIndex(rememberedSetStart, objectIndex);
            }
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    /** Clean the remembered set for the given chunk. */
    protected static void cleanRememberedSetOfUnalignedHeapChunk(UnalignedHeader that) {
        final Log trace = Log.noopLog().string("[UnalignedHeapChunk.cleanRememberedSet:").newline();
        trace.string("  that: ").hex(that);
        CardTable.cleanTableToPointer(getCardTableStart(that), getCardTableLimit(that));
        trace.string("]").newline();
    }

    /** Set up the remembered set for the Object in this chunk. */
    protected static void setUpRememberedSetOfUnalignedHeapChunk(UnalignedHeader that) {
        // There is only one object in this chunk.
        final Object obj = getUnalignedStart(that).toObject();
        final ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
        // Mark the object header to say it has a remembered set.
        ohi.setUnaligned(obj);
    }

    /*
     * Private methods for computing offsets within the chunk.
     */

    /** What is the offset of the start of the card table? */
    @Fold
    static UnsignedWord getCardTableStartOffset() {
        // The card remembered set table starts right after the header fields.
        final UnsignedWord headerSize = WordFactory.unsigned(SizeOf.get(UnalignedHeader.class));
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    /** How big is the card table? */
    @Fold
    static UnsignedWord getCardTableSize() {
        // A "table" of one entry.
        final UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(WordFactory.unsigned(1));
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

    /** Where does the Object start? */
    @Fold
    static UnsignedWord getObjectStartOffset() {
        final UnsignedWord cardTableLimitOffset = getCardTableLimitOffset();
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        final UnsignedWord result = UnsignedUtils.roundUp(cardTableLimitOffset, alignment);
        return result;
    }

    // TODO: Unused. Are there places it should be used?
    @SuppressWarnings("unused")
    private static UnsignedWord getObjectOffset() {
        // The object in an unaligned chunk is always at offset 0.
        return WordFactory.zero();
    }

    private static UnsignedWord getObjectIndex() {
        // The object in an unaligned chunk is always at index 0 in the side tables.
        return WordFactory.zero();
    }

    /*
     * Size-related methods.
     */

    /** A well-named method, similar to the field access methods on HeapChunk. */
    public static Pointer getUnalignedHeapChunkStart(UnalignedHeader that) {
        return getObjectStart(that);
    }

    /** The committed object memory is the space between start and end. */
    public static UnsignedWord committedObjectMemoryOfUnalignedHeapChunk(UnalignedHeader that) {
        final Pointer start = getUnalignedHeapChunkStart(that);
        final Pointer end = that.getEnd();
        final UnsignedWord result = end.subtract(start);
        return result;
    }

    /** How much space is used for the objects in an UnalignedHeapChunk? */
    public static UnsignedWord usedObjectMemoryOfUnalignedHeapChunk(UnalignedHeader that) {
        final Pointer start = getUnalignedHeapChunkStart(that);
        final Pointer top = that.getTop();
        return top.subtract(start);
    }

    /*
     * Verification.
     */

    static boolean verifyUnalignedHeapChunk(UnalignedHeader that) {
        return verifyUnalignedHeapChunk(that, getUnalignedStart(that));
    }

    /** Verify an UnalignedHeapChunk, called from sub-class verify methods. */
    private static boolean verifyUnalignedHeapChunk(UnalignedHeader that, Pointer start) {
        VMOperation.guaranteeInProgress("Should only be called as a VMOperation.");
        // The object in this chunk should not be forwarded,
        // and should be the only object in the chunk.
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[UnalignedHeapChunk.verifyUnalignedHeapChunk");
        trace.string("  that: ").hex(that).string("  start: ").hex(start).string("  top: ").hex(that.getTop()).string("  end: ").hex(that.getEnd()).newline();
        final UnsignedWord objHeader = ObjectHeader.readHeaderFromPointer(start);
        final ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
        // The object should not be forwarded.
        if (ohi.isForwardedHeader(objHeader)) {
            final Log witness = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[UnalignedHeapChunk.verify:");
            witness.string("  that: ").hex(that).string("  start: ").hex(start).string("  top: ").hex(that.getTop()).string("  end: ").hex(that.getEnd());
            witness.string("  space: ").string(that.getSpace().getName());
            witness.string("  objHeader: ").string(ohi.toStringFromHeader(objHeader));
            witness.string("  should not be forwarded").string("]").newline();
            return false;
        }
        // The object should be marked as being unaligned.
        if (!ohi.isUnalignedHeader(objHeader)) {
            final Log witness = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[UnalignedHeapChunk.verify:");
            witness.string("  that: ").hex(that).string("  start: ").hex(start).string("  end: ").hex(that.getEnd());
            witness.string("  space: ").string(that.getSpace().getName());
            witness.string("  obj: ").hex(start).string("  objHeader: ").hex(objHeader);
            witness.string("  does not have an unaligned header").string("]").newline();
            return false;
        }
        // The object should be the only object in the chunk.
        final Object obj = start.toObject();
        final Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
        if (objEnd.notEqual(that.getTop())) {
            final Log witness = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[UnalignedHeapChunk.verify:");
            witness.string("  that: ").hex(that).string("  start: ").hex(start).string("  end: ").hex(that.getEnd());
            witness.string("  space: ").string(that.getSpace().getName());
            witness.string("  obj: ").object(obj).string("  objEnd: ").hex(objEnd);
            witness.string("  should be the only object in the chunk").string("]").newline();
            return false;
        }
        if (!verifyRememberedSet(that)) {
            final Log witnessLog = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[UnalignedHeadChunk remembered set fails to verify");
            witnessLog.string("  that: ").hex(that).string("  remembered set fails to verify.").string("]").newline();
        }
        // Verify the super-class.
        final boolean result = verifyHeapChunk(that, start);
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    /** Verify the remembered set of the given chunk. */
    private static boolean verifyRememberedSet(UnalignedHeader that) {
        // Only chunks in the old from space have a remembered set.
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final OldGeneration oldGen = heap.getOldGeneration();
        if (that.getSpace() == oldGen.getFromSpace()) {
            // Check if there are cross-generational pointers ...
            final Pointer objStart = getUnalignedStart(that);
            final Object obj = objStart.toObject();
            final boolean containsYoungReferences = CardTable.containsReferenceToYoungSpace(obj);
            // ... and if so, that the chunk is marked as dirty.
            if (containsYoungReferences) {
                final Pointer rememberedSet = getCardTableStart(that);
                final UnsignedWord objectIndex = getObjectIndex();
                final boolean isDirty = CardTable.isDirtyEntryAtIndex(rememberedSet, objectIndex);
                if (!isDirty) {
                    final Log witness = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[UnalignedHeapChunk.verify:");
                    witness.string("  that: ").hex(that);
                    witness.string("  containsYoungReferences implies isDirty").string("]").newline();
                    return false;
                }
            }
        }
        return true;
    }

    @Fold
    public static MemoryWalker.HeapChunkAccess<UnalignedHeapChunk.UnalignedHeader> getMemoryWalkerAccess() {
        return ImageSingletons.lookup(UnalignedHeapChunk.MemoryWalkerAccessImpl.class);
    }

    /** Implementation of methods from HeapChunk.MemoryWalkerAccessImpl. */
    static final class MemoryWalkerAccessImpl extends HeapChunk.MemoryWalkerAccessImpl<UnalignedHeapChunk.UnalignedHeader> {

        /** A private constructor used only to make up the singleton instance. */
        @Platforms(Platform.HOSTED_ONLY.class)
        MemoryWalkerAccessImpl() {
            super();
        }

        @Override
        public boolean isAligned(UnalignedHeapChunk.UnalignedHeader heapChunk) {
            return false;
        }

        @Override
        public UnsignedWord getAllocationStart(UnalignedHeapChunk.UnalignedHeader heapChunk) {
            return UnalignedHeapChunk.getUnalignedHeapChunkStart(heapChunk);
        }
    }

    /** Expose some methods that should be protected. */
    public static final class TestingBackDoor {

        private TestingBackDoor() {
            // No instances.
        }

        public static UnsignedWord getCardTableStartOffset() {
            return UnalignedHeapChunk.getCardTableStartOffset();
        }

        public static UnsignedWord getObjectStartOffset() {
            return UnalignedHeapChunk.getObjectStartOffset();
        }
    }
}

@AutomaticFeature
class UnalignedHeapChunkMemoryWalkerAccessFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(UnalignedHeapChunk.MemoryWalkerAccessImpl.class, new UnalignedHeapChunk.MemoryWalkerAccessImpl());
    }
}
