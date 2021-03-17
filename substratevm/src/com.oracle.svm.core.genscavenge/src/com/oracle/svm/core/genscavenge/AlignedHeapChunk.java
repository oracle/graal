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
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.PointerUtils;

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
 * The CardTable and the FirstObjectTable are both optional and start at computed addresses. The two
 * tables each need the same fraction of the size of the space for Objects. I conservatively compute
 * them as a fraction of the size of the entire chunk.
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

    public static void reset(AlignedHeader chunk) {
        HeapChunk.reset(chunk, AlignedHeapChunk.getObjectsStart(chunk));
        RememberedSet.get().cleanCardTable(chunk);
    }

    public static Pointer getObjectsStart(AlignedHeader that) {
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static UnsignedWord getCommittedObjectMemory(AlignedHeader that) {
        return HeapChunk.getEndOffset(that).subtract(getObjectsStartOffset());
    }

    public static AlignedHeader getEnclosingChunk(Object obj) {
        Pointer ptr = Word.objectToUntrackedPointer(obj);
        return getEnclosingChunkFromObjectPointer(ptr);
    }

    public static AlignedHeader getEnclosingChunkFromObjectPointer(Pointer ptr) {
        return (AlignedHeader) PointerUtils.roundDown(ptr, HeapPolicy.getAlignedHeapChunkAlignment());
    }

    /** Return the offset of an object within the objects part of a chunk. */
    public static UnsignedWord getObjectOffset(AlignedHeader that, Pointer objectPointer) {
        Pointer objectsStart = getObjectsStart(that);
        return objectPointer.subtract(objectsStart);
    }

    static boolean walkObjects(AlignedHeader that, ObjectVisitor visitor) {
        return HeapChunk.walkObjectsFrom(that, getObjectsStart(that), visitor);
    }

    @AlwaysInline("GC performance")
    static boolean walkObjectsInline(AlignedHeader that, ObjectVisitor visitor) {
        return HeapChunk.walkObjectsFromInline(that, getObjectsStart(that), visitor);
    }

    @Fold
    public static UnsignedWord getObjectsStartOffset() {
        return RememberedSet.get().getHeaderSizeOfAlignedChunk();
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
        if (result && !RememberedSet.get().verify(that)) {
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
