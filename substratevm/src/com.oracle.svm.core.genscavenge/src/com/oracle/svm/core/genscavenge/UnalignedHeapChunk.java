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

    protected static Pointer getCardTableStart(UnalignedHeader that) {
        return asPointer(that).add(getCardTableStartOffset());
    }

    protected static Pointer getCardTableLimit(UnalignedHeader that) {
        return asPointer(that).add(getCardTableLimitOffset());
    }

    protected static Pointer getObjectStart(UnalignedHeader that) {
        return asPointer(that).add(getObjectStartOffset());
    }

    protected static Pointer getUnalignedStart(UnalignedHeader that) {
        return getObjectStart(that);
    }

    public static UnsignedWord getOverhead() {
        return getObjectStartOffset();
    }

    protected static UnsignedWord getChunkSizeForObject(UnsignedWord objectSize) {
        final UnsignedWord objectStart = getObjectStartOffset();
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(objectStart.add(objectSize), alignment);
    }

    /**
     * Allocate memory within this AlignedHeapChunk. No initialization of the memory happens here.
     */
    @Uninterruptible(reason = "Returns uninitialized memory.", callerMustBe = true)
    public static Pointer allocateMemory(UnalignedHeader that, UnsignedWord size) {
        final UnsignedWord available = availableObjectMemory(that);
        Pointer result = WordFactory.nullPointer();
        if (size.belowOrEqual(available)) {
            result = that.getTop();
            final Pointer newTop = result.add(size);
            setTopCarefully(that, newTop);
        }
        return result;
    }

    protected static UnalignedHeader getEnclosingUnalignedHeapChunk(Object obj) {
        final Pointer objPointer = Word.objectToUntrackedPointer(obj);
        return getEnclosingUnalignedHeapChunkFromPointer(objPointer);
    }

    static UnalignedHeader getEnclosingUnalignedHeapChunkFromPointer(Pointer objPointer) {
        final UnsignedWord startOffset = getObjectStartOffset();
        final Pointer chunkPointer = objPointer.subtract(startOffset);
        return (UnalignedHeader) chunkPointer;
    }

    public static boolean walkObjectsOfUnalignedHeapChunk(UnalignedHeader that, ObjectVisitor visitor) {
        return walkObjectsFrom(that, getUnalignedStart(that), visitor);
    }

    public static void dirtyCardForObjectOfUnalignedHeapChunk(Object obj, boolean verifyOnly) {
        final UnalignedHeader chunk = getEnclosingUnalignedHeapChunk(obj);
        final Pointer cardTableStart = getCardTableStart(chunk);
        final UnsignedWord objectIndex = getObjectIndex();
        if (verifyOnly) {
            AssertionNode.assertion(false, CardTable.isDirtyEntryAtIndexUnchecked(cardTableStart, objectIndex), "card must be dirty", "", "");
        } else {
            CardTable.dirtyEntryAtIndex(cardTableStart, objectIndex);
        }
    }

    static boolean verifyOnlyCleanCardsInUnalignedHeapChunk(UnalignedHeader that) {
        final Log trace = Log.noopLog().string("[UnalignedHeapChunk.verifyOnlyCleanCards:");
        trace.string("  that: ").hex(that);
        boolean result = true;
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

    public static boolean walkDirtyObjectsOfUnalignedHeapChunk(UnalignedHeader that, ObjectVisitor visitor, boolean clean) {
        final Log trace = Log.noopLog().string("[UnalignedHeapChunk.walkDirtyObjects:");
        trace.string("  clean: ").bool(clean).string("  that: ").hex(that).string("  ");
        boolean result = true;
        final Pointer rememberedSetStart = getCardTableStart(that);
        final UnsignedWord objectIndex = getObjectIndex();
        trace.string("  rememberedSetStart: ").hex(rememberedSetStart).string("  objectIndex: ").unsigned(objectIndex);
        if (CardTable.isDirtyEntryAtIndex(rememberedSetStart, objectIndex)) {
            if (clean) {
                CardTable.cleanEntryAtIndex(rememberedSetStart, objectIndex);
            }
            final Pointer objectsStart = getUnalignedStart(that);
            final Object obj = objectsStart.toObject();
            trace.string("  obj: ").object(obj);
            if (!visitor.visitObjectInline(obj)) {
                result = false;
            }
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    protected static void cleanRememberedSetOfUnalignedHeapChunk(UnalignedHeader that) {
        final Log trace = Log.noopLog().string("[UnalignedHeapChunk.cleanRememberedSet:").newline();
        trace.string("  that: ").hex(that);
        CardTable.cleanTableToPointer(getCardTableStart(that), getCardTableLimit(that));
        trace.string("]").newline();
    }

    protected static void setUpRememberedSetOfUnalignedHeapChunk(UnalignedHeader that) {
        final Object obj = getUnalignedStart(that).toObject();
        ObjectHeaderImpl.setRememberedSetBit(obj);
    }

    @Fold
    static UnsignedWord getCardTableStartOffset() {
        final UnsignedWord headerSize = WordFactory.unsigned(SizeOf.get(UnalignedHeader.class));
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Fold
    static UnsignedWord getCardTableSize() {
        final UnsignedWord requiredSize = CardTable.tableSizeForMemorySize(WordFactory.unsigned(1));
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(requiredSize, alignment);
    }

    @Fold
    static UnsignedWord getCardTableLimitOffset() {
        final UnsignedWord tableStart = getCardTableStartOffset();
        final UnsignedWord tableSize = getCardTableSize();
        final UnsignedWord tableLimit = tableStart.add(tableSize);
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(tableLimit, alignment);
    }

    @Fold
    static UnsignedWord getObjectStartOffset() {
        final UnsignedWord cardTableLimitOffset = getCardTableLimitOffset();
        final UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(cardTableLimitOffset, alignment);
    }

    private static UnsignedWord getObjectIndex() {
        return WordFactory.zero();
    }

    public static Pointer getUnalignedHeapChunkStart(UnalignedHeader that) {
        return getObjectStart(that);
    }

    public static UnsignedWord committedObjectMemoryOfUnalignedHeapChunk(UnalignedHeader that) {
        final Pointer start = getUnalignedHeapChunkStart(that);
        final Pointer end = that.getEnd();
        return end.subtract(start);
    }

    static boolean verifyUnalignedHeapChunk(UnalignedHeader that) {
        return verifyUnalignedHeapChunk(that, getUnalignedStart(that));
    }

    private static boolean verifyUnalignedHeapChunk(UnalignedHeader that, Pointer start) {
        VMOperation.guaranteeInProgress("Should only be called as a VMOperation.");
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifier().getTraceLog().string("[UnalignedHeapChunk.verifyUnalignedHeapChunk");
        trace.string("  that: ").hex(that).string("  start: ").hex(start).string("  top: ").hex(that.getTop()).string("  end: ").hex(that.getEnd()).newline();
        final UnsignedWord objHeader = ObjectHeaderImpl.readHeaderFromPointer(start);
        if (ObjectHeaderImpl.isForwardedHeader(objHeader)) {
            final Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeapChunk.verify:");
            witness.string("  that: ").hex(that).string("  start: ").hex(start).string("  top: ").hex(that.getTop()).string("  end: ").hex(that.getEnd());
            witness.string("  space: ").string(that.getSpace().getName());
            witness.string("  should not be forwarded").string("]").newline();
            return false;
        }
        if (!ObjectHeaderImpl.isUnalignedHeader(start, objHeader)) {
            final Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeapChunk.verify:");
            witness.string("  that: ").hex(that).string("  start: ").hex(start).string("  end: ").hex(that.getEnd());
            witness.string("  space: ").string(that.getSpace().getName());
            witness.string("  obj: ").hex(start).string("  objHeader: ").hex(objHeader);
            witness.string("  does not have an unaligned header").string("]").newline();
            return false;
        }
        final Object obj = start.toObject();
        final Pointer objEnd = LayoutEncoding.getObjectEnd(obj);
        if (objEnd.notEqual(that.getTop())) {
            final Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeapChunk.verify:");
            witness.string("  that: ").hex(that).string("  start: ").hex(start).string("  end: ").hex(that.getEnd());
            witness.string("  space: ").string(that.getSpace().getName());
            witness.string("  obj: ").object(obj).string("  objEnd: ").hex(objEnd);
            witness.string("  should be the only object in the chunk").string("]").newline();
            return false;
        }
        if (!verifyRememberedSet(that)) {
            final Log witnessLog = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeadChunk remembered set fails to verify");
            witnessLog.string("  that: ").hex(that).string("  remembered set fails to verify.").string("]").newline();
        }
        final boolean result = verifyObjects(that, start);
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private static boolean verifyRememberedSet(UnalignedHeader that) {
        // Only chunks in the old from space have a remembered set.
        final OldGeneration oldGen = HeapImpl.getHeapImpl().getOldGeneration();
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
                    final Log witness = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[UnalignedHeapChunk.verify:");
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
            return UnalignedHeapChunk.getUnalignedHeapChunkStart(heapChunk);
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
