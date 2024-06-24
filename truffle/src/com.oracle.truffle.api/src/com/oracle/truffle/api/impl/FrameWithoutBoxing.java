/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 * For host compilation all final instance fields of this case are treated as immutable in the
 * Compiler IR. This allows frame array reads to move out of loops even if there are side-effects in
 * them. In order to guarantee this, the frame must not escape a reference of <code>this</code> in
 * the constructor to any other call.
 */
public final class FrameWithoutBoxing implements VirtualFrame, MaterializedFrame {
    private static final String UNEXPECTED_STATIC_WRITE = "Unexpected static write of non-static frame slot";
    private static final String UNEXPECTED_NON_STATIC_READ = "Unexpected non-static read of static frame slot";
    private static final String UNEXPECTED_NON_STATIC_WRITE = "Unexpected non-static write of static frame slot";

    private static final boolean ASSERTIONS_ENABLED;

    private final FrameDescriptor descriptor;
    private final Object[] arguments;

    private final Object[] indexedLocals;
    private final long[] indexedPrimitiveLocals;
    private final byte[] indexedTags;

    private Object[] auxiliarySlots;

    private static final Object OBJECT_LOCATION = new Object();
    private static final Object PRIMITIVE_LOCATION = new Object();

    private static final long INT_MASK = 0xFFFFFFFFL;

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
    public static final byte STATIC_TAG = 8;

    private static final byte STATIC_OBJECT_TAG = STATIC_TAG | OBJECT_TAG;
    private static final byte STATIC_LONG_TAG = STATIC_TAG | LONG_TAG;
    private static final byte STATIC_INT_TAG = STATIC_TAG | INT_TAG;
    private static final byte STATIC_DOUBLE_TAG = STATIC_TAG | DOUBLE_TAG;
    private static final byte STATIC_FLOAT_TAG = STATIC_TAG | FLOAT_TAG;
    private static final byte STATIC_BOOLEAN_TAG = STATIC_TAG | BOOLEAN_TAG;
    private static final byte STATIC_BYTE_TAG = STATIC_TAG | BYTE_TAG;
    private static final byte STATIC_ILLEGAL_TAG = STATIC_TAG | ILLEGAL_TAG;

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
        assert STATIC_TAG == FrameSlotKind.Static.tag;

        ASSERTIONS_ENABLED = areAssertionsEnabled();
    }

    @SuppressWarnings("all")
    private static boolean areAssertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
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
        // Make sure the state of ASSERTIONS_ENABLED matches with
        // the state of assertions at runtime
        // This can be an issue since this class is initialized at build time in native-image
        assert ASSERTIONS_ENABLED;
        /*
         * Important note: Make sure this frame reference does not escape to any other method in
         * this constructor, otherwise the immutable invariant for frame final fields may not hold.
         * This may lead to very hard to debug bugs.
         */
        final int indexedSize = descriptor.getNumberOfSlots();
        final int auxiliarySize = descriptor.getNumberOfAuxiliarySlots();
        Object defaultValue = descriptor.getDefaultValue();
        final Accessor.FrameSupport frameSupport = DefaultRuntimeAccessor.FRAME;
        final Object[] indexedLocalsArray;
        final long[] indexedPrimitiveLocalsArray;
        final byte[] indexedTagsArray;
        final Object[] auxiliarySlotsArray;
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
            if (frameSupport.usesAllStaticMode(descriptor)) {
                Arrays.fill(indexedTagsArray, STATIC_TAG);
            } else if (frameSupport.usesMixedStaticMode(descriptor)) {
                for (int slot = 0; slot < indexedTagsArray.length; slot++) {
                    if (descriptor.getSlotKind(slot) == FrameSlotKind.Static) {
                        indexedTagsArray[slot] = STATIC_TAG;
                    }
                }
            }
        }
        if (auxiliarySize == 0) {
            auxiliarySlotsArray = EMPTY_OBJECT_ARRAY;
        } else {
            auxiliarySlotsArray = new Object[auxiliarySize];
        }
        this.descriptor = descriptor;
        this.arguments = arguments;
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
    public FrameWithoutBoxing materialize() {
        ImplAccessor.frameSupportAccessor().markMaterializeCalled(descriptor);
        return this;
    }

    private static long extend(int value) {
        return value & INT_MASK;
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return unsafeCast(descriptor, FrameDescriptor.class, true, true, false);
    }

    private static FrameSlotTypeException frameSlotTypeException() throws FrameSlotTypeException {
        CompilerAsserts.neverPartOfCompilation();
        throw new FrameSlotTypeException();
    }

    private static long getPrimitiveOffset(int slotIndex) {
        return Unsafe.ARRAY_LONG_BASE_OFFSET + slotIndex * (long) Unsafe.ARRAY_LONG_INDEX_SCALE;
    }

    @Override
    public byte getTag(int slotIndex) {
        try {
            final byte tag = getIndexedTags()[slotIndex];
            return tag < STATIC_TAG ? tag : STATIC_TAG;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw CompilerDirectives.shouldNotReachHere("invalid indexed slot", e);
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
        return (T) value;
    }

    @SuppressWarnings("unused")
    private static long unsafeGetLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getLong(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static Object unsafeGetObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getObject(receiver, offset);
    }

    @SuppressWarnings("unused")
    private static void unsafePutLong(Object receiver, long offset, long value, Object locationIdentity) {
        UNSAFE.putLong(receiver, offset, value);
    }

    @SuppressWarnings("unused")
    private static void unsafePutObject(Object receiver, long offset, Object value, Object locationIdentity) {
        UNSAFE.putObject(receiver, offset, value);
    }

    @Override
    public Object getValue(int slot) {
        byte tag = getTag(slot);
        assert (indexedTags[slot] & STATIC_TAG) == 0 : UNEXPECTED_NON_STATIC_READ;
        switch (tag) {
            case BOOLEAN_TAG:
                return getBoolean(slot);
            case BYTE_TAG:
                return getByte(slot);
            case INT_TAG:
                return getInt(slot);
            case DOUBLE_TAG:
                return getDouble(slot);
            case LONG_TAG:
                return getLong(slot);
            case FLOAT_TAG:
                return getFloat(slot);
            case OBJECT_TAG:
                return getObject(slot);
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
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
        return (byte) (int) unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
    }

    @Override
    public void setByte(int slot, byte value) {
        verifyIndexedSet(slot, BYTE_TAG);
        unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), extend(value), PRIMITIVE_LOCATION);
    }

    @Override
    public boolean getBoolean(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, BOOLEAN_TAG);
        return (int) unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION) != 0;
    }

    @Override
    public void setBoolean(int slot, boolean value) {
        verifyIndexedSet(slot, BOOLEAN_TAG);
        unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), value ? 1L : 0L, PRIMITIVE_LOCATION);
    }

    @Override
    public float getFloat(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, FLOAT_TAG);
        return Float.intBitsToFloat((int) unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION));
    }

    @Override
    public void setFloat(int slot, float value) {
        verifyIndexedSet(slot, FLOAT_TAG);
        unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), extend(Float.floatToRawIntBits(value)), PRIMITIVE_LOCATION);
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
        return (int) unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION);
    }

    @Override
    public void setInt(int slot, int value) {
        verifyIndexedSet(slot, INT_TAG);
        unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), extend(value), PRIMITIVE_LOCATION);
    }

    @Override
    public double getDouble(int slot) throws FrameSlotTypeException {
        boolean condition = verifyIndexedGet(slot, DOUBLE_TAG);
        return Double.longBitsToDouble(unsafeGetLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), condition, PRIMITIVE_LOCATION));
    }

    @Override
    public void setDouble(int slot, double value) {
        verifyIndexedSet(slot, DOUBLE_TAG);
        unsafePutLong(getIndexedPrimitiveLocals(), getPrimitiveOffset(slot), Double.doubleToRawLongBits(value), PRIMITIVE_LOCATION);
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

    @Override
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
        assert (indexedTags[slot] & STATIC_TAG) == 0 : UNEXPECTED_NON_STATIC_WRITE;
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
        byte tag = getIndexedTags()[slot];
        assert (tag & STATIC_TAG) == 0 : UNEXPECTED_NON_STATIC_READ;
        return tag;
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
    public boolean isStatic(int slot) {
        return getTag(slot) == STATIC_TAG;
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

    @Override
    public Object getObjectStatic(int slot) {
        assert indexedTags[slot] == STATIC_OBJECT_TAG : "Unexpected read of static object value";

        return indexedLocals[slot];
    }

    @Override
    public void setObjectStatic(int slot, Object value) {
        assert (indexedTags[slot] & STATIC_TAG) != 0 : UNEXPECTED_STATIC_WRITE;
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_OBJECT_TAG;
        }

        indexedLocals[slot] = value;
    }

    @Override
    public byte getByteStatic(int slot) {
        assert indexedTags[slot] == STATIC_BYTE_TAG : "Unexpected read of static byte value";

        return (byte) (int) indexedPrimitiveLocals[slot];
    }

    @Override
    public void setByteStatic(int slot, byte value) {
        assert (indexedTags[slot] & STATIC_TAG) != 0 : UNEXPECTED_STATIC_WRITE;
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_BYTE_TAG;
        }

        indexedPrimitiveLocals[slot] = extend(value);
    }

    @Override
    public boolean getBooleanStatic(int slot) {
        assert indexedTags[slot] == STATIC_BOOLEAN_TAG : "Unexpected read of static boolean value";

        return (int) indexedPrimitiveLocals[slot] != 0;
    }

    @Override
    public void setBooleanStatic(int slot, boolean value) {
        assert (indexedTags[slot] & STATIC_TAG) != 0 : UNEXPECTED_STATIC_WRITE;
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_BOOLEAN_TAG;
        }

        indexedPrimitiveLocals[slot] = value ? 1L : 0L;
    }

    @Override
    public int getIntStatic(int slot) {
        assert indexedTags[slot] == STATIC_INT_TAG : "Unexpected read of static int value";

        return (int) indexedPrimitiveLocals[slot];
    }

    @Override
    public void setIntStatic(int slot, int value) {
        assert (indexedTags[slot] & STATIC_TAG) != 0 : UNEXPECTED_STATIC_WRITE;
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_INT_TAG;
        }

        indexedPrimitiveLocals[slot] = extend(value);
    }

    @Override
    public long getLongStatic(int slot) {
        assert indexedTags[slot] == STATIC_LONG_TAG : "Unexpected read of static long value";

        return indexedPrimitiveLocals[slot];
    }

    @Override
    public void setLongStatic(int slot, long value) {
        assert (indexedTags[slot] & STATIC_TAG) != 0 : UNEXPECTED_STATIC_WRITE;
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_LONG_TAG;
        }

        indexedPrimitiveLocals[slot] = value;
    }

    @Override
    public float getFloatStatic(int slot) {
        assert indexedTags[slot] == STATIC_FLOAT_TAG : "Unexpected read of static float value";

        return Float.intBitsToFloat((int) indexedPrimitiveLocals[slot]);
    }

    @Override
    public void setFloatStatic(int slot, float value) {
        assert (indexedTags[slot] & STATIC_TAG) != 0 : UNEXPECTED_STATIC_WRITE;
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_FLOAT_TAG;
        }

        indexedPrimitiveLocals[slot] = extend(Float.floatToRawIntBits(value));
    }

    @Override
    public double getDoubleStatic(int slot) {
        assert indexedTags[slot] == STATIC_DOUBLE_TAG : "Unexpected read of static double value";

        return Double.longBitsToDouble(indexedPrimitiveLocals[slot]);
    }

    @Override
    public void setDoubleStatic(int slot, double value) {
        assert (indexedTags[slot] & STATIC_TAG) != 0 : UNEXPECTED_STATIC_WRITE;
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_DOUBLE_TAG;
        }

        indexedPrimitiveLocals[slot] = Double.doubleToRawLongBits(value);
    }

    @Override
    public void copyPrimitiveStatic(int srcSlot, int destSlot) {
        assert indexedTags[srcSlot] > STATIC_TAG && (indexedTags[destSlot] & STATIC_TAG) != 0 : "Unexpected copy of static primitive value ";
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[destSlot] = indexedTags[srcSlot];
        }

        indexedPrimitiveLocals[destSlot] = indexedPrimitiveLocals[srcSlot];
    }

    @Override
    public void copyObjectStatic(int srcSlot, int destSlot) {
        assert (indexedTags[srcSlot] == STATIC_OBJECT_TAG || indexedTags[srcSlot] == STATIC_ILLEGAL_TAG) && (indexedTags[destSlot] & STATIC_TAG) != 0 : "Unexpected copy of static object value";
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[destSlot] = indexedTags[srcSlot];
        }

        indexedLocals[destSlot] = indexedLocals[srcSlot];
    }

    @Override
    public void copyStatic(int srcSlot, int destSlot) {
        assert indexedTags[srcSlot] >= STATIC_TAG && indexedTags[destSlot] >= STATIC_TAG : "Unexpected copy of static value";
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[destSlot] = indexedTags[srcSlot];
        }

        indexedLocals[destSlot] = indexedLocals[srcSlot];
        indexedPrimitiveLocals[destSlot] = indexedPrimitiveLocals[srcSlot];
    }

    @Override
    public void swapPrimitiveStatic(int first, int second) {
        assert indexedTags[first] > STATIC_TAG && indexedTags[second] > STATIC_TAG : "Unexpected swap of static primitive value";
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            final byte swapTag = indexedTags[first];
            indexedTags[first] = indexedTags[second];
            indexedTags[second] = swapTag;
        }

        final long firstValue = indexedPrimitiveLocals[first];
        final long secondValue = indexedPrimitiveLocals[second];

        indexedPrimitiveLocals[first] = secondValue;
        indexedPrimitiveLocals[second] = firstValue;
    }

    @Override
    public void swapObjectStatic(int first, int second) {
        assert (indexedTags[first] == STATIC_OBJECT_TAG || indexedTags[first] == STATIC_ILLEGAL_TAG) &&
                        (indexedTags[second] == STATIC_OBJECT_TAG || indexedTags[second] == STATIC_ILLEGAL_TAG) : "Unexpected swap of static object value";
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            final byte swapTag = indexedTags[first];
            indexedTags[first] = indexedTags[second];
            indexedTags[second] = swapTag;
        }

        final Object firstValue = indexedLocals[first];
        final Object secondValue = indexedLocals[second];

        indexedLocals[first] = secondValue;
        indexedLocals[second] = firstValue;
    }

    @Override
    public void swapStatic(int first, int second) {
        assert indexedTags[first] >= STATIC_TAG && indexedTags[second] >= STATIC_TAG : "Unexpected swap of static value";
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            final byte swapTag = indexedTags[first];
            indexedTags[first] = indexedTags[second];
            indexedTags[second] = swapTag;
        }

        final Object firstValue = indexedLocals[first];
        final Object secondValue = indexedLocals[second];
        final long firstPrimitiveValue = indexedPrimitiveLocals[first];
        final long secondPrimitiveValue = indexedPrimitiveLocals[second];

        indexedLocals[first] = secondValue;
        indexedLocals[second] = firstValue;
        indexedPrimitiveLocals[first] = secondPrimitiveValue;
        indexedPrimitiveLocals[second] = firstPrimitiveValue;
    }

    @Override
    public void clearPrimitiveStatic(int slot) {
        assert indexedTags[slot] > STATIC_TAG : "Unexpected clear of static primitive value";
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_ILLEGAL_TAG;
        }

        if (CompilerDirectives.inCompiledCode()) {
            // Avoids keeping track of cleared frame slots in FrameStates
            indexedPrimitiveLocals[slot] = 0L;
        }
    }

    @Override
    public void clearObjectStatic(int slot) {
        assert indexedTags[slot] == STATIC_OBJECT_TAG || indexedTags[slot] == STATIC_ILLEGAL_TAG : "Unexpected clear of static object value";
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_ILLEGAL_TAG;
        }

        indexedLocals[slot] = null;
    }

    @Override
    public void clearStatic(int slot) {
        assert indexedTags[slot] >= STATIC_TAG : "Unexpected clear of static value";
        // We use this check instead of the assert keyword to update the tags in PE'd code.
        if (ASSERTIONS_ENABLED) {
            indexedTags[slot] = STATIC_ILLEGAL_TAG;
        }

        if (CompilerDirectives.inCompiledCode()) {
            // Avoid keeping track of cleared frame slots in FrameStates
            indexedPrimitiveLocals[slot] = 0L;
        }
        indexedLocals[slot] = null;
    }

    /**
     * Marker method to be called before performing a frame transfer.
     */
    void startOSRTransfer() {
    }

    /**
     * This method is used to transfer a static slot from a source frame to a target frame before or
     * after OSR. This method must exclusively be used inside
     * {@code BytecodeOSRMetadata#transferIndexedFrameSlot}. It is necessary to support static
     * assertions.
     *
     * @param target The target frame (The frame descriptor of this and the target frame must match)
     * @param slot The slot that should be transferred
     */
    void transferOSRStaticSlot(FrameWithoutBoxing target, int slot) {
        if (ASSERTIONS_ENABLED) {
            final byte tag = indexedTags[slot];
            indexedTags[slot] = STATIC_OBJECT_TAG;
            target.setObjectStatic(slot, getObjectStatic(slot));
            indexedTags[slot] = STATIC_LONG_TAG;
            target.setLongStatic(slot, getLongStatic(slot));
            indexedTags[slot] = tag;
            target.setStaticSlotTag(slot, tag);
        } else {
            target.setObjectStatic(slot, getObjectStatic(slot));
            target.setLongStatic(slot, getLongStatic(slot));
        }
    }

    private void setStaticSlotTag(int slot, byte tag) {
        indexedTags[slot] = tag;
    }
}
