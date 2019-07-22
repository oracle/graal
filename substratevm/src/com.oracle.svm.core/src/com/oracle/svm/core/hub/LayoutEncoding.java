/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.snippets.KnownIntrinsics;

public class LayoutEncoding {

    private static final int NEUTRAL_VALUE = 0;
    private static final int PRIMITIVE_VALUE = NEUTRAL_VALUE + 1;
    private static final int INTERFACE_VALUE = PRIMITIVE_VALUE + 1;
    private static final int ABSTRACT_VALUE = INTERFACE_VALUE + 1;
    private static final int LAST_SPECIAL_VALUE = ABSTRACT_VALUE;

    private static final int ARRAY_INDEX_SHIFT_SHIFT = 0;
    private static final int ARRAY_INDEX_SHIFT_MASK = 255;
    private static final int ARRAY_BASE_SHIFT = 8 + ARRAY_INDEX_SHIFT_SHIFT;
    private static final int ARRAY_BASE_MASK = 255;
    private static final int ARRAY_TAG_BITS = 2;
    private static final int ARRAY_TAG_SHIFT = Integer.SIZE - ARRAY_TAG_BITS;
    private static final int ARRAY_TAG_PRIMITIVE_VALUE = ~0x00; // 0xC0000000 >> 30
    private static final int ARRAY_TAG_OBJECT_VALUE = ~0x01; // 0x80000000 >> 30

    public static int forPrimitive() {
        return PRIMITIVE_VALUE;
    }

    public static int forInterface() {
        return INTERFACE_VALUE;
    }

    public static int forAbstract() {
        return ABSTRACT_VALUE;
    }

    public static int forInstance(int size) {
        assert size > LAST_SPECIAL_VALUE && size <= Integer.MAX_VALUE;
        int encoding = size;

        assert isInstance(encoding) && !isArray(encoding) && !isObjectArray(encoding) && !isPrimitiveArray(encoding);
        assert getInstanceSize(encoding).equal(WordFactory.unsigned(size));
        return encoding;
    }

    public static int forArray(boolean isObject, int arrayBaseOffset, int arrayIndexShift) {
        int tag = isObject ? ARRAY_TAG_OBJECT_VALUE : ARRAY_TAG_PRIMITIVE_VALUE;
        int encoding = (tag << ARRAY_TAG_SHIFT) | (arrayBaseOffset << ARRAY_BASE_SHIFT) | (arrayIndexShift << ARRAY_INDEX_SHIFT_SHIFT);

        assert !isInstance(encoding) && isArray(encoding);
        assert isObjectArray(encoding) == isObject;
        assert isPrimitiveArray(encoding) != isObject;
        assert getArrayBaseOffset(encoding).equal(WordFactory.unsigned(arrayBaseOffset));
        assert getArrayIndexShift(encoding) == arrayIndexShift;
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
