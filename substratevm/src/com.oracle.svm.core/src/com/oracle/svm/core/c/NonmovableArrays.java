/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

public final class NonmovableArrays {
    private static final HostedNonmovableArray<?> HOSTED_NULL_VALUE = new HostedNonmovableObjectArray<>(null);

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
        WordBase header = Heap.getHeap().getObjectHeader().formatHubRaw(hub);
        ObjectHeader.initializeHeaderOfNewObject(array, header);
        array.writeInt(ConfigurationValues.getObjectLayout().getArrayLengthOffset(), length);
        // already zero-initialized thanks to calloc()
        return (T) array;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static DynamicHub readHub(NonmovableArray<?> array) {
        UnsignedWord header = ObjectHeader.readHeaderFromPointer((Pointer) array);
        return ObjectHeader.dynamicHubFromObjectHeader(header);
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int lengthOf(NonmovableArray<?> array) {
        if (SubstrateUtil.HOSTED) {
            return Array.getLength(getHostedArray(array));
        }
        return ((Pointer) array).readInt(ConfigurationValues.getObjectLayout().getArrayLengthOffset());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord byteSizeOf(NonmovableArray<?> array) {
        return array.isNonNull() ? LayoutEncoding.getArraySize(readLayoutEncoding(array), lengthOf(array)) : WordFactory.zero();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void arraycopy(NonmovableArray<?> src, int srcPos, NonmovableArray<?> dest, int destPos, int length) {
        if (SubstrateUtil.HOSTED) {
            System.arraycopy(getHostedArray(src), srcPos, getHostedArray(dest), destPos, length);
            return;
        }
        assert srcPos >= 0 && destPos >= 0 && length >= 0 && srcPos + length <= lengthOf(src) && destPos + length <= lengthOf(dest);
        assert readHub(src) == readHub(dest) : "copying is only allowed with same component types";
        MemoryUtil.copyConjointMemoryAtomic(addressOf(src, srcPos), addressOf(dest, destPos), WordFactory.unsigned(length << readElementShift(dest)));
    }

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
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Byte> createByteArray(int nbytes) {
        return createArray(nbytes, byte[].class);
    }

    /**
     * Allocates an integer array of the specified length.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Integer> createIntArray(int length) {
        return createArray(length, int[].class);
    }

    /**
     * Allocates a word array of the specified length.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends WordBase> NonmovableArray<T> createWordArray(int length) {
        return createArray(length, WordBase[].class);
    }

    /**
     * Allocates an array of the specified length to hold references to objects on the Java heap. In
     * order to ensure that the referenced objects are reachable for garbage collection, the owner
     * of an instance must call {@link #walkUnmanagedObjectArray} on each array from
     * {@linkplain GC#registerObjectReferenceWalker(ObjectReferenceWalker) a GC-registered reference
     * walker}. The array must be released manually with {@link #releaseUnmanagedArray}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> NonmovableObjectArray<T> createObjectArray(int length) {
        return createArray(length, Object[].class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> NonmovableObjectArray<T> copyOfObjectArray(T[] source, int newLength) {
        NonmovableObjectArray<T> array = createArray(newLength, source.getClass());
        int copyLength = (source.length < newLength) ? source.length : newLength;
        for (int i = 0; i < copyLength; i++) {
            setObject(array, i, source[i]);
        }
        return array;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> NonmovableObjectArray<T> copyOfObjectArray(T[] source) {
        return copyOfObjectArray(source, source.length);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void releaseUnmanagedArray(NonmovableArray<?> array) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(array);
    }

    /** Returns a {@link NonmovableArray} for an array of primitives in the image heap. */
    @SuppressWarnings("unchecked")
    public static <T> NonmovableArray<T> fromImageHeap(Object array) {
        if (SubstrateUtil.HOSTED) {
            if (array == null) {
                return (NonmovableArray<T>) HOSTED_NULL_VALUE;
            }
            VMError.guarantee(array.getClass().getComponentType().isPrimitive(), "Must call the method for Object[]");
            return new HostedNonmovableArray<>(array);
        }
        assert array == null || Heap.getHeap().getObjectHeader().isNonHeapAllocatedHeader(ObjectHeader.readHeaderFromObject(array));
        return (array != null) ? (NonmovableArray<T>) Word.objectToUntrackedPointer(array) : WordFactory.nullPointer();
    }

    /** Returns a {@link NonmovableObjectArray} for an object array in the image heap. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> NonmovableObjectArray<T> fromImageHeap(Object[] array) {
        if (SubstrateUtil.HOSTED) {
            return (array != null) ? new HostedNonmovableObjectArray<>(array) : (NonmovableObjectArray<T>) HOSTED_NULL_VALUE;
        }
        return (NonmovableObjectArray) fromImageHeap((Object) array);
    }

    /** During the image build, retrieve the array object that will be pinned. */
    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("unchecked")
    public static <T> T getHostedArray(NonmovableArray<?> array) {
        return (T) ((HostedNonmovableArray<?>) array).getArray();
    }

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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getInt(NonmovableArray<Integer> array, int index) {
        if (SubstrateUtil.HOSTED) {
            int[] hosted = getHostedArray(array);
            return hosted[index];
        }
        assert DynamicHub.toClass(readHub(array)) == int[].class;
        return ((Pointer) addressOf(array, index)).readInt(0);
    }

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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends WordBase> void setWord(NonmovableArray<T> array, int index, T value) {
        assert matches(array, true, ConfigurationValues.getTarget().wordSize);
        ((Pointer) addressOf(array, index)).writeWord(0, value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends WordBase> T getWord(NonmovableArray<T> array, int index) {
        assert matches(array, true, ConfigurationValues.getTarget().wordSize);
        return ((Pointer) addressOf(array, index)).readWord(0);
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T addressOf(NonmovableArray<?> array, int index) {
        assert index >= 0 && index <= lengthOf(array);
        return (T) ((Pointer) array).add(readArrayBase(array) + (index << readElementShift(array)));
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> T getObject(NonmovableObjectArray<T> array, int index) {
        if (SubstrateUtil.HOSTED) {
            Object[] hosted = getHostedArray(array);
            return (T) hosted[index];
        }
        assert matches(array, false, ConfigurationValues.getObjectLayout().getReferenceSize());
        return (T) KnownIntrinsics.convertUnknownValue(ReferenceAccess.singleton().readObjectAt(addressOf(array, index), true), Object.class);
    }

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

    /** @see ObjectReferenceWalker */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true, calleeMustBe = false)
    public static void walkUnmanagedObjectArray(NonmovableObjectArray<?> array, ObjectReferenceVisitor visitor) {
        if (array.isNonNull()) {
            int refSize = ConfigurationValues.getObjectLayout().getReferenceSize();
            assert refSize == (1 << readElementShift(array));
            int length = lengthOf(array);
            Pointer p = ((Pointer) array).add(readArrayBase(array));
            for (int i = 0; i < length; i++) {
                visitor.visitObjectReference(p, true);
                p = p.add(refSize);
            }
        }
    }

    private NonmovableArrays() {
    }
}
