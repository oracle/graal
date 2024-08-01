package com.oracle.truffle.api.bytecode;

import java.lang.reflect.Field;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import sun.misc.Unsafe;

/**
 * Implementation of BytecodeDSLAccess that uses Unsafe.
 */
final class BytecodeDSLUncheckedAccess extends BytecodeDSLAccess {

    static final Unsafe UNSAFE = initUnsafe();

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

    @Override
    public ByteArraySupport getByteArraySupport() {
        return BytecodeAccessor.MEMORY.getNativeUnsafe();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readObject(T[] arr, int index) {
        assert index >= 0 && index < arr.length;
        return (T) UNSAFE.getObject(arr, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
    }

    @Override
    public <T> void writeObject(T[] arr, int index, T value) {
        assert index >= 0 && index < arr.length;
        UNSAFE.putObject(arr, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T uncheckedCast(Object obj, Class<T> clazz) {
        return (T) obj;
    }

    @Override
    public byte getTag(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeGetTag(slot);
    }

    @Override
    public Object getObject(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeGetObject(slot);
    }

    @Override
    public int getInt(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeGetInt(slot);
    }

    @Override
    public boolean getBoolean(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeGetBoolean(slot);
    }

    @Override
    public long getLong(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeGetLong(slot);
    }

    @Override
    public double getDouble(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeGetDouble(slot);
    }

    @Override
    public byte getByte(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeGetByte(slot);
    }

    @Override
    public float getFloat(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeGetFloat(slot);
    }

    @Override
    public Object uncheckedGetObject(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeUncheckedGetObject(slot);
    }

    @Override
    public byte uncheckedGetByte(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeUncheckedGetByte(slot);
    }

    @Override
    public int uncheckedGetInt(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeUncheckedGetInt(slot);
    }

    @Override
    public boolean uncheckedGetBoolean(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeUncheckedGetBoolean(slot);
    }

    @Override
    public long uncheckedGetLong(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeUncheckedGetLong(slot);
    }

    @Override
    public double uncheckedGetDouble(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeUncheckedGetDouble(slot);
    }

    @Override
    public float uncheckedGetFloat(Frame frame, int slot) {
        return ((FrameWithoutBoxing) frame).unsafeUncheckedGetFloat(slot);
    }

    @Override
    public void setObject(Frame frame, int slot, Object value) {
        ((FrameWithoutBoxing) frame).unsafeSetObject(slot, value);
    }

    @Override
    public void setInt(Frame frame, int slot, int value) {
        ((FrameWithoutBoxing) frame).unsafeSetInt(slot, value);
    }

    @Override
    public void setBoolean(Frame frame, int slot, boolean value) {
        ((FrameWithoutBoxing) frame).unsafeSetBoolean(slot, value);
    }

    @Override
    public void setByte(Frame frame, int slot, byte value) {
        ((FrameWithoutBoxing) frame).unsafeSetByte(slot, value);
    }

    @Override
    public void setLong(Frame frame, int slot, long value) {
        ((FrameWithoutBoxing) frame).unsafeSetLong(slot, value);
    }

    @Override
    public void setFloat(Frame frame, int slot, float value) {
        ((FrameWithoutBoxing) frame).unsafeSetFloat(slot, value);
    }

    @Override
    public void setDouble(Frame frame, int slot, double value) {
        ((FrameWithoutBoxing) frame).unsafeSetDouble(slot, value);
    }

    @Override
    public void copy(Frame frame, int srcSlot, int dstSlot) {
        ((FrameWithoutBoxing) frame).unsafeCopy(srcSlot, dstSlot);
    }

    @Override
    public void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length) {
        ((FrameWithoutBoxing) srcFrame).unsafeCopyTo(srcOffset, ((FrameWithoutBoxing) dstFrame), dstOffset, length);
    }

    @Override
    public void copyObject(Frame frame, int srcSlot, int dstSlot) {
        ((FrameWithoutBoxing) frame).unsafeCopyObject(srcSlot, dstSlot);
    }

    @Override
    public void copyPrimitive(Frame frame, int srcSlot, int dstSlot) {
        ((FrameWithoutBoxing) frame).unsafeCopyPrimitive(srcSlot, dstSlot);
    }

    @Override
    public void clear(Frame frame, int slot) {
        ((FrameWithoutBoxing) frame).unsafeClear(slot);
    }

    @Override
    public void uncheckedSetObject(Frame frame, int slot, Object value) {
        ((FrameWithoutBoxing) frame).unsafeUncheckedSetObject(slot, value);
    }

    @Override
    public boolean expectBoolean(Frame frame, int slot) throws UnexpectedResultException {
        return ((FrameWithoutBoxing) frame).unsafeExpectBoolean(slot);
    }

    @Override
    public byte expectByte(Frame frame, int slot) throws UnexpectedResultException {
        return ((FrameWithoutBoxing) frame).unsafeExpectByte(slot);
    }

    @Override
    public int expectInt(Frame frame, int slot) throws UnexpectedResultException {
        return ((FrameWithoutBoxing) frame).unsafeExpectInt(slot);
    }

    @Override
    public long expectLong(Frame frame, int slot) throws UnexpectedResultException {
        return ((FrameWithoutBoxing) frame).unsafeExpectLong(slot);
    }

    @Override
    public Object expectObject(Frame frame, int slot) throws UnexpectedResultException {
        return ((FrameWithoutBoxing) frame).unsafeExpectObject(slot);
    }

    @Override
    public float expectFloat(Frame frame, int slot) throws UnexpectedResultException {
        return ((FrameWithoutBoxing) frame).unsafeExpectFloat(slot);
    }

    @Override
    public double expectDouble(Frame frame, int slot) throws UnexpectedResultException {
        return ((FrameWithoutBoxing) frame).unsafeExpectDouble(slot);
    }

}