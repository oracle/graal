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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

public final class NonmovableArrays {
    private static final HostedNonmovableArray<?> HOSTED_NULL_VALUE = new HostedNonmovableObjectArray<>(null);

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Faux object reference on stack during array initialization.")
    private static <T extends NonmovableArray<?>> T createArray(int length, Class<?> arrayType) {
        if (SubstrateUtil.HOSTED) {
            Class<?> componentType = arrayType.getComponentType();
            Object array = Array.newInstance(componentType, length);
            return (T) (componentType.isPrimitive() ? new HostedNonmovableArray<>(array) : new HostedNonmovableObjectArray<>(array));
        }
        int layoutEncoding = SubstrateUtil.cast(arrayType, DynamicHub.class).getLayoutEncoding();
        assert LayoutEncoding.isArray(layoutEncoding);
        UnsignedWord size = LayoutEncoding.getArraySize(layoutEncoding, length);
        T array = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(size);
        KnownIntrinsics.formatArray((Pointer) array, arrayType, length, false, false);
        return array;
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    private static int readLayoutEncoding(NonmovableArray<?> array) {
        return KnownIntrinsics.readHub(asObject(array)).getLayoutEncoding();
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Faux object reference on stack.", callerMustBe = true)
    private static <T> T asObject(PointerBase array) {
        if (SubstrateUtil.HOSTED) {
            return (T) getHostedArray((NonmovableArray<?>) array);
        }
        return (T) KnownIntrinsics.convertUnknownValue(((Pointer) array).toObject(), Object.class);
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    private static int readElementShift(NonmovableArray<?> array) {
        return LayoutEncoding.getArrayIndexShift(readLayoutEncoding(array));
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    private static int readArrayBase(NonmovableArray<?> array) {
        return (int) LayoutEncoding.getArrayBaseOffset(readLayoutEncoding(array)).rawValue();
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    public static int lengthOf(NonmovableArray<?> array) {
        if (SubstrateUtil.HOSTED) {
            return Array.getLength(getHostedArray(array));
        }
        return KnownIntrinsics.readArrayLength(asObject(array));
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    public static UnsignedWord byteSizeOf(NonmovableArray<?> array) {
        return array.isNonNull() ? LayoutEncoding.getSizeFromObject(asObject(array)) : WordFactory.zero();
    }

    @Uninterruptible(reason = "Faux object references on stack.")
    public static void arraycopy(NonmovableArray<?> src, int srcPos, NonmovableArray<?> dest, int destPos, int length) {
        System.arraycopy(asObject(src), srcPos, asObject(dest), destPos, length);
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> NonmovableObjectArray<T> copyOfObjectArray(T[] source, int newLength) {
        NonmovableArray<?> array = createArray(newLength, source.getClass());
        int copyLength = (source.length < newLength) ? source.length : newLength;
        System.arraycopy(source, 0, asObject(array), 0, copyLength);
        return (NonmovableObjectArray<T>) array;
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

    @Uninterruptible(reason = "Faux object reference on stack.")
    public static int getInt(NonmovableArray<?> nonmovableArray, int index) {
        Object array = asObject(nonmovableArray);
        if (array instanceof int[]) {
            return ((int[]) array)[index];
        }
        // add support for byte, short, char on demand
        throw ImplicitExceptions.CACHED_CLASS_CAST_EXCEPTION;
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    public static void setInt(NonmovableArray<?> nonmovableArray, int index, int value) {
        Object array = asObject(nonmovableArray);
        // add support for long, float, double on demand
        if (array instanceof int[]) {
            ((int[]) array)[index] = value;
        } else {
            throw ImplicitExceptions.CACHED_CLASS_CAST_EXCEPTION;
        }
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    public static <T extends WordBase> void setWord(NonmovableArray<T> nonmovableArray, int index, T value) {
        T[] array = asObject(nonmovableArray);
        array[index] = value;
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    public static <T extends WordBase> T getWord(NonmovableArray<T> nonmovableArray, int index) {
        T[] array = asObject(nonmovableArray);
        return array[index];
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T addressOf(NonmovableArray<?> array, int index) {
        assert index >= 0 && index <= lengthOf(array);
        return (T) ((Pointer) array).add(readArrayBase(array) + (index << readElementShift(array)));
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    public static <T> T getObject(NonmovableObjectArray<T> array, int index) {
        T[] obj = asObject(array);
        return obj[index];
    }

    @Uninterruptible(reason = "Faux object reference on stack.")
    public static <T> void setObject(NonmovableObjectArray<T> array, int index, T value) {
        T[] obj = asObject(array);
        obj[index] = value;
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
