package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Implementation of BytecodeDSLAccess that does not use Unsafe.
 */
final class BytecodeDSLCheckedAccess extends BytecodeDSLAccess {

    BytecodeDSLCheckedAccess() {
    }

    @Override
    public ByteArraySupport getByteArraySupport() {
        return BytecodeAccessor.MEMORY.getNativeChecked();
    }

    @Override
    public <T> T readObject(T[] arr, int index) {
        return arr[index];
    }

    @Override
    public <T> void writeObject(T[] arr, int index, T value) {
        arr[index] = value;
    }

    @Override
    public <T> T uncheckedCast(Object obj, Class<T> clazz) {
        return clazz.cast(obj);
    }

    @Override
    public byte getTag(Frame frame, int slot) {
        return frame.getTag(slot);
    }

    @Override
    public Object getObject(Frame frame, int slot) {
        return frame.getObject(slot);
    }

    @Override
    public boolean getBoolean(Frame frame, int slot) {
        return frame.getBoolean(slot);
    }

    @Override
    public int getInt(Frame frame, int slot) {
        return frame.getInt(slot);
    }

    @Override
    public long getLong(Frame frame, int slot) {
        return frame.getLong(slot);
    }

    @Override
    public double getDouble(Frame frame, int slot) {
        return frame.getDouble(slot);
    }

    @Override
    public byte getByte(Frame frame, int slot) {
        return frame.getByte(slot);
    }

    @Override
    public float getFloat(Frame frame, int slot) {
        return frame.getFloat(slot);
    }

    @Override
    public Object uncheckedGetObject(Frame frame, int slot) {
        return frame.getObject(slot);
    }

    @Override
    public boolean uncheckedGetBoolean(Frame frame, int slot) {
        return frame.getBoolean(slot);
    }

    @Override
    public byte uncheckedGetByte(Frame frame, int slot) {
        return frame.getByte(slot);
    }

    @Override
    public int uncheckedGetInt(Frame frame, int slot) {
        return frame.getInt(slot);
    }

    @Override
    public long uncheckedGetLong(Frame frame, int slot) {
        return frame.getLong(slot);
    }

    @Override
    public double uncheckedGetDouble(Frame frame, int slot) {
        return frame.getDouble(slot);
    }

    @Override
    public float uncheckedGetFloat(Frame frame, int slot) {
        return frame.getFloat(slot);
    }

    @Override
    public boolean expectBoolean(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectBoolean(slot);
    }

    @Override
    public byte expectByte(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectByte(slot);
    }

    @Override
    public int expectInt(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectInt(slot);
    }

    @Override
    public long expectLong(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectLong(slot);
    }

    @Override
    public Object expectObject(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectObject(slot);
    }

    @Override
    public float expectFloat(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectFloat(slot);
    }

    @Override
    public double expectDouble(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectDouble(slot);
    }

    @Override
    public void setObject(Frame frame, int slot, Object value) {
        frame.setObject(slot, value);
    }

    @Override
    public void setBoolean(Frame frame, int slot, boolean value) {
        frame.setBoolean(slot, value);
    }

    @Override
    public void setByte(Frame frame, int slot, byte value) {
        frame.setByte(slot, value);
    }

    @Override
    public void setInt(Frame frame, int slot, int value) {
        frame.setInt(slot, value);
    }

    @Override
    public void setLong(Frame frame, int slot, long value) {
        frame.setLong(slot, value);
    }

    @Override
    public void setFloat(Frame frame, int slot, float value) {
        frame.setFloat(slot, value);
    }

    @Override
    public void setDouble(Frame frame, int slot, double value) {
        frame.setDouble(slot, value);
    }

    @Override
    public void copy(Frame frame, int srcSlot, int dstSlot) {
        frame.copy(srcSlot, dstSlot);
    }

    @Override
    public void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length) {
        srcFrame.copyTo(srcOffset, dstFrame, dstOffset, length);
    }

    @Override
    public void copyObject(Frame frame, int srcSlot, int dstSlot) {
        frame.copyObject(srcSlot, dstSlot);
    }

    @Override
    public void copyPrimitive(Frame frame, int srcSlot, int dstSlot) {
        frame.copyPrimitive(srcSlot, dstSlot);
    }

    @Override
    public void clear(Frame frame, int slot) {
        frame.clear(slot);
    }

    @Override
    public void uncheckedSetObject(Frame frame, int slot, Object value) {
        frame.setObject(slot, value);
    }

    // Exposed for testing.

    public static short readShortBigEndian(byte[] arr, int index) {
        return (short) (((arr[index] & 0xFF) << 8) | (arr[index + 1] & 0xFF));
    }

    public static short readShortLittleEndian(byte[] arr, int index) {
        return (short) ((arr[index] & 0xFF) | ((arr[index + 1] & 0xFF) << 8));
    }

    public static int readIntBigEndian(byte[] arr, int index) {
        return ((arr[index] & 0xFF) << 24) | ((arr[index + 1] & 0xFF) << 16) | ((arr[index + 2] & 0xFF) << 8) | (arr[index + 3] & 0xFF);
    }

    public static int readIntLittleEndian(byte[] arr, int index) {
        return (arr[index] & 0xFF) | ((arr[index + 1] & 0xFF) << 8) | ((arr[index + 2] & 0xFF) << 16) | ((arr[index + 3] & 0xFF) << 24);
    }

    public static void writeShortBigEndian(byte[] arr, int index, short value) {
        arr[index] = (byte) (value >> 8);
        arr[index + 1] = (byte) value;
    }

    public static void writeShortLittleEndian(byte[] arr, int index, short value) {
        arr[index] = (byte) value;
        arr[index + 1] = (byte) (value >> 8);
    }

    public static void writeIntBigEndian(byte[] arr, int index, int value) {
        arr[index] = (byte) (value >> 24);
        arr[index + 1] = (byte) (value >> 16);
        arr[index + 2] = (byte) (value >> 8);
        arr[index + 3] = (byte) value;
    }

    public static void writeIntLittleEndian(byte[] arr, int index, int value) {
        arr[index] = (byte) (value);
        arr[index + 1] = (byte) (value >> 8);
        arr[index + 2] = (byte) (value >>> 16);
        arr[index + 3] = (byte) (value >> 24);
    }

}