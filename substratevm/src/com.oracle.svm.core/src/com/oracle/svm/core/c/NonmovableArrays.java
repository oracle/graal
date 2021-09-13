/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c;

// Checkstyle: allow reflection

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

/**
 * Support for allocating and accessing non-moving arrays. Such arrays are safe to access during
 * garbage collection. They can also be passed between isolates, provided that they do not contain
 * object references and that their lifecycle is managed correctly.
 * <p>
 * Non-moving arrays are created in unmanaged memory (for example, using {@link #createByteArray})
 * and their owner must eventually manually {@linkplain #releaseUnmanagedArray release them}. For
 * {@linkplain #createObjectArray object arrays}, the owner must manually ensure that the contained
 * object references are always {@linkplain #walkUnmanagedObjectArray visible}. Although the memory
 * layout of arrays might resemble that of Java arrays, they are not Java objects and must never be
 * referenced as an object (with the exception for the image build below).
 * <p>
 * During image generation, the methods of this class create and access arrays that will reside in
 * the image heap. Due to current restrictions, the backing objects must be referenced from fields
 * via {@link #getHostedArray} and they must be cast back via {@link #fromImageHeap} at runtime.
 */
public final class NonmovableArrays {

    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final HostedNonmovableArray<?> HOSTED_NULL_VALUE = new HostedNonmovableObjectArray<>(null);

    private static final UninterruptibleUtils.AtomicLong runtimeArraysInExistence = new UninterruptibleUtils.AtomicLong(0);

    private static final OutOfMemoryError OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate nonmovable array");

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static <T extends NonmovableArray<?>> T createArray(int length, Class<?> arrayType) {
        if (SubstrateUtil.HOSTED) {
            Class<?> componentType = arrayType.getComponentType();
            Object array = Array.newInstance(componentType, length);
            return (T) (componentType.isPrimitive() ? new HostedNonmovableArray<>(array) : new HostedNonmovableObjectArray<>(array));
        }
        DynamicHub hub = SubstrateUtil.cast(arrayType, DynamicHub.class);
        assert LayoutEncoding.isArray(hub.getLayoutEncoding());
        UnsignedWord size = LayoutEncoding.getArraySize(hub.getLayoutEncoding(), length);
        Pointer array = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(size);
        if (array.isNull()) {
            throw OUT_OF_MEMORY_ERROR;
        }

        ObjectHeader header = Heap.getHeap().getObjectHeader();
        Word encodedHeader = header.encodeAsUnmanagedObjectHeader(hub);
        header.initializeHeaderOfNewObject(array, encodedHeader);

        array.writeInt(ConfigurationValues.getObjectLayout().getArrayLengthOffset(), length);
        // already zero-initialized thanks to calloc()
        trackUnmanagedArray((NonmovableArray<?>) array);
        return (T) array;
    }

    /** Begins tracking an array, e.g. when it is handed over from a different isolate. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void trackUnmanagedArray(@SuppressWarnings("unused") NonmovableArray<?> array) {
        assert !Heap.getHeap().isInImageHeap((Pointer) array) : "must not track image heap objects";
        assert array.isNull() || runtimeArraysInExistence.incrementAndGet() > 0 : "overflow";
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static DynamicHub readHub(NonmovableArray<?> array) {
        ObjectHeader objectHeader = Heap.getHeap().getObjectHeader();
        return objectHeader.readDynamicHubFromPointer((Pointer) array);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int readLayoutEncoding(NonmovableArray<?> array) {
        return readHub(array).getLayoutEncoding();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int readElementShift(NonmovableArray<?> array) {
        return LayoutEncoding.getArrayIndexShift(readLayoutEncoding(array));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int readArrayBase(NonmovableArray<?> array) {
        return (int) LayoutEncoding.getArrayBaseOffset(readLayoutEncoding(array)).rawValue();
    }

    /** Provides the length of an array in elements. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int lengthOf(NonmovableArray<?> array) {
        if (SubstrateUtil.HOSTED) {
            return Array.getLength(getHostedArray(array));
        }
        return ((Pointer) array).readInt(ConfigurationValues.getObjectLayout().getArrayLengthOffset());
    }

    /** Provides the size of the given array in bytes. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord byteSizeOf(NonmovableArray<?> array) {
        return array.isNonNull() ? LayoutEncoding.getArraySize(readLayoutEncoding(array), lengthOf(array)) : WordFactory.zero();
    }

    /** @see System#arraycopy */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void arraycopy(NonmovableArray<?> src, int srcPos, NonmovableArray<?> dest, int destPos, int length) {
        if (SubstrateUtil.HOSTED) {
            System.arraycopy(getHostedArray(src), srcPos, getHostedArray(dest), destPos, length);
            return;
        }
        assert srcPos >= 0 && destPos >= 0 && length >= 0 && srcPos + length <= lengthOf(src) && destPos + length <= lengthOf(dest);
        assert readHub(src) == readHub(dest) : "copying is only allowed with same component types";
        UnmanagedMemoryUtil.copy(addressOf(src, srcPos), addressOf(dest, destPos), WordFactory.unsigned(length << readElementShift(dest)));
    }

    /** Provides an array for which {@link NonmovableArray#isNull()} returns {@code true}. */
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends NonmovableArray<?>> T nullArray() {
        if (SubstrateUtil.HOSTED) {
            return (T) HOSTED_NULL_VALUE;
        }
        return WordFactory.nullPointer();
    }

    /**
     * Allocates a byte array of the specified length.
     * <p>
     * The returned array must be accessed via methods of {@link NonmovableArrays} only, such as
     * {@link #asByteBuffer}. Although the array's memory layout might resemble that of a Java
     * array, it is not a Java object and must never be referenced as an object.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Byte> createByteArray(int nbytes) {
        return createArray(nbytes, byte[].class);
    }

    /**
     * Allocates an integer array of the specified length.
     * <p>
     * The returned array must be accessed via methods of {@link NonmovableArrays} only, such as
     * {@link #getInt} and {@link #setInt}. Although the array's memory layout might resemble that
     * of a Java array, it is not a Java object and must never be referenced as an object.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Integer> createIntArray(int length) {
        return createArray(length, int[].class);
    }

    /**
     * Allocates a word array of the specified length.
     * <p>
     * The returned array must be accessed via methods of {@link NonmovableArrays} only, such as
     * {@link #getWord} and {@link #setWord}. Although the array's memory layout might resemble that
     * of a Java array, it is not a Java object and must never be referenced as an object.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends WordBase> NonmovableArray<T> createWordArray(int length) {
        return createArray(length, WordBase[].class);
    }

    /**
     * Allocates an array of the specified length to hold references to objects on the Java heap. In
     * order to ensure that the referenced objects are reachable for garbage collection, the owner
     * of an instance must call {@link #walkUnmanagedObjectArray} on each array from a GC-registered
     * reference walker. The array must be released manually with {@link #releaseUnmanagedArray}.
     * <p>
     * The returned array must be accessed via methods of {@link NonmovableArrays} only, such as
     * {@link #getObject} and {@link #setObject}. Although the array's memory layout might resemble
     * that of a Java array, it is not a Java object and must never be referenced as an object.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> NonmovableObjectArray<T> createObjectArray(Class<T[]> arrayType, int length) {
        assert (SubstrateUtil.HOSTED ? (arrayType.isArray() && !arrayType.getComponentType().isPrimitive())
                        : LayoutEncoding.isObjectArray(SubstrateUtil.cast(arrayType, DynamicHub.class).getLayoutEncoding())) : "must be an object array type";
        return createArray(length, arrayType);
    }

    /** @see java.util.Arrays#copyOf */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> NonmovableObjectArray<T> copyOfObjectArray(T[] source, int newLength) {
        NonmovableObjectArray<T> array = createArray(newLength, source.getClass());
        int copyLength = (source.length < newLength) ? source.length : newLength;
        for (int i = 0; i < copyLength; i++) {
            setObject(array, i, source[i]);
        }
        return array;
    }

    /** Same as {@link #copyOfObjectArray} with a {@code newLength} of the array length. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> NonmovableObjectArray<T> copyOfObjectArray(T[] source) {
        return copyOfObjectArray(source, source.length);
    }

    public static byte[] heapCopyOfByteArray(NonmovableArray<Byte> source) {
        if (source.isNull()) {
            return null;
        }
        int length = NonmovableArrays.lengthOf(source);
        return arraycopyToHeap(source, 0, new byte[length], 0, length);
    }

    public static int[] heapCopyOfIntArray(NonmovableArray<Integer> source) {
        if (source.isNull()) {
            return null;
        }
        int length = NonmovableArrays.lengthOf(source);
        return arraycopyToHeap(source, 0, new int[length], 0, length);
    }

    public static <T> T[] heapCopyOfObjectArray(NonmovableObjectArray<T> source) {
        if (source.isNull()) {
            return null;
        }
        if (SubstrateUtil.HOSTED) {
            T[] hosted = getHostedArray(source);
            return Arrays.copyOf(hosted, hosted.length);
        }
        int length = NonmovableArrays.lengthOf(source);
        Class<?> componentType = DynamicHub.toClass(readHub(source).getComponentHub());
        @SuppressWarnings("unchecked")
        T[] dest = (T[]) Array.newInstance(componentType, length);
        return arraycopyToHeap(source, 0, dest, 0, length);
    }

    @Uninterruptible(reason = "Destination array must not move.")
    private static <T> T arraycopyToHeap(NonmovableArray<?> src, int srcPos, T dest, int destPos, int length) {
        if (SubstrateUtil.HOSTED) {
            System.arraycopy(getHostedArray(src), srcPos, dest, destPos, length);
            return dest;
        }
        DynamicHub destHub = KnownIntrinsics.readHub(dest);
        assert LayoutEncoding.isArray(destHub.getLayoutEncoding()) && destHub == readHub(src) : "Copying is only supported for arrays with identical types";
        assert srcPos >= 0 && destPos >= 0 && length >= 0 && srcPos + length <= lengthOf(src) && destPos + length <= ArrayLengthNode.arrayLength(dest);
        Pointer destAddressAtPos = Word.objectToUntrackedPointer(dest).add(LayoutEncoding.getArrayElementOffset(destHub.getLayoutEncoding(), destPos));
        if (LayoutEncoding.isPrimitiveArray(destHub.getLayoutEncoding())) {
            Pointer srcAddressAtPos = addressOf(src, srcPos);
            JavaMemoryUtil.copyPrimitiveArrayForward(srcAddressAtPos, destAddressAtPos, WordFactory.unsigned(length << readElementShift(src)));
        } else { // needs barriers
            Object[] destArr = (Object[]) dest;
            for (int i = 0; i < length; i++) {
                destArr[destPos + i] = getObject((NonmovableObjectArray<?>) src, srcPos + i);
            }
        }
        return dest;
    }

    /** Releases an array created at runtime. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void releaseUnmanagedArray(NonmovableArray<?> array) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(array);
        untrackUnmanagedArray(array);
    }

    /** Untracks an array created at runtime, e.g. before it is handed over to another isolate. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void untrackUnmanagedArray(NonmovableArray<?> array) {
        assert !Heap.getHeap().isInImageHeap((Pointer) array) : "must not track image heap objects";
        assert array.isNull() || runtimeArraysInExistence.getAndDecrement() > 0;
    }

    /** Returns a {@link NonmovableArray} for an array of primitives in the image heap. */
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> NonmovableArray<T> fromImageHeap(Object array) {
        if (SubstrateUtil.HOSTED) {
            if (array == null) {
                return (NonmovableArray<T>) HOSTED_NULL_VALUE;
            }
            VMError.guarantee(array.getClass().getComponentType().isPrimitive(), "Must call the method for Object[]");
            return new HostedNonmovableArray<>(array);
        }
        assert array == null || Heap.getHeap().isInImageHeap(array);
        return (array != null) ? (NonmovableArray<T>) Word.objectToUntrackedPointer(array) : WordFactory.nullPointer();
    }

    /** Returns a {@link NonmovableObjectArray} for an object array in the image heap. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> NonmovableObjectArray<T> fromImageHeap(Object[] array) {
        if (SubstrateUtil.HOSTED) {
            return (array != null) ? new HostedNonmovableObjectArray<>(array) : (NonmovableObjectArray<T>) HOSTED_NULL_VALUE;
        }
        return (NonmovableObjectArray) fromImageHeap((Object) array);
    }

    /** During the image build, get the backing array that will be nonmovable in the image heap. */
    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("unchecked")
    public static <T> T getHostedArray(NonmovableArray<?> array) {
        return (T) ((HostedNonmovableArray<?>) array).getArray();
    }

    /** Obtain a ByteBuffer that is backed by the given array. */
    public static ByteBuffer asByteBuffer(NonmovableArray<Byte> array) {
        if (SubstrateUtil.HOSTED) {
            return ByteBuffer.wrap(getHostedArray(array));
        }
        return CTypeConversion.asByteBuffer(addressOf(array, 0), lengthOf(array));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean matches(NonmovableArray<?> array, boolean primitive, int elementSize) {
        int encoding = readLayoutEncoding(array);
        return (primitive == LayoutEncoding.isPrimitiveArray(encoding) && (1 << LayoutEncoding.getArrayIndexShift(encoding)) == elementSize);
    }

    /** Reads the value at the given index in an array of {@code int}s. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getInt(NonmovableArray<Integer> array, int index) {
        if (SubstrateUtil.HOSTED) {
            int[] hosted = getHostedArray(array);
            return hosted[index];
        }
        assert DynamicHub.toClass(readHub(array)) == int[].class;
        return ((Pointer) addressOf(array, index)).readInt(0);
    }

    /** Writes the value at the given index in an array of {@code int}s. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setInt(NonmovableArray<Integer> array, int index, int value) {
        if (SubstrateUtil.HOSTED) {
            int[] hosted = getHostedArray(array);
            hosted[index] = value;
            return;
        }
        assert DynamicHub.toClass(readHub(array)) == int[].class;
        ((Pointer) addressOf(array, index)).writeInt(0, value);
    }

    /** Writes the value at the given index in an array of {@linkplain WordBase words}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends WordBase> void setWord(NonmovableArray<T> array, int index, T value) {
        if (SubstrateUtil.HOSTED) {
            T[] hosted = getHostedArray(array);
            hosted[index] = value;
            return;
        }
        assert matches(array, true, ConfigurationValues.getTarget().wordSize);
        ((Pointer) addressOf(array, index)).writeWord(0, value);
    }

    /** Reads the value at the given index in an array of {@linkplain WordBase words}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends WordBase> T getWord(NonmovableArray<T> array, int index) {
        if (SubstrateUtil.HOSTED) {
            T[] hosted = getHostedArray(array);
            return hosted[index];
        }
        assert matches(array, true, ConfigurationValues.getTarget().wordSize);
        return ((Pointer) addressOf(array, index)).readWord(0);
    }

    /** Returns a pointer to the address of the given index of an array. */
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T addressOf(NonmovableArray<?> array, int index) {
        assert index >= 0 && index <= lengthOf(array);
        return (T) getArrayBase(array).add(index << readElementShift(array));
    }

    /** Reads the value at the given index in an object array. */
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends Pointer> T getArrayBase(NonmovableArray<?> array) {
        return (T) ((Pointer) array).add(readArrayBase(array));
    }

    /** Reads the value at the given index in an object array. */
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> T getObject(NonmovableObjectArray<T> array, int index) {
        if (SubstrateUtil.HOSTED) {
            Object[] hosted = getHostedArray(array);
            return (T) hosted[index];
        }
        assert matches(array, false, ConfigurationValues.getObjectLayout().getReferenceSize());
        return (T) ReferenceAccess.singleton().readObjectAt(addressOf(array, index), true);
    }

    /** Writes the value at the given index in an object array. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> void setObject(NonmovableObjectArray<T> array, int index, T value) {
        if (SubstrateUtil.HOSTED) {
            Object[] hosted = getHostedArray(array);
            hosted[index] = value;
            return;
        }
        assert matches(array, false, ConfigurationValues.getObjectLayout().getReferenceSize());
        ReferenceAccess.singleton().writeObjectAt(addressOf(array, index), value, true);
    }

    /**
     * Visits all array elements with the provided {@link ObjectReferenceVisitor}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true, calleeMustBe = false)
    public static boolean walkUnmanagedObjectArray(NonmovableObjectArray<?> array, ObjectReferenceVisitor visitor) {
        if (array.isNonNull()) {
            return walkUnmanagedObjectArray(array, visitor, 0, lengthOf(array));
        }
        return true;
    }

    /**
     * Visits all array elements with the provided {@link ObjectReferenceVisitor}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true, calleeMustBe = false)
    public static boolean walkUnmanagedObjectArray(NonmovableObjectArray<?> array, ObjectReferenceVisitor visitor, int startIndex, int count) {
        if (array.isNonNull()) {
            assert startIndex >= 0 && count <= lengthOf(array) - startIndex;
            int refSize = ConfigurationValues.getObjectLayout().getReferenceSize();
            assert refSize == (1 << readElementShift(array));
            Pointer p = ((Pointer) array).add(readArrayBase(array)).add(startIndex * refSize);
            for (int i = 0; i < count; i++) {
                if (!visitor.visitObjectReference(p, true, null)) {
                    return false;
                }
                p = p.add(refSize);
            }
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void tearDown() {
        assert runtimeArraysInExistence.get() == 0 : "All runtime-allocated NonmovableArrays must have been freed";
    }

    private NonmovableArrays() {
    }
}
