/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.impl;

import java.lang.reflect.Field;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

import sun.misc.Unsafe;

/**
 * More efficient implementation of the Truffle frame that has no safety checks for frame accesses
 * and therefore is much faster. Should not be used during debugging as potential misuses of the
 * frame object would show up very late and would be hard to identify.
 */
@SuppressWarnings("deprecation")
public final class FrameWithoutBoxing implements VirtualFrame, MaterializedFrame {
    private final FrameDescriptor descriptor;
    private final Object[] arguments;
    private Object[] locals;
    private long[] primitiveLocals;
    private byte[] tags;

    private final Object[] indexedLocals;
    private final long[] indexedPrimitiveLocals;
    private final byte[] indexedTags;

    private Object[] auxiliarySlots;

    private static final Object OBJECT_LOCATION = new Object();
    private static final Object PRIMITIVE_LOCATION = new Object();

    /*
     * Changing these constants implies changes in NewFrameNode.java as well:
     */
    public static final byte OBJECT_TAG = 0;
    public static final byte LONG_TAG = 1;
    public static final byte INT_TAG = 2;
    public static final byte DOUBLE_TAG = 3;
    public static final byte FLOAT_TAG = 4;
    public static final byte BOOLEAN_TAG = 5;
    public static final byte BYTE_TAG = 6;
    public static final byte ILLEGAL_TAG = 7;

    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private static final long[] EMPTY_LONG_ARRAY = {};
    private static final byte[] EMPTY_BYTE_ARRAY = {};

    private static final Unsafe UNSAFE = initUnsafe();

    static {
        assert OBJECT_TAG == FrameSlotKind.Object.tag;
        assert ILLEGAL_TAG == FrameSlotKind.Illegal.tag;
        assert LONG_TAG == FrameSlotKind.Long.tag;
        assert INT_TAG == FrameSlotKind.Int.tag;
        assert DOUBLE_TAG == FrameSlotKind.Double.tag;
        assert FLOAT_TAG == FrameSlotKind.Float.tag;
        assert BOOLEAN_TAG == FrameSlotKind.Boolean.tag;
        assert BYTE_TAG == FrameSlotKind.Byte.tag;
    }

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    public FrameWithoutBoxing(FrameDescriptor descriptor, Object[] arguments) {
        final int size = descriptor.getSize();
        final int indexedSize = descriptor.getNumberOfSlots();
        final int auxiliarySize = descriptor.getNumberOfAuxiliarySlots();
        Object defaultValue = descriptor.getDefaultValue();
        final Object[] localsArray;
        final long[] primitiveLocalsArray;
        final byte[] tagsArray;
        final Object[] indexedLocalsArray;
        final long[] indexedPrimitiveLocalsArray;
        final byte[] indexedTagsArray;
        final Object[] auxiliarySlotsArray;
        if (size == 0) {
            localsArray = EMPTY_OBJECT_ARRAY;
            primitiveLocalsArray = EMPTY_LONG_ARRAY;
            tagsArray = EMPTY_BYTE_ARRAY;
        } else {
            localsArray = new Object[size];
            if (defaultValue != null) {
                Arrays.fill(localsArray, defaultValue);
            }
            primitiveLocalsArray = new long[size];
            tagsArray = new byte[size];
        }
        if (indexedSize == 0) {
            indexedLocalsArray = EMPTY_OBJECT_ARRAY;
            indexedPrimitiveLocalsArray = EMPTY_LONG_ARRAY;
            indexedTagsArray = EMPTY_BYTE_ARRAY;
        } else {
            indexedLocalsArray = new Object[indexedSize];
            if (defaultValue != null) {
                Arrays.fill(indexedLocalsArray, defaultValue);
            }
            indexedPrimitiveLocalsArray = new long[indexedSize];
            indexedTagsArray = new byte[indexedSize];
        }
        if (auxiliarySize == 0) {
            auxiliarySlotsArray = EMPTY_OBJECT_ARRAY;
        } else {
            auxiliarySlotsArray = new Object[auxiliarySize];
        }
        this.descriptor = descriptor;
        this.arguments = arguments;
        this.locals = localsArray;
        this.primitiveLocals = primitiveLocalsArray;
        this.tags = tagsArray;
        this.indexedLocals = indexedLocalsArray;
        this.indexedPrimitiveLocals = indexedPrimitiveLocalsArray;
        this.indexedTags = indexedTagsArray;
        this.auxiliarySlots = auxiliarySlotsArray;
    }

    @Override
    public Object[] getArguments() {
        return unsafeCast(arguments, Object[].class, true, true, true);
    }

    @Override
    public MaterializedFrame materialize() {
        ImplAccessor.frameSupportAccessor().markMaterializeCalled(descriptor);
        return this;
    }

    @Override
    public Object getObject(com.oracle.truffle.api.frame.FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, OBJECT_TAG);
        return getObjectUnsafe(slotIndex, slot, condition);
    }

    private Object[] getLocals() {
        return unsafeCast(locals, Object[].class, true, true, true);
    }

    private long[] getPrimitiveLocals() {
        return unsafeCast(primitiveLocals, long[].class, true, true, true);
    }

    public byte[] getTags() {
        return unsafeCast(tags, byte[].class, true, true, true);
    }

    Object getObjectUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, boolean condition) {
        return unsafeGetObject(getLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slotIndex * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, condition, slot);
    }

    @Override
    public void setObject(com.oracle.truffle.api.frame.FrameSlot slot, Object value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, OBJECT_TAG);
        setObjectUnsafe(slotIndex, slot, value);
    }

    private void setObjectUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, Object value) {
        unsafePutObject(getLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slotIndex * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, value, slot);
    }

    @Override
    public byte getByte(com.oracle.truffle.api.frame.FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, BYTE_TAG);
        return getByteUnsafe(slotIndex, slot, condition);
    }

    byte getByteUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return (byte) unsafeGetInt(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setByte(com.oracle.truffle.api.frame.FrameSlot slot, byte value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, BYTE_TAG);
        setByteUnsafe(slotIndex, slot, value);
    }

    private void setByteUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, byte value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutInt(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public boolean getBoolean(com.oracle.truffle.api.frame.FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, BOOLEAN_TAG);
        return getBooleanUnsafe(slotIndex, slot, condition);
    }

    boolean getBooleanUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetInt(getPrimitiveLocals(), offset, condition, slot) != 0;
    }

    @Override
    public void setBoolean(com.oracle.truffle.api.frame.FrameSlot slot, boolean value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, BOOLEAN_TAG);
        setBooleanUnsafe(slotIndex, slot, value);
    }

    private void setBooleanUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, boolean value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutInt(getPrimitiveLocals(), offset, value ? 1 : 0, slot);
    }

    @Override
    public float getFloat(com.oracle.truffle.api.frame.FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, FLOAT_TAG);
        return getFloatUnsafe(slotIndex, slot, condition);
    }

    float getFloatUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetFloat(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setFloat(com.oracle.truffle.api.frame.FrameSlot slot, float value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, FLOAT_TAG);
        setFloatUnsafe(slotIndex, slot, value);
    }

    private void setFloatUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, float value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutFloat(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public long getLong(com.oracle.truffle.api.frame.FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, LONG_TAG);
        return getLongUnsafe(slotIndex, slot, condition);
    }

    long getLongUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetLong(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setLong(com.oracle.truffle.api.frame.FrameSlot slot, long value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, LONG_TAG);
        setLongUnsafe(slotIndex, slot, value);
    }

    private void setLongUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, long value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutLong(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public int getInt(com.oracle.truffle.api.frame.FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, INT_TAG);
        return getIntUnsafe(slotIndex, slot, condition);
    }

    int getIntUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetInt(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setInt(com.oracle.truffle.api.frame.FrameSlot slot, int value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, INT_TAG);
        setIntUnsafe(slotIndex, slot, value);
    }

    private void setIntUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, int value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutInt(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public double getDouble(com.oracle.truffle.api.frame.FrameSlot slot) throws FrameSlotTypeException {
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = verifyGet(slotIndex, DOUBLE_TAG);
        return getDoubleUnsafe(slotIndex, slot, condition);
    }

    double getDoubleUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, boolean condition) {
        long offset = getPrimitiveOffset(slotIndex);
        return unsafeGetDouble(getPrimitiveLocals(), offset, condition, slot);
    }

    @Override
    public void setDouble(com.oracle.truffle.api.frame.FrameSlot slot, double value) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, DOUBLE_TAG);
        setDoubleUnsafe(slotIndex, slot, value);
    }

    private void setDoubleUnsafe(int slotIndex, com.oracle.truffle.api.frame.FrameSlot slot, double value) {
        long offset = getPrimitiveOffset(slotIndex);
        unsafePutDouble(getPrimitiveLocals(), offset, value, slot);
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return unsafeCast(descriptor, FrameDescriptor.class, true, true, false);
    }

    private void verifySet(int slotIndex, byte tag) {
        try {
            getTags()[slotIndex] = tag;
        } catch (ArrayIndexOutOfBoundsException e) {
            resizeAndGetTagsOrThrow(slotIndex)[slotIndex] = tag;
        }
    }

    private boolean verifyGet(int slotIndex, byte expectedTag) throws FrameSlotTypeException {
        byte actualTag = getTagChecked(slotIndex);
        boolean condition = actualTag == expectedTag;
        if (!condition) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw frameSlotTypeException();
        }
        return condition;
    }

    private byte getTagChecked(int slotIndex) {
        try {
            return getTags()[slotIndex];
        } catch (ArrayIndexOutOfBoundsException e) {
            return resizeAndGetTagsOrThrow(slotIndex)[slotIndex];
        }
    }

    private static FrameSlotTypeException frameSlotTypeException() throws FrameSlotTypeException {
        CompilerAsserts.neverPartOfCompilation();
        throw new FrameSlotTypeException();
    }

    private byte[] resizeAndGetTagsOrThrow(int slotIndex) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (resize()) {
            byte[] newTags = getTags();
            if (Integer.compareUnsigned(slotIndex, newTags.length) < 0) {
                return newTags;
            }
        }
        throw outOfBoundsException(slotIndex);
    }

    private static IllegalArgumentException outOfBoundsException(int slotIndex) {
        CompilerAsserts.neverPartOfCompilation();
        throw new IllegalArgumentException("The frame slot '" + slotIndex + "' is not known by the frame descriptor.");
    }

    private static long getPrimitiveOffset(int slotIndex) {
        return Unsafe.ARRAY_LONG_BASE_OFFSET + slotIndex * (long) Unsafe.ARRAY_LONG_INDEX_SCALE;
    }

    @Override
    public Object getValue(com.oracle.truffle.api.frame.FrameSlot slot) {
        byte tag = getTag(slot);
        int slotIndex = getFrameSlotIndex(slot);
        boolean condition = (tag == BOOLEAN_TAG);
        if (condition) {
            return getBooleanUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == BYTE_TAG);
        if (condition) {
            return getByteUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == INT_TAG);
        if (condition) {
            return getIntUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == DOUBLE_TAG);
        if (condition) {
            return getDoubleUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == LONG_TAG);
        if (condition) {
            return getLongUnsafe(slotIndex, slot, condition);
        }
        condition = (tag == FLOAT_TAG);
        if (condition) {
            return getFloatUnsafe(slotIndex, slot, condition);
        }
        condition = tag == OBJECT_TAG;
        assert condition;
        return getObjectUnsafe(slotIndex, slot, condition);
    }

    boolean resize() {
        CompilerAsserts.neverPartOfCompilation();
        int oldSize = tags.length;
        int newSize = descriptor.getSize();
        if (newSize > oldSize) {
            locals = Arrays.copyOf(locals, newSize);
            Arrays.fill(locals, oldSize, newSize, descriptor.getDefaultValue());
            primitiveLocals = Arrays.copyOf(primitiveLocals, newSize);
            tags = Arrays.copyOf(tags, newSize);
            return true;
        }
        return false;
    }

    @Override
    public byte getTag(int slotIndex) {
        try {
            return getIndexedTags()[slotIndex];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw CompilerDirectives.shouldNotReachHere("invalid indexed slot", e);
        }
    }

    private byte getTag(com.oracle.truffle.api.frame.FrameSlot slot) {
        int slotIndex = getFrameSlotIndex(slot);
        try {
            return getTags()[slotIndex];
        } catch (ArrayIndexOutOfBoundsException e) {
            return resizeAndGetTags()[slotIndex];
        }
    }

    private byte[] resizeAndGetTags() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        resize();
        return getTags();
    }

    @Override
    public boolean isObject(com.oracle.truffle.api.frame.FrameSlot slot) {
        return getTag(slot) == OBJECT_TAG;
    }

    @Override
    public boolean isByte(com.oracle.truffle.api.frame.FrameSlot slot) {
        return getTag(slot) == BYTE_TAG;
    }

    @Override
    public boolean isBoolean(com.oracle.truffle.api.frame.FrameSlot slot) {
        return getTag(slot) == BOOLEAN_TAG;
    }

    @Override
    public boolean isInt(com.oracle.truffle.api.frame.FrameSlot slot) {
        return getTag(slot) == INT_TAG;
    }

    @Override
    public boolean isLong(com.oracle.truffle.api.frame.FrameSlot slot) {
        return getTag(slot) == LONG_TAG;
    }

    @Override
    public boolean isFloat(com.oracle.truffle.api.frame.FrameSlot slot) {
        return getTag(slot) == FLOAT_TAG;
    }

    @Override
    public boolean isDouble(com.oracle.truffle.api.frame.FrameSlot slot) {
        return getTag(slot) == DOUBLE_TAG;
    }

    @Override
    public void clear(com.oracle.truffle.api.frame.FrameSlot slot) {
        int slotIndex = getFrameSlotIndex(slot);
        verifySet(slotIndex, ILLEGAL_TAG);
        setObjectUnsafe(slotIndex, slot, null);
        if (CompilerDirectives.inCompiledCode()) {
            setLongUnsafe(slotIndex, slot, 0L);
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
        return (T) value;
    }

    @SuppressWarnings("unused")
    private static int unsafeGetInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getInt(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static long unsafeGetLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getLong(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static float unsafeGetFloat(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getFloat(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static double unsafeGetDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getDouble(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static Object unsafeGetObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getObject(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static void unsafePutInt(Object receiver, long offset, int value, Object locationIdentity) {
        UNSAFE.putInt(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    private static void unsafePutLong(Object receiver, long offset, long value, Object locationIdentity) {
        UNSAFE.putLong(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    private static void unsafePutFloat(Object receiver, long offset, float value, Object locationIdentity) {
        UNSAFE.putFloat(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    private static void unsafePutDouble(Object receiver, long offset, double value, Object locationIdentity) {
        UNSAFE.putDouble(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    private static void unsafePutObject(Object receiver, long offset, Object value, Object locationIdentity) {
        UNSAFE.putObject(receiver, offset, value);
    }

    @Override
    public Object getValue(int slot) {
        byte tag = getTag(slot);
        boolean condition = (tag == BOOLEAN_TAG);
        if (condition) {
            return unsafeGetInt(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION) != 0;
        }
        condition = (tag == BYTE_TAG);
        if (condition) {
            return (byte) unsafeGetInt(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
        }
        condition = (tag == INT_TAG);
        if (condition) {
            return unsafeGetInt(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
        }
        condition = (tag == DOUBLE_TAG);
        if (condition) {
            return unsafeGetDouble(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
        }
        condition = (tag == LONG_TAG);
        if (condition) {
            return unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
        }
        condition = (tag == FLOAT_TAG);
        if (condition) {
            return unsafeGetFloat(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
        }
        condition = tag == OBJECT_TAG;
        assert condition;
        return unsafeGetObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slot * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, condition, OBJECT_LOCATION);

    }

    @SuppressWarnings("deprecation")
    private static int getFrameSlotIndex(com.oracle.truffle.api.frame.FrameSlot slot) {
        return slot.getIndex();
    }

    private Object[] getIndexedLocals() {
        return unsafeCast(indexedLocals, Object[].class, true, true, true);
    }

    private long[] getIndexedPrimitiveLocals() {
        return unsafeCast(this.indexedPrimitiveLocals, long[].class, true, true, true);
    }

    private byte[] getIndexedTags() {
        return unsafeCast(indexedTags, byte[].class, true, true, true);
    }

    @Override
    public Object getObject(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, OBJECT_TAG);
        return unsafeGetObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slot * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, condition, OBJECT_LOCATION);
    }

    @Override
    public void setObject(int slot, Object value) {
        verifyIndexedSet(slot, OBJECT_TAG);
        unsafePutObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slot * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, value, OBJECT_LOCATION);
    }

    @Override
    public byte getByte(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, BYTE_TAG);
        return (byte) unsafeGetInt(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
    }

    @Override
    public void setByte(int slot, byte value) {
        verifyIndexedSet(slot, BYTE_TAG);
        unsafePutInt(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), value, PRIMITIVE_LOCATION);
    }

    @Override
    public boolean getBoolean(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, BOOLEAN_TAG);
        return unsafeGetInt(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION) != 0;
    }

    @Override
    public void setBoolean(int slot, boolean value) {
        verifyIndexedSet(slot, BOOLEAN_TAG);
        unsafePutInt(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), value ? 1 : 0, PRIMITIVE_LOCATION);
    }

    @Override
    public float getFloat(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, FLOAT_TAG);
        return unsafeGetFloat(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
    }

    @Override
    public void setFloat(int slot, float value) {
        verifyIndexedSet(slot, FLOAT_TAG);
        unsafePutFloat(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), value, PRIMITIVE_LOCATION);
    }

    @Override
    public long getLong(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, LONG_TAG);
        return unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
    }

    @Override
    public void setLong(int slot, long value) {
        verifyIndexedSet(slot, LONG_TAG);
        unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), value, PRIMITIVE_LOCATION);
    }

    @Override
    public int getInt(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, INT_TAG);
        return unsafeGetInt(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
    }

    @Override
    public void setInt(int slot, int value) {
        verifyIndexedSet(slot, INT_TAG);
        unsafePutInt(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), value, PRIMITIVE_LOCATION);
    }

    @Override
    public double getDouble(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, DOUBLE_TAG);
        return unsafeGetDouble(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
    }

    @Override
    public void setDouble(int slot, double value) {
        verifyIndexedSet(slot, DOUBLE_TAG);
        unsafePutDouble(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), value, PRIMITIVE_LOCATION);
    }

    @Override
    public void copy(int srcSlot, int destSlot) {
        byte tag = getIndexedTagChecked(srcSlot);
        Object value = unsafeGetObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + srcSlot * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, true, OBJECT_LOCATION);
        verifyIndexedSet(destSlot, tag);
        unsafePutObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + destSlot * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, value, OBJECT_LOCATION);
        long primitiveValue = unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(srcSlot), true, PRIMITIVE_LOCATION);
        unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(destSlot), primitiveValue, PRIMITIVE_LOCATION);
    }

    public void swap(int first, int second) {
        byte firstTag = getIndexedTagChecked(first);
        Object firstValue = unsafeGetObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + first * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, true, OBJECT_LOCATION);
        long firstPrimitiveValue = unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(first), true, PRIMITIVE_LOCATION);
        byte secondTag = getIndexedTagChecked(second);
        Object secondValue = unsafeGetObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + second * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, true, OBJECT_LOCATION);
        long secondPrimitiveValue = unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(second), true, PRIMITIVE_LOCATION);

        verifyIndexedSet(first, secondTag);
        verifyIndexedSet(second, firstTag);
        unsafePutObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + first * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, secondValue, OBJECT_LOCATION);
        unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(first), secondPrimitiveValue, PRIMITIVE_LOCATION);
        unsafePutObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + second * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, firstValue, OBJECT_LOCATION);
        unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(second), firstPrimitiveValue, PRIMITIVE_LOCATION);
    }

    private void verifyIndexedSet(int slot, byte tag) {
        // this may raise an AIOOBE
        getIndexedTags()[slot] = tag;
    }

    private boolean verifyIndexedGet(int slot, byte expectedTag) throws FrameSlotTypeException {
        byte actualTag = getIndexedTagChecked(slot);
        boolean condition = actualTag == expectedTag;
        if (!condition) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw frameSlotTypeException();
        }
        return condition;
    }

    private byte getIndexedTagChecked(int slot) {
        // this may raise an AIOOBE
        return getIndexedTags()[slot];
    }

    @Override
    public boolean isObject(int slot) {
        return getTag(slot) == OBJECT_TAG;
    }

    @Override
    public boolean isByte(int slot) {
        return getTag(slot) == BYTE_TAG;
    }

    @Override
    public boolean isBoolean(int slot) {
        return getTag(slot) == BOOLEAN_TAG;
    }

    @Override
    public boolean isInt(int slot) {
        return getTag(slot) == INT_TAG;
    }

    @Override
    public boolean isLong(int slot) {
        return getTag(slot) == LONG_TAG;
    }

    @Override
    public boolean isFloat(int slot) {
        return getTag(slot) == FLOAT_TAG;
    }

    @Override
    public boolean isDouble(int slot) {
        return getTag(slot) == DOUBLE_TAG;
    }

    @Override
    public void clear(int slot) {
        verifyIndexedSet(slot, ILLEGAL_TAG);
        unsafePutObject(getIndexedLocals(), Unsafe.ARRAY_OBJECT_BASE_OFFSET + slot * (long) Unsafe.ARRAY_OBJECT_INDEX_SCALE, null, OBJECT_LOCATION);
        if (CompilerDirectives.inCompiledCode()) {
            unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), 0L, PRIMITIVE_LOCATION);
        }
    }

    @Override
    public void setAuxiliarySlot(int slot, Object value) {
        if (auxiliarySlots.length <= slot) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            auxiliarySlots = Arrays.copyOf(auxiliarySlots, descriptor.getNumberOfAuxiliarySlots());
        }
        auxiliarySlots[slot] = value;
    }

    @Override
    public Object getAuxiliarySlot(int slot) {
        return slot < auxiliarySlots.length ? auxiliarySlots[slot] : null;
    }
}
