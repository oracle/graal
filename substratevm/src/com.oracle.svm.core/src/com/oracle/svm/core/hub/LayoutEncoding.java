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
package com.oracle.svm.core.hub;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The layout encoding determines how the GC interprets an object. The following encodings are
 * currently used:
 * <ul>
 * <li>Special objects: a positive value less or equal than {@link #LAST_SPECIAL_VALUE}.</li>
 * <li>Instance objects: the layout encoding for instances is the aligned instance size (i.e., a
 * positive number greater than {@link #LAST_SPECIAL_VALUE}).</li>
 * <li>Array objects: the layout encoding for arrays is a negative number with the following
 * format:<br>
 *
 * <code>[tag:3, unused:9, base:12, indexShift:8]</code>
 * <ul>
 * <li>tag: determines element type and whether the object is a true array or a {@link Hybrid}.</li>
 * <li>unused: bits currently not used for any purpose</li>
 * <li>base: the array base offset</li>
 * <li>indexShift: the array index shift for accessing array elements or for computing the array
 * size based on the array length</li>
 * </ul>
 *
 * {@link Hybrid} objects are encoded like arrays but are treated like instance objects in other
 * places (e.g. {@link HubType}). Another difference to arrays is that hybrid objects need a
 * reference map because they have fields.</li>
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
    private static final int ARRAY_TAG_BITS = 3;
    private static final int ARRAY_TAG_SHIFT = Integer.SIZE - ARRAY_TAG_BITS;
    private static final int ARRAY_TAG_IDENTITY_BIT = 0b100;
    private static final int ARRAY_TAG_PRIMITIVE_BIT = 0b010;
    private static final int ARRAY_TAG_PURE_BIT = 0b001; // means non-hybrid
    private static final int ARRAY_TAG_PRIMITIVE_VALUE = ARRAY_TAG_IDENTITY_BIT | ARRAY_TAG_PRIMITIVE_BIT | ARRAY_TAG_PURE_BIT; // 0b111
    private static final int ARRAY_TAG_HYBRID_PRIMITIVE_VALUE = ARRAY_TAG_IDENTITY_BIT | ARRAY_TAG_PRIMITIVE_BIT;               // 0b110
    private static final int ARRAY_TAG_OBJECT_VALUE = ARRAY_TAG_IDENTITY_BIT | ARRAY_TAG_PURE_BIT;                              // 0b101
    private static final int ARRAY_TAG_HYBRID_OBJECT_VALUE = ARRAY_TAG_IDENTITY_BIT;                                            // 0b100

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
    private static void guaranteeEncoding(ResolvedJavaType type, boolean expected, boolean actual, String description) {
        if (actual != expected) {
            throw VMError.shouldNotReachHere(description + ": expected to be " + expected + ". " +
                            "This error is caused by an incorrect compact encoding of a type " +
                            "(a class, array or a primitive). The error occurred with the following type, but also could be caused " +
                            "by characteristics of the overall type hierarchy: " + type + ". Please report this problem and the " +
                            "conditions in which it occurs and include any noteworthy characteristics of the type hierarchy and " +
                            "architecture of the application and the libraries and frameworks it uses.");
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int forPureInstance(ResolvedJavaType type, int size) {
        guaranteeEncoding(type, true, size > LAST_SPECIAL_VALUE, "Instance type size must be above special values for encoding: " + size);
        int encoding = size;
        guaranteeEncoding(type, true, isPureInstance(encoding), "Instance type encoding denotes an instance");
        guaranteeEncoding(type, false, isArray(encoding) || isArrayLike(encoding), "Instance type encoding denotes an array-like object");
        guaranteeEncoding(type, false, isHybrid(encoding), "Instance type encoding denotes a hybrid");
        guaranteeEncoding(type, false, isObjectArray(encoding) || isArrayLikeWithObjectElements(encoding), "Instance type encoding denotes an object array");
        guaranteeEncoding(type, false, isPrimitiveArray(encoding) || isArrayLikeWithPrimitiveElements(encoding), "Instance type encoding denotes a primitive array");
        guaranteeEncoding(type, true, getPureInstanceSize(encoding).equal(WordFactory.unsigned(size)), "Instance type encoding size matches type size");
        return encoding;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int forArray(ResolvedJavaType type, boolean objectElements, int arrayBaseOffset, int arrayIndexShift) {
        return forArrayLike(type, false, objectElements, arrayBaseOffset, arrayIndexShift);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int forHybrid(ResolvedJavaType type, boolean objectElements, int arrayBaseOffset, int arrayIndexShift) {
        return forArrayLike(type, true, objectElements, arrayBaseOffset, arrayIndexShift);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int forArrayLike(ResolvedJavaType type, boolean isHybrid, boolean objectElements, int arrayBaseOffset, int arrayIndexShift) {
        assert isHybrid != type.isArray();
        int tag = isHybrid ? (objectElements ? ARRAY_TAG_HYBRID_OBJECT_VALUE : ARRAY_TAG_HYBRID_PRIMITIVE_VALUE)
                        : (objectElements ? ARRAY_TAG_OBJECT_VALUE : ARRAY_TAG_PRIMITIVE_VALUE);
        int encoding = (tag << ARRAY_TAG_SHIFT) | (arrayBaseOffset << ARRAY_BASE_SHIFT) | (arrayIndexShift << ARRAY_INDEX_SHIFT_SHIFT);

        guaranteeEncoding(type, true, isArrayLike(encoding), "Array-like object encoding denotes an array-like object");
        guaranteeEncoding(type, !isHybrid, isArray(encoding), "Encoding denotes an array");
        guaranteeEncoding(type, isHybrid, isHybrid(encoding), "Encoding denotes a hybrid");
        guaranteeEncoding(type, false, isPureInstance(encoding), "Array-like object encoding denotes an instance type");
        guaranteeEncoding(type, objectElements, isArrayLikeWithObjectElements(encoding), "Encoding denotes an array-like object with object elements");
        guaranteeEncoding(type, !objectElements, isArrayLikeWithPrimitiveElements(encoding), "Encoding denotes an array-like object with primitive elements");
        guaranteeEncoding(type, !isHybrid && objectElements, isObjectArray(encoding), "Encoding denotes an object array");
        guaranteeEncoding(type, !isHybrid && !objectElements, isPrimitiveArray(encoding), "Encoding denotes a primitive array");
        guaranteeEncoding(type, true, getArrayBaseOffset(encoding).equal(WordFactory.unsigned(arrayBaseOffset)),
                        "Encoding denotes a base offset of " + arrayBaseOffset + " (actual value: " + getArrayBaseOffset(encoding) + ')');
        guaranteeEncoding(type, true, getArrayIndexShift(encoding) == arrayIndexShift,
                        "Encoding denotes an index shift of " + arrayIndexShift + " (actual value: " + getArrayIndexShift(encoding) + ')');
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

    /** Note that this method does not consider hybrids special. */
    public static boolean isSpecial(int encoding) {
        return encoding >= NEUTRAL_VALUE && encoding <= LAST_SPECIAL_VALUE;
    }

    /** Tests if an encoding denotes a pure instance object, i.e. not a hybrid object or array. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isPureInstance(int encoding) {
        return encoding > LAST_SPECIAL_VALUE;
    }

    /** Determines the size of a pure instance object (i.e. not a hybrid object or array). */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getPureInstanceSize(int encoding) {
        return WordFactory.unsigned(encoding);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isArray(int encoding) {
        int mask = (ARRAY_TAG_IDENTITY_BIT | ARRAY_TAG_PURE_BIT) << ARRAY_TAG_SHIFT;
        return (encoding & mask) == mask;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isArrayLike(int encoding) {
        return encoding < NEUTRAL_VALUE;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isHybrid(int encoding) {
        int setBits = ARRAY_TAG_IDENTITY_BIT << ARRAY_TAG_SHIFT;
        int mask = setBits | (ARRAY_TAG_PURE_BIT << ARRAY_TAG_SHIFT);
        return (encoding & mask) == setBits;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isPrimitiveArray(int encoding) {
        return UnsignedMath.aboveOrEqual(encoding, ARRAY_TAG_PRIMITIVE_VALUE << ARRAY_TAG_SHIFT);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isObjectArray(int encoding) {
        return (encoding >>> ARRAY_TAG_SHIFT) == ARRAY_TAG_OBJECT_VALUE;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isArrayLikeWithPrimitiveElements(int encoding) {
        return UnsignedMath.aboveOrEqual(encoding, ARRAY_TAG_HYBRID_PRIMITIVE_VALUE << ARRAY_TAG_SHIFT);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isArrayLikeWithObjectElements(int encoding) {
        return encoding <= ~(~ARRAY_TAG_OBJECT_VALUE << ARRAY_TAG_SHIFT);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getArrayBaseOffsetAsInt(int encoding) {
        return (encoding >> ARRAY_BASE_SHIFT) & ARRAY_BASE_MASK;
    }

    // May be inlined because it does not deal in Pointers.
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getArrayBaseOffset(int encoding) {
        return WordFactory.unsigned(getArrayBaseOffsetAsInt(encoding));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getArrayIndexShift(int encoding) {
        return (encoding >> ARRAY_INDEX_SHIFT_SHIFT) & ARRAY_INDEX_SHIFT_MASK;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getSizeFromObject(Object obj) {
        return getSizeFromObjectInline(obj);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getSizeFromObjectInline(Object obj) {
        int encoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
        if (isArrayLike(encoding)) {
            return getArraySize(encoding, ArrayLengthNode.arrayLength(obj));
        } else {
            return getPureInstanceSize(encoding);
        }
    }

    /** Returns the end of the Object when the call started, e.g., for logging. */
    public static Pointer getObjectEnd(Object obj) {
        return getObjectEndInline(obj);
    }

    @AlwaysInline("GC performance")
    public static Pointer getObjectEndInline(Object obj) {
        final Pointer objStart = Word.objectToUntrackedPointer(obj);
        final UnsignedWord objSize = getSizeFromObjectInline(obj);
        return objStart.add(objSize);
    }

    public static boolean isArray(Object obj) {
        final int encoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
        return isArray(encoding);
    }

    public static boolean isArrayLike(Object obj) {
        final int encoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
        return isArrayLike(encoding);
    }

    @Fold
    protected static int getAlignmentMask() {
        return ImageSingletons.lookup(ObjectLayout.class).getAlignment() - 1;
    }
}
