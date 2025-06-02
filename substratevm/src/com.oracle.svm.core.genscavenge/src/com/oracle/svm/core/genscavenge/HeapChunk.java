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

import java.util.function.IntUnaryOperator;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawFieldOffset;
import org.graalvm.nativeimage.c.struct.RawFieldAddress;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.identityhashcode.IdentityHashCodeSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.word.Word;

/**
 * The common structure of the chunks of memory which make up the heap. HeapChunks are aggregated
 * into {@linkplain Space spaces}. A specific "subtype" of chunk should be accessed via its own
 * accessor class, such as {@link AlignedHeapChunk}, which provides methods that are specific to the
 * type of chunk and its layout. (Such classes intentionally do not subclass {@link HeapChunk} so to
 * not directly expose its methods.)
 * <p>
 * A HeapChunk is raw memory with a {@linkplain Header} on the beginning that stores bookkeeping
 * information about the HeapChunk. HeapChunks do not have any instance methods: instead they have
 * static methods that take the HeapChunk.Header as a parameter.
 * <p>
 * HeapChunks maintain offsets of the current allocation point (top) with them, and the limit (end)
 * where Objects can be allocated. Subclasses of HeapChunks can add additional fields as needed.
 * <p>
 * HeapChunks maintain some fields that would otherwise have to be maintained in per-HeapChunk
 * memory by the Space that contains them. For example, the fields for linking lists of HeapChunks
 * in a Space is kept in each HeapChunk rather than in some storage outside the HeapChunk.
 * <p>
 * For fields that are maintained as more-specifically-typed offsets by leaf "sub-classes",
 * HeapChunk defines the generic (Pointer) "get" methods, and only the "sub-classes" define "set"
 * methods that store more-specifically-typed Pointers, for type safety.
 * <p>
 * In addition to the declared fields of a HeapChunk.Header, for example, a subtype keeps a card
 * table for the write barrier, but because they are variable-sized, rather than declaring field in
 * the Header, static methods are used to compute Pointers to those "fields".
 * <p>
 * HeapChunks are *not* examined for interior Object references by the collector, though the Objects
 * allocated within the HeapChunk are examined by the collector.
 */
public final class HeapChunk {

    public static final LocationIdentity CHUNK_HEADER_TOP_IDENTITY = NamedLocationIdentity.mutable("ChunkHeader.top");

    private HeapChunk() { // all static
    }

    static class HeaderPaddingSizeProvider implements IntUnaryOperator {
        @Override
        public int applyAsInt(int operand) {
            assert operand == 0 : "padding structure does not declare any fields";
            return SerialAndEpsilonGCOptions.HeapChunkHeaderPadding.getValue();
        }
    }

    @RawStructure(sizeProvider = HeaderPaddingSizeProvider.class)
    private interface HeaderPadding extends PointerBase {
    }

    /**
     * The header of a chunk. All locations are given as offsets relative to the start of this
     * chunk, including the links to the previous and next chunk in the linked list in which this
     * chunk is inserted. This is necessary because the runtime addresses are not yet known for the
     * chunks in the image heap, and relocations need to be avoided.
     */
    @RawStructure
    public interface Header<T extends Header<T>> extends HeaderPadding {
        /**
         * Offset of the memory available for allocation, i.e., the end of the last allocated object
         * in the chunk.
         */
        @RawField
        UnsignedWord getTopOffset(LocationIdentity topIdentity);

        @RawField
        void setTopOffset(UnsignedWord newTop, LocationIdentity topIdentity);

        @RawFieldOffset
        static int offsetOfTopOffset() {
            // replaced
            throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
        }

        /** Offset of the limit of memory available for allocation. */
        @RawField
        @UniqueLocationIdentity
        UnsignedWord getEndOffset();

        @RawField
        @UniqueLocationIdentity
        void setEndOffset(UnsignedWord newEnd);

        /**
         * The Space this HeapChunk is part of.
         *
         * All Space instances are in the native image heap, so it is safe to have a reference to a
         * Java object that the GC does not see.
         */
        @RawField
        @UniqueLocationIdentity
        @PinnedObjectField
        Space getSpace();

        @RawField
        @UniqueLocationIdentity
        @PinnedObjectField
        void setSpace(Space newSpace);

        /**
         * Address offset of the previous HeapChunk relative to this chunk's address in a
         * doubly-linked list of chunks.
         */
        @RawField
        @UniqueLocationIdentity
        SignedWord getOffsetToPreviousChunk();

        @RawField
        @UniqueLocationIdentity
        void setOffsetToPreviousChunk(SignedWord newPrevious);

        /**
         * Address offset of the next HeapChunk relative to this chunk's address in a doubly-linked
         * list of chunks.
         */
        @RawField
        @UniqueLocationIdentity
        SignedWord getOffsetToNextChunk();

        @RawField
        @UniqueLocationIdentity
        void setOffsetToNextChunk(SignedWord newNext);

        @RawField
        UnsignedWord getIdentityHashSalt(LocationIdentity identity);

        @RawField
        void setIdentityHashSalt(UnsignedWord value, LocationIdentity identity);

        @RawField
        int getPinnedObjectCount();

        @RawFieldAddress
        Pointer addressOfPinnedObjectCount();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initialize(Header<?> chunk, Pointer objectsStart, UnsignedWord endOffset) {
        HeapChunk.setEndOffset(chunk, endOffset);
        HeapChunk.setTopPointer(chunk, objectsStart);
        HeapChunk.setSpace(chunk, null);
        HeapChunk.setNext(chunk, Word.nullPointer());
        HeapChunk.setPrevious(chunk, Word.nullPointer());

        /*
         * The epoch is obviously not random, but cheap to use, and we cannot use a random number
         * generator object in all contexts where we are called from, particularly during GC.
         * Together with a good bit mixer function, it seems sufficient.
         */
        HeapChunk.setIdentityHashSalt(chunk, GCImpl.getGCImpl().getCollectionEpoch());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getTopOffset(Header<?> that) {
        assert getTopPointer(that).isNonNull() : "Not safe: top currently points to NULL.";
        return that.getTopOffset(CHUNK_HEADER_TOP_IDENTITY);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getTopPointer(Header<?> that) {
        return asPointer(that).add(that.getTopOffset(CHUNK_HEADER_TOP_IDENTITY));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setTopPointer(Header<?> that, Pointer newTop) {
        // Note that the address arithmetic also works for newTop == NULL, e.g. in TLAB allocation
        that.setTopOffset(newTop.subtract(asPointer(that)), CHUNK_HEADER_TOP_IDENTITY);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setTopPointerCarefully(Header<?> that, Pointer newTop) {
        assert getTopPointer(that).isNonNull() : "Not safe: top currently points to NULL.";
        assert getTopPointer(that).belowOrEqual(newTop) : "newTop too low.";
        assert newTop.belowOrEqual(getEndPointer(that)) : "newTop too high.";
        setTopPointer(that, newTop);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getEndOffset(Header<?> that) {
        return that.getEndOffset();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getEndPointer(Header<?> that) {
        return asPointer(that).add(getEndOffset(that));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setEndOffset(Header<?> that, UnsignedWord newEnd) {
        that.setEndOffset(newEnd);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Space getSpace(Header<?> that) {
        return that.getSpace();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setSpace(Header<?> that, Space newSpace) {
        that.setSpace(newSpace);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends Header<T>> T getPrevious(Header<T> that) {
        return pointerFromOffset(that, that.getOffsetToPreviousChunk());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends Header<T>> void setPrevious(Header<T> that, T newPrevious) {
        that.setOffsetToPreviousChunk(offsetFromPointer(that, newPrevious));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends Header<T>> T getNext(Header<T> that) {
        return pointerFromOffset(that, that.getOffsetToNextChunk());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends Header<T>> void setNext(Header<T> that, T newNext) {
        that.setOffsetToNextChunk(offsetFromPointer(that, newNext));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getIdentityHashSalt(Header<?> that) {
        return that.getIdentityHashSalt(IdentityHashCodeSupport.IDENTITY_HASHCODE_SALT_LOCATION);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setIdentityHashSalt(Header<?> that, UnsignedWord value) {
        that.setIdentityHashSalt(value, IdentityHashCodeSupport.IDENTITY_HASHCODE_SALT_LOCATION);
    }

    /**
     * Converts from an offset to a pointer, where a zero offset translates to {@code NULL}. This is
     * necessary for treating image heap chunks, where addresses at runtime are not yet known.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressWarnings("unchecked")
    private static <T extends PointerBase> T pointerFromOffset(Header<?> that, ComparableWord offset) {
        T pointer = Word.nullPointer();
        if (offset.notEqual(Word.zero())) {
            pointer = (T) ((SignedWord) that).add((SignedWord) offset);
        }
        return pointer;
    }

    /**
     * Converts from a pointer to an offset, where {@code NULL} translates to zero. This method is
     * used only at runtime and in contexts where technically, special treatment of {@code NULL} is
     * not necessary because it would be covered by the arithmetic, but it is done for consistency.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static SignedWord offsetFromPointer(Header<?> that, PointerBase pointer) {
        SignedWord offset = Word.zero();
        if (pointer.isNonNull()) {
            offset = ((SignedWord) pointer).subtract((SignedWord) that);
        }
        return offset;
    }

    @NeverInline("Not performance critical")
    @Uninterruptible(reason = "Forced inlining (StoredContinuation objects must not move).")
    public static void walkObjectsFrom(Header<?> that, Pointer start, ObjectVisitor visitor) {
        walkObjectsFromInline(that, start, visitor);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Forced inlining (StoredContinuation objects must not move).", callerMustBe = true)
    public static void walkObjectsFromInline(Header<?> that, Pointer start, ObjectVisitor visitor) {
        Pointer p = start;
        while (p.belowThan(getTopPointer(that))) { // crucial: top can move, so always re-read
            Object obj = p.toObjectNonNull();
            callVisitor(visitor, obj);
            p = p.add(LayoutEncoding.getSizeFromObjectInlineInGC(obj));
        }
    }

    @AlwaysInline("de-virtualize calls to ObjectReferenceVisitor")
    @Uninterruptible(reason = "Bridge between uninterruptible and potentially interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static void callVisitor(ObjectVisitor visitor, Object obj) {
        visitor.visitObject(obj);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord availableObjectMemory(Header<?> that) {
        return that.getEndOffset().subtract(that.getTopOffset(CHUNK_HEADER_TOP_IDENTITY));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer asPointer(Header<?> that) {
        return (Pointer) that;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static HeapChunk.Header<?> getEnclosingHeapChunk(Object obj) {
        if (!GraalDirectives.inIntrinsic()) {
            assert !ObjectHeaderImpl.isPointerToForwardedObject(Word.objectToUntrackedPointer(obj)) : "Forwarded objects must be a pointer and not an object";
        }
        if (ObjectHeaderImpl.isAlignedObject(obj)) {
            return AlignedHeapChunk.getEnclosingChunk(obj);
        } else {
            if (!GraalDirectives.inIntrinsic()) {
                assert ObjectHeaderImpl.isUnalignedObject(obj);
            }
            return UnalignedHeapChunk.getEnclosingChunk(obj);
        }
    }

    public static HeapChunk.Header<?> getEnclosingHeapChunk(Pointer ptrToObj, UnsignedWord header) {
        if (ObjectHeaderImpl.isAlignedHeader(header)) {
            return AlignedHeapChunk.getEnclosingChunkFromObjectPointer(ptrToObj);
        } else {
            return UnalignedHeapChunk.getEnclosingChunkFromObjectPointer(ptrToObj);
        }
    }
}
