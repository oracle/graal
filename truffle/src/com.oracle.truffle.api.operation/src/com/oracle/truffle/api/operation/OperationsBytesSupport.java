package com.oracle.truffle.api.operation;

public class OperationsBytesSupport {
    public static final OperationsBytesSupport INSTANCE = new OperationsBytesSupport();

    public static OperationsBytesSupport littleEndian() {
        return INSTANCE;
    }

    private OperationsBytesSupport() {
    }

    @SuppressWarnings("static-method")
    public final byte getByte(byte[] data, int index) {
        return data[index];
    }

    @SuppressWarnings("static-method")
    public final void putByte(byte[] data, int index, byte value) {
        data[index] = value;
    }

    @SuppressWarnings("static-method")
    public final short getShort(byte[] data, int index) {
        return (short) (((data[index + 1] & 0xff) << 8) | (data[index] & 0xff));
    }

    @SuppressWarnings("static-method")
    public final void putShort(byte[] data, int index, short value) {
        data[index] = (byte) (value & 0xff);
        data[index + 1] = (byte) ((value >>> 8) & 0xff);
    }
}
