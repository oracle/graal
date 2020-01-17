/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.DuplicatedInNativeCode;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The layout encoding for instances is the aligned instance size (i.e., a positive number).
 * <p>
 * For arrays, the layout encoding is a negative number with the following format:<br>
 *
 * <code>[tag:2, free:10, base:12, indexShift:8]</code>
 * <ul>
 * <li>tag: 0x80 if the array is an object array, 0xC0 if it is a primitive array</li>
 * <li>free: currently unused bits</li>
 * <li>base: the array base offset</li>
 * <li>indexShift: the array index shift for accessing array elements or for computing the array
 * size based on the array length</li>
 * </ul>
 */
@DuplicatedInNativeCode
public class LayoutEncoding {

    private static final int NEUTRAL_VALUE = 0;
    private static final int PRIMITIVE_VALUE = NEUTRAL_VALUE + 1;
    private static final int INTERFACE_VALUE = PRIMITIVE_VALUE + 1;
    private static final int ABSTRACT_VALUE = INTERFACE_VALUE + 1;
    private static final int LAST_SPECIAL_VALUE = ABSTRACT_VALUE;

    private static final int ARRAY_INDEX_SHIFT_SHIFT = 0;
    private static final int ARRAY_INDEX_SHIFT_MASK = 0xff;
    private static final int ARRAY_BASE_SHIFT = 8 + ARRAY_INDEX_SHIFT_SHIFT;
    private static final int ARRAY_BASE_MASK = 0xfff;
    private static final int ARRAY_TAG_BITS = 2;
    private static final int ARRAY_TAG_SHIFT = Integer.SIZE - ARRAY_TAG_BITS;
    private static final int ARRAY_TAG_PRIMITIVE_VALUE = ~0x00;
    private static final int ARRAY_TAG_OBJECT_VALUE = ~0x01;

    public static int forPrimitive() {
        return PRIMITIVE_VALUE;
    }

    public static int forInterface() {
        return INTERFACE_VALUE;
    }

    public static int forAbstract() {
        return ABSTRACT_VALUE;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void guaranteeEncoding(ResolvedJavaType type, boolean condition, String description) {
        VMError.guarantee(condition, description + ". This error is caused by an incorrect compact encoding of a type " +
                        "(a class, array or a primitive). The error occurred with the following type, but also could be caused " +
                        "by characteristics of the overall type hierarchy: " + type + ". Please report this problem and the " +
                        "conditions in which it occurs and include any noteworthy characteristics of the type hierarchy and " +
                        "architecture of the application and the libraries and frameworks it uses.");
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int forInstance(ResolvedJavaType type, int size) {
        guaranteeEncoding(type, size > LAST_SPECIAL_VALUE, "Instance type size must be above special values for encoding: " + size);
        int encoding = size;
        guaranteeEncoding(type, isInstance(encoding), "Instance type encoding must denote an instance");
        guaranteeEncoding(type, !isArray(encoding), "Instance type encoding must not denote an array");
        guaranteeEncoding(type, !isObjectArray(encoding), "Instance type encoding must not denote an object array");
        guaranteeEncoding(type, !isPrimitiveArray(encoding), "Instance type encoding must not denote a primitive array");
        guaranteeEncoding(type, getInstanceSize(encoding).equal(WordFactory.unsigned(size)), "Instance type encoding size must match type size");
        return encoding;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int forArray(ResolvedJavaType type, boolean isObject, int arrayBaseOffset, int arrayIndexShift) {
        int tag = isObject ? ARRAY_TAG_OBJECT_VALUE : ARRAY_TAG_PRIMITIVE_VALUE;
        int encoding = (tag << ARRAY_TAG_SHIFT) | (arrayBaseOffset << ARRAY_BASE_SHIFT) | (arrayIndexShift << ARRAY_INDEX_SHIFT_SHIFT);

        guaranteeEncoding(type, isArray(encoding), "Array encoding must denote an array");
        guaranteeEncoding(type, !isInstance(encoding), "Array encoding must not denote an instance type");
        guaranteeEncoding(type, isObjectArray(encoding) == isObject, "Expected isObjectArray(encoding) == " + isObject);
        guaranteeEncoding(type, isPrimitiveArray(encoding) != isObject, "Expected isPrimitiveArray(encoding) != " + isObject);
        guaranteeEncoding(type, getArrayBaseOffset(encoding).equal(WordFactory.unsigned(arrayBaseOffset)),
                        "Expected array base offset of " + arrayBaseOffset + ", but encoding gives " + getArrayBaseOffset(encoding));
        guaranteeEncoding(type, getArrayIndexShift(encoding) == arrayIndexShift,
                        "Expected array index shift of " + arrayIndexShift + ", but encoding gives " + getArrayIndexShift(encoding));
        return encoding;
    }

    public static boolean isPrimitive(int encoding) {
        return encoding == PRIMITIVE_VALUE;
    }

    public static boolean isInterface(int encoding) {
        return encoding == INTERFACE_VALUE;
    }

    public static boolean isAbstract(int encoding) {
        return encoding == ABSTRACT_VALUE;
    }

    public static boolean isInstance(int encoding) {
        return encoding > LAST_SPECIAL_VALUE;
    }

    public static UnsignedWord getInstanceSize(int encoding) {
        return WordFactory.unsigned(encoding);
    }

    // May be inlined because it does not deal in Pointers.
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isArray(int encoding) {
        return encoding < NEUTRAL_VALUE;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isPrimitiveArray(int encoding) {
        return UnsignedMath.aboveOrEqual(encoding, ARRAY_TAG_PRIMITIVE_VALUE << ARRAY_TAG_SHIFT);
    }

    public static boolean isObjectArray(int encoding) {
        return encoding < (ARRAY_TAG_PRIMITIVE_VALUE << ARRAY_TAG_SHIFT);
    }

    // May be inlined because it does not deal in Pointers.
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getArrayBaseOffset(int encoding) {
        return WordFactory.unsigned((encoding >> ARRAY_BASE_SHIFT) & ARRAY_BASE_MASK);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getArrayIndexShift(int encoding) {
        return (encoding >> ARRAY_INDEX_SHIFT_SHIFT) & ARRAY_INDEX_SHIFT_MASK;
    }

    public static int getArrayIndexScale(int encoding) {
        return 1 << getArrayIndexShift(encoding);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getArrayElementOffset(int encoding, int index) {
        return getArrayBaseOffset(encoding).add(WordFactory.unsigned(index).shiftLeft(getArrayIndexShift(encoding)));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getArraySize(int encoding, int length) {
        int alignmentMask = getAlignmentMask();
        return getArrayElementOffset(encoding, length).add(alignmentMask).and(~alignmentMask);
    }

    public static UnsignedWord getSizeFromObject(Object obj) {
        int encoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
        if (isArray(encoding)) {
            return getArraySize(encoding, KnownIntrinsics.readArrayLength(obj));
        } else {
            return getInstanceSize(encoding);
        }
    }

    /** Returns the end of the Object when the call started, e.g., for logging. */
    public static Pointer getObjectEnd(Object obj) {
        // TODO: This assumes that the object starts at obj.
        // - In other universes obj could point to the hub in the middle of,
        // for example, a butterfly object.
        final Pointer objStart = Word.objectToUntrackedPointer(obj);
        final UnsignedWord objSize = getSizeFromObject(obj);
        return objStart.add(objSize);
    }

    public static boolean isArray(Object obj) {
        final int encoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
        return isArray(encoding);
    }

    public static boolean isInstance(Object obj) {
        final int encoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
        return isInstance(encoding);
    }

    @Fold
    protected static int getAlignmentMask() {
        return ImageSingletons.lookup(ObjectLayout.class).getAlignment() - 1;
    }
}
