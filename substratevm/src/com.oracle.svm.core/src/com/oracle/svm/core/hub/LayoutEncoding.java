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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.calc.UnsignedMath;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.word.Word;
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
        int encoding = forPureInstance(size);
        guaranteeEncoding(type, true, isPureInstance(encoding), "Instance type encoding denotes an instance");
        guaranteeEncoding(type, false, isArray(encoding) || isArrayLike(encoding), "Instance type encoding denotes an array-like object");
        guaranteeEncoding(type, false, isHybrid(encoding), "Instance type encoding denotes a hybrid");
        guaranteeEncoding(type, false, isObjectArray(encoding) || isArrayLikeWithObjectElements(encoding), "Instance type encoding denotes an object array");
        guaranteeEncoding(type, false, isPrimitiveArray(encoding) || isArrayLikeWithPrimitiveElements(encoding), "Instance type encoding denotes a primitive array");
        guaranteeEncoding(type, true, getPureInstanceAllocationSize(encoding).equal(Word.unsigned(size)), "Instance type encoding size matches type size");
        return encoding;
    }

    public static int forPureInstance(int size) {
        assert size > LAST_SPECIAL_VALUE;
        return size;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int forArray(ResolvedJavaType type, boolean objectElements, int arrayBaseOffset, int arrayIndexShift) {
        return forArrayLike(type, false, objectElements, arrayBaseOffset, arrayIndexShift);
    }

    public static int forArray(boolean objectElements, int arrayBaseOffset, int arrayIndexShift) {
        return forArrayLike(false, objectElements, arrayBaseOffset, arrayIndexShift);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int forHybrid(ResolvedJavaType type, boolean objectElements, int arrayBaseOffset, int arrayIndexShift) {
        return forArrayLike(type, true, objectElements, arrayBaseOffset, arrayIndexShift);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static int forDynamicHub(ResolvedJavaType type, int vtableOffset, int vtableIndexShift) {
        return forArrayLike(type, true, false, vtableOffset, vtableIndexShift);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int forArrayLike(ResolvedJavaType type, boolean isHybrid, boolean objectElements, int arrayBaseOffset, int arrayIndexShift) {
        assert isHybrid != type.isArray();
        int encoding = forArrayLike(isHybrid, objectElements, arrayBaseOffset, arrayIndexShift);

        guaranteeEncoding(type, true, isArrayLike(encoding), "Array-like object encoding denotes an array-like object");
        guaranteeEncoding(type, !isHybrid, isArray(encoding), "Encoding denotes an array");
        guaranteeEncoding(type, isHybrid, isHybrid(encoding), "Encoding denotes a hybrid");
        guaranteeEncoding(type, false, isPureInstance(encoding), "Array-like object encoding denotes an instance type");
        guaranteeEncoding(type, objectElements, isArrayLikeWithObjectElements(encoding), "Encoding denotes an array-like object with object elements");
        guaranteeEncoding(type, !objectElements, isArrayLikeWithPrimitiveElements(encoding), "Encoding denotes an array-like object with primitive elements");
        guaranteeEncoding(type, !isHybrid && objectElements, isObjectArray(encoding), "Encoding denotes an object array");
        guaranteeEncoding(type, !isHybrid && !objectElements, isPrimitiveArray(encoding), "Encoding denotes a primitive array");
        guaranteeEncoding(type, true, getArrayBaseOffset(encoding).equal(Word.unsigned(arrayBaseOffset)),
                        "Encoding denotes a base offset of " + arrayBaseOffset + " (actual value: " + getArrayBaseOffset(encoding) + ')');
        guaranteeEncoding(type, true, getArrayIndexShift(encoding) == arrayIndexShift,
                        "Encoding denotes an index shift of " + arrayIndexShift + " (actual value: " + getArrayIndexShift(encoding) + ')');
        return encoding;
    }

    private static int forArrayLike(boolean isHybrid, boolean objectElements, int arrayBaseOffset, int arrayIndexShift) {
        int tag = isHybrid ? (objectElements ? ARRAY_TAG_HYBRID_OBJECT_VALUE : ARRAY_TAG_HYBRID_PRIMITIVE_VALUE)
                        : (objectElements ? ARRAY_TAG_OBJECT_VALUE : ARRAY_TAG_PRIMITIVE_VALUE);
        return (tag << ARRAY_TAG_SHIFT) | (arrayBaseOffset << ARRAY_BASE_SHIFT) | (arrayIndexShift << ARRAY_INDEX_SHIFT_SHIFT);
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

    /**
     * Determines the size of a pure instance object (i.e. not a hybrid object or array) at
     * allocation, that is, without an identity hash code field if such a field is optional.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getPureInstanceAllocationSize(int encoding) {
        return Word.unsigned(encoding);
    }

    /**
     * Determines the size of a pure instance object (i.e. not a hybrid object or array) with or
     * without an identity hash code field (if such a field is optional).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getPureInstanceSize(DynamicHub hub, boolean withOptionalIdHashField) {
        UnsignedWord size = getPureInstanceAllocationSize(hub.getLayoutEncoding());
        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        if (withOptionalIdHashField && ol.isIdentityHashFieldOptional()) {
            int afterIdHashField = hub.getIdentityHashOffset() + Integer.BYTES;
            if (size.belowThan(afterIdHashField)) {
                /* Identity hash is at the end of the object and does not fit in a gap. */
                size = Word.unsigned(ol.alignUp(afterIdHashField));
            }
        }
        return size;
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
        return Word.unsigned(getArrayBaseOffsetAsInt(encoding));
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
        return getArrayBaseOffset(encoding).add(Word.unsigned(index).shiftLeft(getArrayIndexShift(encoding)));
    }

    /**
     * Determines the size of an array at allocation, that is, without an identity hash code field
     * if such a field is optional.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getArrayAllocationSize(int encoding, int length) {
        return getArraySize(encoding, length, false);
    }

    /**
     * Determines the size of an array with or without an identity hash code field, if such a field
     * is optional.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getArraySize(int encoding, int length, boolean withOptionalIdHashField) {
        long unalignedSize = getArrayElementOffset(encoding, length).rawValue();
        long totalSize = ConfigurationValues.getObjectLayout().computeArrayTotalSize(unalignedSize, withOptionalIdHashField);
        return Word.unsigned(totalSize);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getIdentityHashOffset(Object obj) {
        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        if (ol.isIdentityHashFieldInObjectHeader()) {
            return ol.getObjectHeaderIdentityHashOffset();
        }

        DynamicHub hub = KnownIntrinsics.readHub(obj);
        int encoding = hub.getLayoutEncoding();
        if (ol.isIdentityHashFieldOptional() && isArrayLike(encoding)) {
            long unalignedSize = getArrayElementOffset(encoding, ArrayLengthNode.arrayLength(obj)).rawValue();
            return (int) ol.getArrayIdentityHashOffset(unalignedSize);
        } else {
            return hub.getIdentityHashOffset();
        }
    }

    @Uninterruptible(reason = "Prevent a GC moving the object or interfering with its identity hash state.", callerMustBe = true)
    public static UnsignedWord getSizeFromObject(Object obj) {
        boolean withOptionalIdHashField = ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional() && hasOptionalIdentityHashField(obj);
        return getSizeFromObjectInline(obj, withOptionalIdHashField);
    }

    public static UnsignedWord getSizeFromObjectAddOptionalIdHashField(Object obj) {
        return getSizeFromObjectInline(obj, true);
    }

    /**
     * Returns the size of the object in the instant of the call, which can have already become
     * stale after returning. This can be useful for diagnostic output.
     */
    @Uninterruptible(reason = "Caller is aware the value can be stale, but required by callee.")
    public static UnsignedWord getMomentarySizeFromObject(Object obj) {
        return getSizeFromObject(obj);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getSizeFromObjectInGC(Object obj) {
        return getSizeFromObjectInlineInGC(obj);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getSizeFromObjectInlineInGC(Object obj) {
        return getSizeFromObjectInlineInGC(obj, false);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getSizeFromObjectInlineInGC(Object obj, boolean addOptionalIdHashField) {
        boolean withOptionalIdHashField = addOptionalIdHashField ||
                        (ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional() && hasOptionalIdentityHashField(obj));
        return getSizeFromObjectInline(obj, withOptionalIdHashField);
    }

    @AlwaysInline("GC performance")
    public static UnsignedWord getSizeFromObjectWithoutOptionalIdHashFieldInGC(Object obj) {
        return getSizeFromObjectInline(obj, false);
    }

    @AlwaysInline("Actual inlining decided by callers.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getSizeFromObjectInline(Object obj, boolean withOptionalIdHashField) {
        DynamicHub hub = KnownIntrinsics.readHub(obj);
        int encoding = hub.getLayoutEncoding();
        if (isArrayLike(encoding)) {
            return getArraySize(encoding, ArrayLengthNode.arrayLength(obj), withOptionalIdHashField);
        } else {
            return getPureInstanceSize(hub, withOptionalIdHashField);
        }
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean hasOptionalIdentityHashField(Object obj) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        Word header = oh.readHeaderFromPointer(Word.objectToUntrackedPointer(obj));
        return oh.hasOptionalIdentityHashField(header);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getObjectEndInGC(Object obj) {
        return getObjectEndInlineInGC(obj);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getObjectEndInlineInGC(Object obj) {
        UnsignedWord size = getSizeFromObjectInlineInGC(obj, false);
        return Word.objectToUntrackedPointer(obj).add(size);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer getImageHeapObjectEnd(Object obj) {
        // Image heap objects never move and always have an identity hash code field.
        UnsignedWord size = getSizeFromObjectInline(obj, true);
        return Word.objectToUntrackedPointer(obj).add(size);
    }

    public static boolean isArray(Object obj) {
        final int encoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
        return isArray(encoding);
    }

    public static boolean isArrayLike(Object obj) {
        final int encoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
        return isArrayLike(encoding);
    }
}
