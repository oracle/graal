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
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
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
 * The Object in an UnalignedHeapChunk can be promoted from one Space to another by moving the
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
 * The HeapChunk fields can be accessed as declared fields, but the card "table" and the location of
 * the Object are just computed as Pointers.
 *
 * In this implementation, I am only implementing precise card remembered sets, so I only need one
 * entry for the whole Object. But for consistency I am treating it as a 1-element table.
 */
public final class UnalignedHeapChunk {
    private UnalignedHeapChunk() { // all static
    }

    /**
     * Additional fields beyond what is in {@link HeapChunk.Header}.
     *
     * This does <em>not</em> include the card remembered set table and certainly does not include
     * the object. Those fields are accessed via Pointers that are computed below.
     */
    @RawStructure
    public interface UnalignedHeader extends HeapChunk.Header<UnalignedHeader> {
    }

    static Pointer getCardTableStart(UnalignedHeader that) {
        return HeapChunk.asPointer(that).add(getCardTableStartOffset());
    }

    static Pointer getCardTableLimit(UnalignedHeader that) {
        return HeapChunk.asPointer(that).add(getCardTableLimitOffset());
    }

    static Pointer getObjectStart(UnalignedHeader that) {
        return HeapChunk.asPointer(that).add(getObjectStartOffset());
    }

    public static UnsignedWord getOverhead() {
        return getObjectStartOffset();
    }

    static UnsignedWord getChunkSizeForObject(UnsignedWord objectSize) {
        UnsignedWord objectStart = getObjectStartOffset();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(objectStart.add(objectSize), alignment);
    }

    /** Allocate uninitialized memory within this AlignedHeapChunk. */
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    public static Pointer allocateMemory(UnalignedHeader that, UnsignedWord size) {
        UnsignedWord available = HeapChunk.availableObjectMemory(that);
        Pointer result = WordFactory.nullPointer();
        if (size.belowOrEqual(available)) {
            result = HeapChunk.getTopPointer(that);
            Pointer newTop = result.add(size);
            HeapChunk.setTopPointerCarefully(that, newTop);
        }
        return result;
    }

    static UnalignedHeader getEnclosingChunk(Object obj) {
        Pointer objPointer = Word.objectToUntrackedPointer(obj);
        return getEnclosingChunkFromObjectPointer(objPointer);
    }

    static UnalignedHeader getEnclosingChunkFromObjectPointer(Pointer objPointer) {
        Pointer chunkPointer = objPointer.subtract(getObjectStartOffset());
        return (UnalignedHeader) chunkPointer;
    }

    public static boolean walkObjects(UnalignedHeader that, ObjectVisitor visitor) {
        return HeapChunk.walkObjectsFrom(that, getObjectStart(that), visitor);
    }

    @AlwaysInline("GC performance")
    public static boolean walkObjectsInline(UnalignedHeader that, ObjectVisitor visitor) {
        return HeapChunk.walkObjectsFromInline(that, getObjectStart(that), visitor);
    }

    public static void dirtyCardForObject(Object obj, boolean verifyOnly) {
        UnalignedHeader chunk = getEnclosingChunk(obj);
        Pointer cardTableStart = getCardTableStart(chunk);
        UnsignedWord objectIndex = getObjectIndex();
        if (verifyOnly) {
            AssertionNode.assertion(false, CardTable.isDirtyEntryAtIndexUnchecked(cardTableStart, objectIndex), "card must be dirty", "", "", 0L, 0L);
        } else {
            CardTable.dirtyEntryAtIndex(cardTableStart, objectIndex);
        }
    }

    static boolean verifyOnlyCleanCards(UnalignedHeader that) {
        Log trace = Log.noopLog().string("[UnalignedHeapChunk.verifyOnlyCleanCards:");
        trace.string("  that: ").hex(that);
        boolean result = true;
        Pointer rememberedSetStart = getCardTableStart(that);
        UnsignedWord objectIndex = getObjectIndex();
        if (CardTable.isDirtyEntryAtIndex(rememberedSetStart, objectIndex)) {
            result = false;
            Log witness = Log.log().string("[UnalignedHeapChunk.verifyOnlyCleanCards:");
            witness.string("  that: ").hex(that).string("  dirty card at index: ").unsigned(objectIndex).string("]").newline();
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    public static boolean walkDirtyObjects(UnalignedHeader that, ObjectVisitor visitor, boolean clean) {
        Log trace = Log.noopLog().string("[UnalignedHeapChunk.walkDirtyObjects:");
        trace.string("  clean: ").bool(clean).string("  that: ").hex(that).string("  ");
        boolean result = true;
        Pointer rememberedSetStart = getCardTableStart(that);
        UnsignedWord objectIndex = getObjectIndex();
        trace.string("  rememberedSetStart: ").hex(rememberedSetStart).string("  objectIndex: ").unsigned(objectIndex);
        if (CardTable.isDirtyEntryAtIndex(rememberedSetStart, objectIndex)) {
            if (clean) {
                CardTable.cleanEntryAtIndex(rememberedSetStart, objectIndex);
            }
            Pointer objectsStart = getObjectStart(that);
            Object obj = objectsStart.toObject();
            trace.string("  obj: ").object(obj);
            if (!visitor.visitObjectInline(obj)) {
                result = false;
            }
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    static void cleanRememberedSet(UnalignedHeader that) {
        Log trace = Log.noopLog().string("[UnalignedHeapChunk.cleanRememberedSet:").newline();
        trace.string("  that: ").hex(that);
        CardTable.cleanTableToPointer(getCardTableStart(that), getCardTableLimit(that));
        trace.string("]").newline();
    }

    static void setUpRememberedSet(UnalignedHeader that) {
        Object obj = getObjectStart(that).toObject();
        ObjectHeaderImpl.setRememberedSetBit(obj);
    }

    @Fold
    static UnsignedWord getCardTableStartOffset() {
        UnsignedWord headerSize = WordFactory.unsigned(SizeOf.get(UnalignedHeader.class));
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Fold
    static UnsignedWord getCardTableSize() {
        UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(WordFactory.unsigned(1));
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
    static UnsignedWord getObjectStartOffset() {
        UnsignedWord cardTableLimitOffset = getCardTableLimitOffset();
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(cardTableLimitOffset, alignment);
    }

    private static UnsignedWord getObjectIndex() {
        return WordFactory.zero();
    }

    public static UnsignedWord getCommittedObjectMemory(UnalignedHeader that) {
        return HeapChunk.getEndOffset(that).subtract(getObjectStartOffset());
    }

    static boolean verify(UnalignedHeader that) {
        return verify(that, getObjectStart(that));
    }

    private static boolean verify(UnalignedHeader that, Pointer start) {
        VMOperation.guaranteeInProgress("Should only be called as a VMOperation.");
        Log trace = HeapVerifier.getTraceLog().string("[UnalignedHeapChunk.verify");
        trace.string("  that: ").hex(that).string("  start: ").hex(start).string("  top: ").hex(HeapChunk.getTopPointer(that)).string("  end: ").hex(HeapChunk.getEndPointer(that)).newline();
        UnsignedWord objHeader = ObjectHeaderImpl.readHeaderFromPointer(start);
        if (ObjectHeaderImpl.isForwardedHeader(objHeader)) {
            Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeapChunk.verify:");
            witness.string("  that: ").hex(that).string("  start: ").hex(start).string("  top: ").hex(HeapChunk.getTopPointer(that)).string("  end: ").hex(HeapChunk.getEndPointer(that));
            witness.string("  space: ").string(HeapChunk.getSpace(that).getName());
            witness.string("  should not be forwarded").string("]").newline();
            return false;
        }
        if (!ObjectHeaderImpl.isUnalignedHeader(start, objHeader)) {
            Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeapChunk.verify:");
            witness.string("  that: ").hex(that).string("  start: ").hex(start).string("  end: ").hex(HeapChunk.getEndPointer(that));
            witness.string("  space: ").string(HeapChunk.getSpace(that).getName());
            witness.string("  obj: ").hex(start).string("  objHeader: ").hex(objHeader);
            witness.string("  does not have an unaligned header").string("]").newline();
            return false;
        }
        Object obj = start.toObject();
        Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
        if (objEnd.notEqual(HeapChunk.getTopPointer(that))) {
            Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeapChunk.verify:");
            witness.string("  that: ").hex(that).string("  start: ").hex(start).string("  end: ").hex(HeapChunk.getEndPointer(that));
            witness.string("  space: ").string(HeapChunk.getSpace(that).getName());
            witness.string("  obj: ").object(obj).string("  objEnd: ").hex(objEnd);
            witness.string("  should be the only object in the chunk").string("]").newline();
            return false;
        }
        if (!verifyRememberedSet(that)) {
            Log witnessLog = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeadChunk remembered set fails to verify");
            witnessLog.string("  that: ").hex(that).string("  remembered set fails to verify.").string("]").newline();
        }
        boolean result = HeapChunk.verifyObjects(that, start);
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private static boolean verifyRememberedSet(UnalignedHeader that) {
        // Only chunks in the old from space have a remembered set.
        OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
        if (HeapChunk.getSpace(that) == oldGen.getFromSpace()) {
            // Check if there are cross-generational pointers ...
            Pointer objStart = getObjectStart(that);
            Object obj = objStart.toObject();
            boolean containsYoungReferences = CardTable.containsReferenceToYoungSpace(obj);
            // ... and if so, that the chunk is marked as dirty.
            if (containsYoungReferences) {
                Pointer rememberedSet = getCardTableStart(that);
                UnsignedWord objectIndex = getObjectIndex();
                boolean isDirty = CardTable.isDirtyEntryAtIndex(rememberedSet, objectIndex);
                if (!isDirty) {
                    Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeapChunk.verify:");
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

    static final class MemoryWalkerAccessImpl extends HeapChunk.MemoryWalkerAccessImpl<UnalignedHeapChunk.UnalignedHeader> {

        @Platforms(Platform.HOSTED_ONLY.class)
        MemoryWalkerAccessImpl() {
        }

        @Override
        public boolean isAligned(UnalignedHeapChunk.UnalignedHeader heapChunk) {
            return false;
        }

        @Override
        public UnsignedWord getAllocationStart(UnalignedHeapChunk.UnalignedHeader heapChunk) {
            return getObjectStart(heapChunk);
        }
    }

    public static final class TestingBackDoor {

        private TestingBackDoor() {
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
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.UseCardRememberedSetHeap.getValue();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(UnalignedHeapChunk.MemoryWalkerAccessImpl.class, new UnalignedHeapChunk.MemoryWalkerAccessImpl());
    }
}
