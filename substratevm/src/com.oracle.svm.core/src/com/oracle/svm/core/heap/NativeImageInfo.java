/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;

public class NativeImageInfo {

    /*
     * The boundaries of the partitions of the native image heap.
     *
     * These have null values at native image construction, but have relocations that set them
     * correctly in the runtime image.
     */

    // The first object in the read-only primitives partition of the native image heap
    // (or null if there is no such object).
    public static Object firstReadOnlyPrimitiveObject;
    // The last object in the read-only primitives partition of the native image heap
    // (or null if there is no such object).
    public static Object lastReadOnlyPrimitiveObject;

    // The first object in the read-only references partition of the native image heap
    // (or null if there is no such object).
    public static Object firstReadOnlyReferenceObject;
    // The last object in the read-only references partition of the native image heap
    // (or null if there is no such object).
    public static Object lastReadOnlyReferenceObject;

    // The first object in the writable primitives partition of the native image heap
    // (or null if there is no such object).
    public static Object firstWritablePrimitiveObject;
    // The last object in the writable primitives partition of the native image heap
    // (or null if there is no such object).
    public static Object lastWritablePrimitiveObject;

    // The first object in the writable references partition of the native image heap
    // (or null if there is no such object).
    public static Object firstWritableReferenceObject;
    // The last object in the writable references partition of the native image heap
    // (or null if there is no such object).
    public static Object lastWritableReferenceObject;

    /*
     * Convenience methods for asking if a Pointer is in the various native image heap partitions.
     *
     * These test [first .. last] rather than [first .. last), because last is in the partition.
     * These do not test for Pointers *into* the last object in each partition, though methods would
     * be easy to write, but slower.
     */

    public static boolean isInReadOnlyPrimitivePartition(final Pointer ptr) {
        final boolean result = Word.objectToUntrackedPointer(firstReadOnlyPrimitiveObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastReadOnlyPrimitiveObject));
        return result;
    }

    public static boolean isInWritablePrimitivePartition(final Pointer ptr) {
        final boolean result = Word.objectToUntrackedPointer(firstWritablePrimitiveObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastWritablePrimitiveObject));
        return result;
    }

    public static boolean isInReadOnlyReferencePartition(final Pointer ptr) {
        final boolean result = Word.objectToUntrackedPointer(firstReadOnlyReferenceObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastReadOnlyReferenceObject));
        return result;
    }

    public static boolean isInWritableReferencePartition(final Pointer ptr) {
        final boolean result = Word.objectToUntrackedPointer(firstWritableReferenceObject).belowOrEqual(ptr) && ptr.belowOrEqual(Word.objectToUntrackedPointer(lastWritableReferenceObject));
        return result;
    }

    /* Convenience methods taking an Object as a parameter. */

    public static boolean isObjectInReadOnlyPrimitivePartition(Object obj) {
        return isInReadOnlyPrimitivePartition(Word.objectToUntrackedPointer(obj));
    }

    public static boolean isObjectInWritablePrimitivePartition(Object obj) {
        return isInWritablePrimitivePartition(Word.objectToUntrackedPointer(obj));
    }

    public static boolean isObjectInReadOnlyReferencePartition(Object obj) {
        return isInReadOnlyReferencePartition(Word.objectToUntrackedPointer(obj));
    }

    public static boolean isObjectInWritableReferencePartition(Object obj) {
        return isInWritableReferencePartition(Word.objectToUntrackedPointer(obj));
    }

    /*
     * Convenience methods for applying lambdas to each of the partitions of the native image heap.
     *
     * Unfortunately, since lambdas require allocation, these can not be called from allocation-free
     * regions, like the collector.
     */

    public static void voidApplyFromObjects(final VoidReduceFromObjects lambda) {
        lambda.voidFromObjects(firstReadOnlyPrimitiveObject, lastReadOnlyPrimitiveObject);
        lambda.voidFromObjects(firstReadOnlyReferenceObject, lastReadOnlyReferenceObject);
        lambda.voidFromObjects(firstWritablePrimitiveObject, lastWritablePrimitiveObject);
        lambda.voidFromObjects(firstWritableReferenceObject, lastWritableReferenceObject);
    }

    public static void voidApplyFromPointers(final VoidReduceFromPointers lambda) {
        lambda.voidFromPointers(Word.objectToUntrackedPointer(firstReadOnlyPrimitiveObject), Word.objectToUntrackedPointer(lastReadOnlyPrimitiveObject));
        lambda.voidFromPointers(Word.objectToUntrackedPointer(firstReadOnlyReferenceObject), Word.objectToUntrackedPointer(lastReadOnlyReferenceObject));
        lambda.voidFromPointers(Word.objectToUntrackedPointer(firstWritablePrimitiveObject), Word.objectToUntrackedPointer(lastWritablePrimitiveObject));
        lambda.voidFromPointers(Word.objectToUntrackedPointer(firstWritableReferenceObject), Word.objectToUntrackedPointer(lastWritableReferenceObject));
    }

    public static boolean andReduceFromObjects(final BoolReduceFromObjects lambda) {
        boolean result = true;
        result &= lambda.boolFromObjects(firstReadOnlyPrimitiveObject, lastReadOnlyPrimitiveObject);
        result &= lambda.boolFromObjects(firstReadOnlyReferenceObject, lastReadOnlyReferenceObject);
        result &= lambda.boolFromObjects(firstWritablePrimitiveObject, lastWritablePrimitiveObject);
        result &= lambda.boolFromObjects(firstWritableReferenceObject, lastWritableReferenceObject);
        return result;
    }

    public static boolean andReduceFromPointers(final BoolReduceFromPointers lambda) {
        boolean result = true;
        result &= lambda.boolFromPointers(Word.objectToUntrackedPointer(firstReadOnlyPrimitiveObject), Word.objectToUntrackedPointer(lastReadOnlyPrimitiveObject));
        result &= lambda.boolFromPointers(Word.objectToUntrackedPointer(firstReadOnlyReferenceObject), Word.objectToUntrackedPointer(lastReadOnlyReferenceObject));
        result &= lambda.boolFromPointers(Word.objectToUntrackedPointer(firstWritablePrimitiveObject), Word.objectToUntrackedPointer(lastWritablePrimitiveObject));
        result &= lambda.boolFromPointers(Word.objectToUntrackedPointer(firstWritableReferenceObject), Word.objectToUntrackedPointer(lastWritableReferenceObject));
        return result;
    }

    public static boolean orReduceFromObjects(final BoolReduceFromObjects lambda) {
        boolean result = false;
        result |= lambda.boolFromObjects(firstReadOnlyPrimitiveObject, lastReadOnlyPrimitiveObject);
        result |= lambda.boolFromObjects(firstReadOnlyReferenceObject, lastReadOnlyReferenceObject);
        result |= lambda.boolFromObjects(firstWritablePrimitiveObject, lastWritablePrimitiveObject);
        result |= lambda.boolFromObjects(firstWritableReferenceObject, lastWritableReferenceObject);
        return result;
    }

    public static boolean orReduceFromPointers(final BoolReduceFromPointers lambda) {
        boolean result = false;
        result |= lambda.boolFromPointers(Word.objectToUntrackedPointer(firstReadOnlyPrimitiveObject), Word.objectToUntrackedPointer(lastReadOnlyPrimitiveObject));
        result |= lambda.boolFromPointers(Word.objectToUntrackedPointer(firstReadOnlyReferenceObject), Word.objectToUntrackedPointer(lastReadOnlyReferenceObject));
        result |= lambda.boolFromPointers(Word.objectToUntrackedPointer(firstWritablePrimitiveObject), Word.objectToUntrackedPointer(lastWritablePrimitiveObject));
        result |= lambda.boolFromPointers(Word.objectToUntrackedPointer(firstWritableReferenceObject), Word.objectToUntrackedPointer(lastWritableReferenceObject));
        return result;
    }

    /*
     * Interfaces for lambda for native image heap partitions.
     */

    /** Apply to the boundaries of each partition. */
    public interface VoidReduceFromObjects {
        void voidFromObjects(Object first, Object last);
    }

    /** Apply to the boundaries of each partition. */
    public interface VoidReduceFromPointers {
        void voidFromPointers(Pointer first, Pointer last);
    }

    /** Produce a boolean for the boundaries of each partition. */
    public interface BoolReduceFromObjects {
        boolean boolFromObjects(Object first, Object last);
    }

    /** Produce a boolean for the boundaries of each partition. */
    public interface BoolReduceFromPointers {
        boolean boolFromPointers(Pointer first, Pointer last);
    }

    public enum NativeImageHeapRegion {
        READ_ONLY_PRIMITIVE,
        READ_ONLY_REFERENCE,
        WRITABLE_PRIMITIVE,
        WRITABLE_REFERENCE
    }

    public static boolean walkNativeImageHeap(MemoryWalker.Visitor visitor) {
        boolean continueVisiting = true;
        /*
         * Visit each of the regions of the native image heap in turn by visiting with the
         * appropriate access methods. The NativeImageHeapRegion parameter is unused because I have
         * unique access methods for each region.
         */
        if (continueVisiting) {
            final MemoryWalker.NativeImageHeapRegionAccess<NativeImageHeapRegion> access = ImageSingletons.lookup(NativeImageInfo.ReadOnlyPrimitiveMemoryWalkerAccessImpl.class);
            continueVisiting = visitor.visitNativeImageHeapRegion(NativeImageHeapRegion.READ_ONLY_PRIMITIVE, access);
        }
        if (continueVisiting) {
            final MemoryWalker.NativeImageHeapRegionAccess<NativeImageHeapRegion> access = ImageSingletons.lookup(NativeImageInfo.ReadOnlyReferenceMemoryWalkerAccessImpl.class);
            continueVisiting = visitor.visitNativeImageHeapRegion(NativeImageHeapRegion.READ_ONLY_REFERENCE, access);
        }
        if (continueVisiting) {
            final MemoryWalker.NativeImageHeapRegionAccess<NativeImageHeapRegion> access = ImageSingletons.lookup(NativeImageInfo.WritablePrimitiveMemoryWalkerAccessImpl.class);
            continueVisiting = visitor.visitNativeImageHeapRegion(NativeImageHeapRegion.WRITABLE_PRIMITIVE, access);
        }
        if (continueVisiting) {
            final MemoryWalker.NativeImageHeapRegionAccess<NativeImageHeapRegion> access = ImageSingletons.lookup(NativeImageInfo.WritableReferenceMemoryWalkerAccessImpl.class);
            continueVisiting = visitor.visitNativeImageHeapRegion(NativeImageHeapRegion.WRITABLE_REFERENCE, access);
        }
        return continueVisiting;
    }

    public static boolean walkNativeImageHeap(ObjectVisitor visitor) {
        if (!walkNativeImagePartition(NativeImageInfo.firstReadOnlyPrimitiveObject, NativeImageInfo.lastReadOnlyPrimitiveObject, visitor)) {
            return false;
        }
        if (!walkNativeImagePartition(NativeImageInfo.firstReadOnlyReferenceObject, NativeImageInfo.lastReadOnlyReferenceObject, visitor)) {
            return false;
        }
        if (!walkNativeImagePartition(NativeImageInfo.firstWritablePrimitiveObject, NativeImageInfo.lastWritablePrimitiveObject, visitor)) {
            return false;
        }
        if (!walkNativeImagePartition(NativeImageInfo.firstWritableReferenceObject, NativeImageInfo.lastWritableReferenceObject, visitor)) {
            return false;
        }
        return true;
    }

    private static boolean walkNativeImagePartition(Object firstObject, Object lastObject, ObjectVisitor visitor) {
        if ((firstObject == null) || (lastObject == null)) {
            return true;
        }
        final Pointer firstPointer = Word.objectToUntrackedPointer(firstObject);
        final Pointer lastPointer = Word.objectToUntrackedPointer(lastObject);
        Pointer current = firstPointer;
        while (current.belowOrEqual(lastPointer)) {
            final Object currentObject = KnownIntrinsics.convertUnknownValue(current.toObject(), Object.class);
            if (!visitor.visitObject(currentObject)) {
                return false;
            }
            current = LayoutEncoding.getObjectEnd(currentObject);
        }
        return true;
    }

    /** A base class with shared logic for all the MemoryWalkerAccessImpl implementations. */
    protected static class BaseMemoryWalkerAccessImpl {

        /*
         * This looks like the "firstObject" and "lastObject" parameters could be replaced with
         * instance fields, initialized in the constructors for the subclasses and used here. That
         * would not work because the MemoryWalkerAccessImpl instances are created during native
         * image generation at which point I do not know the location of the first and last objects
         * of each region. So, I have to indirect through the variables that are relocated during
         * image loading.
         */

        /** Constructor for subclasses. */
        protected BaseMemoryWalkerAccessImpl() {
            super();
        }

        /** Return the address of the first object of a region as an Unsigned. */
        protected UnsignedWord baseGetStart(Object firstObject) {
            return Word.objectToUntrackedPointer(firstObject);
        }

        /** Return the distance from the start of the first object to the end of the last object. */
        protected UnsignedWord baseGetSize(Object firstObject, Object lastObject) {
            final Pointer firstStart = Word.objectToUntrackedPointer(firstObject);
            final Pointer lastEnd = LayoutEncoding.getObjectEnd(lastObject);
            return lastEnd.subtract(firstStart);
        }
    }

    /** Access methods for the read-only primitive region of the native image heap. */
    protected static class ReadOnlyPrimitiveMemoryWalkerAccessImpl extends BaseMemoryWalkerAccessImpl implements MemoryWalker.NativeImageHeapRegionAccess<NativeImageHeapRegion> {

        /** A constructor used only to make up the singleton instance. */
        @Platforms(Platform.HOSTED_ONLY.class)
        protected ReadOnlyPrimitiveMemoryWalkerAccessImpl() {
            super();
        }

        @Override
        public UnsignedWord getStart(NativeImageHeapRegion region) {
            return baseGetStart(NativeImageInfo.firstReadOnlyPrimitiveObject);
        }

        @Override
        public UnsignedWord getSize(NativeImageHeapRegion region) {
            return baseGetSize(NativeImageInfo.firstReadOnlyPrimitiveObject, NativeImageInfo.lastReadOnlyPrimitiveObject);
        }

        @Override
        public String getRegion(NativeImageHeapRegion region) {
            return "read-only primitives";
        }
    }

    /** Access methods for the read-only reference region of the native image heap. */
    protected static class ReadOnlyReferenceMemoryWalkerAccessImpl extends BaseMemoryWalkerAccessImpl implements MemoryWalker.NativeImageHeapRegionAccess<NativeImageHeapRegion> {

        /** A constructor used only to make up the singleton instance. */
        @Platforms(Platform.HOSTED_ONLY.class)
        protected ReadOnlyReferenceMemoryWalkerAccessImpl() {
            super();
        }

        @Override
        public UnsignedWord getStart(NativeImageHeapRegion region) {
            return baseGetStart(NativeImageInfo.firstReadOnlyReferenceObject);
        }

        @Override
        public UnsignedWord getSize(NativeImageHeapRegion region) {
            return baseGetSize(NativeImageInfo.firstReadOnlyReferenceObject, NativeImageInfo.lastReadOnlyReferenceObject);
        }

        @Override
        public String getRegion(NativeImageHeapRegion region) {
            return "read-only references";
        }
    }

    /** Access methods for the writable primitive region of the native image heap. */
    protected static class WritablePrimitiveMemoryWalkerAccessImpl extends BaseMemoryWalkerAccessImpl implements MemoryWalker.NativeImageHeapRegionAccess<NativeImageHeapRegion> {

        /** A constructor used only to make up the singleton instance. */
        @Platforms(Platform.HOSTED_ONLY.class)
        protected WritablePrimitiveMemoryWalkerAccessImpl() {
            super();
        }

        @Override
        public UnsignedWord getStart(NativeImageHeapRegion region) {
            return baseGetStart(NativeImageInfo.firstWritablePrimitiveObject);
        }

        @Override
        public UnsignedWord getSize(NativeImageHeapRegion region) {
            return baseGetSize(NativeImageInfo.firstWritablePrimitiveObject, NativeImageInfo.lastWritablePrimitiveObject);
        }

        @Override
        public String getRegion(NativeImageHeapRegion region) {
            return "writable primitives";
        }
    }

    /** Access methods for the writable reference region of the native image heap. */
    protected static class WritableReferenceMemoryWalkerAccessImpl extends BaseMemoryWalkerAccessImpl implements MemoryWalker.NativeImageHeapRegionAccess<NativeImageHeapRegion> {

        /** A constructor used only to make up the singleton instance. */
        @Platforms(Platform.HOSTED_ONLY.class)
        protected WritableReferenceMemoryWalkerAccessImpl() {
            super();
        }

        @Override
        public UnsignedWord getStart(NativeImageHeapRegion region) {
            return baseGetStart(NativeImageInfo.firstWritableReferenceObject);
        }

        @Override
        public UnsignedWord getSize(NativeImageHeapRegion region) {
            return baseGetSize(NativeImageInfo.firstWritableReferenceObject, NativeImageInfo.lastWritableReferenceObject);
        }

        @Override
        public String getRegion(NativeImageHeapRegion region) {
            return "writable references";
        }
    }
}

@AutomaticFeature
class MemoryWalkerAccessFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(NativeImageInfo.ReadOnlyPrimitiveMemoryWalkerAccessImpl.class, new NativeImageInfo.ReadOnlyPrimitiveMemoryWalkerAccessImpl());
        ImageSingletons.add(NativeImageInfo.ReadOnlyReferenceMemoryWalkerAccessImpl.class, new NativeImageInfo.ReadOnlyReferenceMemoryWalkerAccessImpl());
        ImageSingletons.add(NativeImageInfo.WritablePrimitiveMemoryWalkerAccessImpl.class, new NativeImageInfo.WritablePrimitiveMemoryWalkerAccessImpl());
        ImageSingletons.add(NativeImageInfo.WritableReferenceMemoryWalkerAccessImpl.class, new NativeImageInfo.WritableReferenceMemoryWalkerAccessImpl());
    }
}
