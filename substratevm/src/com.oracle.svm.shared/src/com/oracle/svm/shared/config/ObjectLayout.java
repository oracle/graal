/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.shared.config;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.SharedIntrinsics;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.CodeUtil;
import com.oracle.svm.shared.util.NumUtil;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

import com.oracle.svm.shared.meta.GuestFold;

/**
 * Immutable class that holds all sizes and offsets that contribute to the object layout.
 *
 * Identity hashcode fields can either be:
 * <ol type="a">
 * <li>In the object header, at a fixed offset for all objects (see
 * {@link #isIdentityHashFieldInObjectHeader()}).</li>
 * <li>At a type specific offset, potentially outside the object header (see
 * {@link #isIdentityHashFieldAtTypeSpecificOffset()}).</li>
 * <li>Outside the object header, at a type- or object-specific offset (see
 * {@link #isIdentityHashFieldOptional()}). Note that the field is not part of every object. When an
 * object needs the field, the object is resized during garbage collection to accommodate the
 * field.</li>
 * </ol>
 *
 * See this classes instantiation sites (such as {@code HostedConfiguration#createObjectLayout}) for
 * more details on the exact object layout for a given configuration.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = ObjectLayout.LayeredCallbacks.class, layeredInstallationKind = Duplicable.class)
public final class ObjectLayout {

    @GuestFold
    public static ObjectLayout singleton() {
        return ImageSingletons.lookup(ObjectLayout.class);
    }

    private final int referenceSize;
    private final int objectAlignment;
    private final int alignmentMask;
    private final int hubSize;
    private final int hubOffset;
    private final int firstFieldOffset;
    private final int arrayLengthOffset;
    private final int arrayBaseOffset;
    private final int objectHeaderIdentityHashOffset;
    private final int identityHashMode;
    private final int identityHashNumBits;
    private final int identityHashShift;

    public ObjectLayout(int referenceSize, int objectAlignment, int hubSize, int hubOffset, int firstFieldOffset, int arrayLengthOffset, int arrayBaseOffset,
                    int headerIdentityHashOffset, IdentityHashMode identityHashMode, int identityHashNumBits, int identityHashShift) {
        assert CodeUtil.isPowerOf2(referenceSize) : referenceSize;
        assert CodeUtil.isPowerOf2(objectAlignment) : objectAlignment;
        assert arrayLengthOffset % Integer.BYTES == 0;
        assert hubOffset < firstFieldOffset && hubOffset < arrayLengthOffset : hubOffset;
        assert hubSize == Integer.BYTES || hubSize == Long.BYTES;
        assert (identityHashMode != IdentityHashMode.OPTIONAL && headerIdentityHashOffset >= 0 && headerIdentityHashOffset < arrayLengthOffset && headerIdentityHashOffset % Integer.BYTES == 0) ||
                        (identityHashMode == IdentityHashMode.OPTIONAL && headerIdentityHashOffset == -1);
        assert identityHashNumBits > 0 && identityHashNumBits <= Integer.SIZE;
        assert identityHashShift >= 0 && identityHashShift < Long.SIZE;

        this.referenceSize = referenceSize;
        this.objectAlignment = objectAlignment;
        this.alignmentMask = objectAlignment - 1;
        this.hubSize = hubSize;
        this.hubOffset = hubOffset;
        this.firstFieldOffset = firstFieldOffset;
        this.arrayLengthOffset = arrayLengthOffset;
        this.arrayBaseOffset = arrayBaseOffset;
        this.objectHeaderIdentityHashOffset = headerIdentityHashOffset;
        this.identityHashMode = identityHashMode.value;
        this.identityHashNumBits = identityHashNumBits;
        this.identityHashShift = identityHashShift;
    }

    /** The minimum alignment of objects (instances and arrays). */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getAlignment() {
        return objectAlignment;
    }

    /** Tests if the given offset or address is aligned according to {@link #getAlignment()}. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isAligned(long value) {
        return (value % getAlignment() == 0L);
    }

    /** Tests if the given offset or address is aligned according to {@link #getAlignment()}. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isAligned(UnsignedWord value) {
        return UnsignedUtils.isAMultiple(value, Word.unsigned(getAlignment()));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getReferenceSize() {
        return referenceSize;
    }

    /**
     * Align the specified offset or address up to {@link #getAlignment()}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int alignUp(int obj) {
        return (obj + alignmentMask) & ~alignmentMask;
    }

    /**
     * Align the specified offset or address up to {@link #getAlignment()}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long alignUp(long obj) {
        return (obj + alignmentMask) & ~alignmentMask;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getHubOffset() {
        return hubOffset;
    }

    public int getHubSize() {
        return hubSize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getFirstFieldOffset() {
        return firstFieldOffset;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getArrayLengthOffset() {
        return arrayLengthOffset;
    }

    /**
     * Indicates whether all objects, including arrays, always contain an identity hash code field
     * at a specific offset in the object header.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isIdentityHashFieldInObjectHeader() {
        return identityHashMode == IdentityHashMode.OBJECT_HEADER.value;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isIdentityHashFieldAtTypeSpecificOffset() {
        return identityHashMode == IdentityHashMode.TYPE_SPECIFIC.value;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isIdentityHashFieldOptional() {
        return identityHashMode == IdentityHashMode.OPTIONAL.value;
    }

    /**
     * Returns the offset of the identity hash field when it is stored in the object header. The
     * caller must ensure that the identity hash field is in the object header. Ordinary Java access
     * checks this precondition with an assertion; calls from an intrinsic or snippet use a dynamic
     * assertion provided by {@link SharedIntrinsics}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getObjectHeaderIdentityHashOffset() {
        if (SharedIntrinsics.inIntrinsic()) {
            SharedIntrinsics.dynamicAssert(objectHeaderIdentityHashOffset >= 0, "must check before calling");
        } else {
            assert objectHeaderIdentityHashOffset >= 0 : "must check before calling";
        }
        return objectHeaderIdentityHashOffset;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getIdentityHashCodeNumBits() {
        return identityHashNumBits;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getIdentityHashCodeShift() {
        return identityHashShift;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getIdentityHashCodeMask() {
        long mask = (1L << identityHashNumBits) - 1L;
        return mask << identityHashShift;
    }

    /**
     * Returns the base offset of an array with elements of the given storage size in bytes.
     *
     * @param elementSize the element storage size in bytes, a positive power of two
     */
    public int getArrayBaseOffset(int elementSize) {
        return NumUtil.roundUp(arrayBaseOffset, elementSize);
    }

    /**
     * Returns the offset of an array element with the given storage size in bytes.
     *
     * @param elementSize the element storage size in bytes, a positive power of two
     */
    public long getArrayElementOffset(int elementSize, int index) {
        return getArrayBaseOffset(elementSize) + ((long) index) * elementSize;
    }

    /**
     * Returns the size of an array with elements of the given storage size in bytes.
     *
     * @param elementSize the element storage size in bytes, a positive power of two
     */
    public long getArraySize(int elementSize, int length, boolean withOptionalIdHashField) {
        return computeArrayTotalSize(getArrayUnalignedSize(elementSize, length), withOptionalIdHashField);
    }

    private long getArrayUnalignedSize(int elementSize, int length) {
        assert length >= 0 : length;
        return getArrayBaseOffset(elementSize) + ((long) length << CodeUtil.log2(elementSize));
    }

    /**
     * Returns the offset of an array's identity hash field with elements of the given storage size
     * in bytes.
     *
     * @param elementSize the element storage size in bytes, a positive power of two
     */
    public long getArrayIdentityHashOffset(int elementSize, int length) {
        return getArrayIdentityHashOffset(getArrayUnalignedSize(elementSize, length));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getArrayIdentityHashOffset(long unalignedSize) {
        if (isIdentityHashFieldInObjectHeader() || isIdentityHashFieldAtTypeSpecificOffset()) {
            return getObjectHeaderIdentityHashOffset();
        }
        int align = Integer.BYTES;
        return ((unalignedSize + align - 1) / align) * align;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long computeArrayTotalSize(long unalignedSize, boolean withOptionalIdHashField) {
        long size = unalignedSize;
        if (withOptionalIdHashField && isIdentityHashFieldOptional()) {
            size = getArrayIdentityHashOffset(size) + Integer.BYTES;
        }
        return alignUp(size);
    }

    public int getMinRuntimeHeapInstanceSize() {
        return getMinInstanceSize(false);
    }

    public int getMinImageHeapInstanceSize() {
        return getMinInstanceSize(true);
    }

    private int getMinInstanceSize(boolean withOptionalIdHashField) {
        int unalignedSize = firstFieldOffset; // assumes no always-present "synthetic fields"
        if (isIdentityHashFieldAtTypeSpecificOffset() || (withOptionalIdHashField && isIdentityHashFieldOptional())) {
            int idHashOffset = NumUtil.roundUp(unalignedSize, Integer.BYTES);
            unalignedSize = idHashOffset + Integer.BYTES;
        }
        return alignUp(unalignedSize);
    }

    public int getMinImageHeapArraySize() {
        return NumUtil.safeToInt(getArraySize(Byte.BYTES, 0, true));
    }

    @GuestFold
    public int getMinImageHeapObjectSize() {
        return Math.min(getMinImageHeapArraySize(), getMinImageHeapInstanceSize());
    }

    private List<Integer> getCurrentValues() {
        return List.of(this.referenceSize,
                        this.objectAlignment,
                        this.alignmentMask,
                        this.hubSize,
                        this.hubOffset,
                        this.firstFieldOffset,
                        this.arrayLengthOffset,
                        this.arrayBaseOffset,
                        this.objectHeaderIdentityHashOffset,
                        this.identityHashMode,
                        this.identityHashNumBits,
                        this.identityHashShift);
    }

    public enum IdentityHashMode {
        /* At a fixed offset, for all objects (part of the object header). */
        OBJECT_HEADER(0),
        /* At a type-specific offset (potentially outside the object header). */
        TYPE_SPECIFIC(1),
        /* At a type- or object-specific offset (outside the object header). */
        OPTIONAL(2);

        final int value;

        IdentityHashMode(int value) {
            this.value = value;
        }
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            var action = new SingletonLayeredCallbacks<ObjectLayout>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, ObjectLayout singleton) {
                    List<Integer> currentValues = singleton.getCurrentValues();
                    writer.writeIntList("priorValues", currentValues);
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, ObjectLayout singleton) {
                    List<Integer> currentValues = singleton.getCurrentValues();
                    List<Integer> priorValues = loader.readIntList("priorValues");

                    long numFields = Arrays.stream(ObjectLayout.class.getDeclaredFields())
                                    .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                                    .count();
                    VMError.guarantee(numFields == currentValues.size(), "Missing fields");

                    VMError.guarantee(currentValues.equals(priorValues),
                                    "The object layout values should be consistent across layers. The previous layer object layout were %s, but the current layer are %s",
                                    priorValues, currentValues);
                }
            };
            return new LayeredCallbacksSingletonTrait(action);
        }
    }
}
