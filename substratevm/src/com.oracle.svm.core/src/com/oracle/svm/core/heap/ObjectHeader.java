/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.word.Word;
import jdk.graal.compiler.word.WordOperationPlugin;

/**
 * An object header is a reference-sized collection of bits in each object instance. The object
 * header holds a reference to a {@link DynamicHub}, which identifies the class of the instance. It
 * may also contain a couple of reserved bits that encode internal state information (e.g., for the
 * GC).
 *
 * During garbage collection, the object header may hold a forwarding reference to the new location
 * of this instance if the object has been moved by the collector.
 */
public abstract class ObjectHeader {
    protected static final String INLINE_INITIALIZE_HEADER_INIT_REASON = "Methods that write to INIT_LOCATION must be inlined into a caller that emits an ALLOCATION_INIT barrier.";

    @Platforms(Platform.HOSTED_ONLY.class)
    protected ObjectHeader() {
    }

    /**
     * Returns a mask where all reserved bits are set.
     */
    public abstract int getReservedHubBitsMask();

    /**
     * Returns an encoded hub pointer that can be used when writing the object header of an image
     * heap object. Note that the returned value is not necessarily the full object header.
     */
    public abstract long encodeHubPointerForImageHeap(ImageHeapObject obj, long hubOffsetFromHeapBase);

    public abstract Word encodeAsTLABObjectHeader(DynamicHub hub);

    /**
     * Compute an object header of a TLAB object from the offset of the {@link DynamicHub} from the
     * heap base. This is similar to {@link #encodeAsTLABObjectHeader(DynamicHub)}, the difference
     * is that the other method works at runtime with a {@link DynamicHub} object, while this method
     * works at build time.
     */
    public abstract long encodeAsTLABObjectHeader(long hubOffsetFromHeapBase);

    /**
     * If we should constant-fold the header calculation when initializing new objects, this method
     * returns the size of the header, else it returns -1.
     */
    public abstract int constantHeaderSize();

    public abstract Word encodeAsUnmanagedObjectHeader(DynamicHub hub);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract void verifyDynamicHubOffset(long offsetFromHeapBase);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean verifyDynamicHubOffset(DynamicHub hub) {
        long offsetFromHeapBase = Word.objectToUntrackedPointer(hub).subtract(KnownIntrinsics.heapBase()).rawValue();
        verifyDynamicHubOffset(offsetFromHeapBase);
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public DynamicHub dynamicHubFromObjectHeader(Word header) {
        return (DynamicHub) extractPotentialDynamicHubFromHeader(header).toObject();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static DynamicHub readDynamicHubFromObject(Object o) {
        return KnownIntrinsics.readHub(o);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract Word readHeaderFromPointer(Pointer objectPointer);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract Word readHeaderFromObject(Object o);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public DynamicHub readDynamicHubFromPointer(Pointer ptr) {
        Word header = readHeaderFromPointer(ptr);
        return dynamicHubFromObjectHeader(header);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Pointer readPotentialDynamicHubFromPointer(Pointer ptr) {
        Word potentialHeader = readHeaderFromPointer(ptr);
        return extractPotentialDynamicHubFromHeader(potentialHeader);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract Pointer extractPotentialDynamicHubFromHeader(Word header);

    /**
     * Initializes the header of a newly allocated heap object (i.e. writing to
     * {@link LocationIdentity#INIT_LOCATION})
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @AlwaysInline(INLINE_INITIALIZE_HEADER_INIT_REASON)
    public final void initializeHeaderOfNewObjectInit(Pointer ptr, Word header, boolean isArrayLike) {
        initializeObjectHeader(ptr, header, isArrayLike, InitLocationMemWriter.INSTANCE);
    }

    /**
     * Initializes the header of a newly allocated object located off the heap (i.e. writing to
     * {@link NamedLocationIdentity#OFF_HEAP_LOCATION})
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void initializeHeaderOfNewObjectOffHeap(Pointer ptr, Word header, boolean isArrayLike) {
        initializeObjectHeader(ptr, header, isArrayLike, OffHeapLocationMemWriter.INSTANCE);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @AlwaysInline(INLINE_INITIALIZE_HEADER_INIT_REASON)
    protected abstract void initializeObjectHeader(Pointer ptr, Word header, boolean isArrayLike, MemWriter writer);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean pointsToObjectHeader(Pointer ptr) {
        Pointer potentialDynamicHub = readPotentialDynamicHubFromPointer(ptr);
        return isDynamicHub(potentialDynamicHub);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isEncodedObjectHeader(Word potentialHeader) {
        Pointer potentialDynamicHub = extractPotentialDynamicHubFromHeader(potentialHeader);
        return isDynamicHub(potentialDynamicHub);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean hasOptionalIdentityHashField(Word header);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean hasIdentityHashFromAddress(Word header);

    @Uninterruptible(reason = "Prevent a GC interfering with the object's identity hash state.", callerMustBe = true)
    public abstract void setIdentityHashFromAddress(Pointer ptr, Word currentHeader);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean isDynamicHub(Pointer potentialDynamicHub) {
        if (Heap.getHeap().isInImageHeap(potentialDynamicHub) || Metaspace.singleton().isInAllocatedMemory(potentialDynamicHub)) {
            Pointer potentialHubOfDynamicHub = readPotentialDynamicHubFromPointer(potentialDynamicHub);
            return potentialHubOfDynamicHub.equal(Word.objectToUntrackedPointer(DynamicHub.class));
        }
        return false;
    }

    @Fold
    protected static int getReferenceSize() {
        return ConfigurationValues.getObjectLayout().getReferenceSize();
    }

    @Fold
    protected static int getHubOffset() {
        return ConfigurationValues.getObjectLayout().getHubOffset();
    }

    /**
     * Helper interface to write to memory at an overridable {@link LocationIdentity}.
     * <p>
     * {@link #initializeObjectHeader} is used in multiple contexts which write to different memory
     * locations. LocationIdentity arguments to {@link WordOperationPlugin}s are required to be
     * constant at the time of bytecode parsing, meaning it's not possible to {@link Fold} a
     * location identity in any way, it must be a compile-time constant. To avoid duplicating
     * implementations of {@link #initializeObjectHeader}, we get around this by delegating the
     * writing to the MemWriter, whose implementations use different (constant) location identities.
     */
    protected interface MemWriter {
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void writeWord(Pointer ptr, int offset, Word word);

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void writeInt(Pointer ptr, int offset, int val);

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void writeLong(Pointer ptr, int offset, long val);
    }

    private static final class OffHeapLocationMemWriter implements MemWriter {
        private static final OffHeapLocationMemWriter INSTANCE = new OffHeapLocationMemWriter();

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void writeWord(Pointer ptr, int offset, Word word) {
            ptr.writeWord(offset, word, NamedLocationIdentity.OFF_HEAP_LOCATION);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void writeInt(Pointer ptr, int offset, int val) {
            ptr.writeInt(offset, val, NamedLocationIdentity.OFF_HEAP_LOCATION);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void writeLong(Pointer ptr, int offset, long val) {
            ptr.writeLong(offset, val, NamedLocationIdentity.OFF_HEAP_LOCATION);
        }
    }

    private static final class InitLocationMemWriter implements MemWriter {
        private static final InitLocationMemWriter INSTANCE = new InitLocationMemWriter();

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @AlwaysInline(INLINE_INITIALIZE_HEADER_INIT_REASON)
        public void writeWord(Pointer ptr, int offset, Word word) {
            ptr.writeWord(offset, word, LocationIdentity.INIT_LOCATION);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @AlwaysInline(INLINE_INITIALIZE_HEADER_INIT_REASON)
        public void writeInt(Pointer ptr, int offset, int val) {
            ptr.writeInt(offset, val, LocationIdentity.INIT_LOCATION);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @AlwaysInline(INLINE_INITIALIZE_HEADER_INIT_REASON)
        public void writeLong(Pointer ptr, int offset, long val) {
            ptr.writeLong(offset, val, LocationIdentity.INIT_LOCATION);
        }
    }
}
