package com.oracle.truffle.espresso._native;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.meta.JavaKind;

@ExportLibrary(InteropLibrary.class)
public final class TruffleByteBuffer implements TruffleObject {

    private final ByteBuffer byteBuffer;

    private TruffleByteBuffer(@Pointer TruffleObject addressPtr, long byteCapacity) {
        if (byteCapacity < 0) {
            throw new IllegalArgumentException("negative requested capacity");
        }
        this.byteBuffer = NativeEnv.directByteBuffer(NativeEnv.interopAsPointer(addressPtr), byteCapacity);
    }

    private TruffleByteBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = Objects.requireNonNull(byteBuffer);
    }

    public static @Buffer TruffleObject create(ByteBuffer byteBuffer) {
        return new TruffleByteBuffer(byteBuffer);
    }

    public @Buffer TruffleObject create(@Pointer TruffleObject addressPtr, long size, JavaKind kind) {
        long byteCapacity = Math.multiplyExact(size, kind.getByteCount());
        return new TruffleByteBuffer(addressPtr, byteCapacity);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasBufferElements() {
        return true;
    }

    @ExportMessage
    long getBufferSize() throws UnsupportedMessageException {
        return byteBuffer.capacity();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isBufferWritable() {
        return !byteBuffer.isReadOnly();
    }

    @ExportMessage
    byte readBufferByte(long byteOffset, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readByte(this.byteBuffer, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferByte(long byteOffset, byte value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeByte(this.byteBuffer, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    short readBufferShort(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readShort(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferShort(ByteOrder order, long byteOffset, short value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeShort(this.byteBuffer, order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    int readBufferInt(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readInt(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferInt(ByteOrder order, long byteOffset, int value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeInt(this.byteBuffer, order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    long readBufferLong(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readLong(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferLong(ByteOrder order, long byteOffset, long value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeLong(this.byteBuffer, order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    float readBufferFloat(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readFloat(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferFloat(ByteOrder order, long byteOffset, float value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeFloat(this.byteBuffer, order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    double readBufferDouble(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readDouble(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferDouble(ByteOrder order, long byteOffset, double value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeDouble(this.byteBuffer, order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static byte readByte(ByteBuffer byteBuffer, int index) {
        return byteBuffer.get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static short readShort(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asShortBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static double readDouble(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asDoubleBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static float readFloat(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asFloatBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static long readLong(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asLongBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static int readInt(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asIntBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeByte(ByteBuffer byteBuffer, int index, byte value) {
        byteBuffer.put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeShort(ByteBuffer byteBuffer, ByteOrder order, int index, short value) {
        byteBuffer.order(order).asShortBuffer().put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeDouble(ByteBuffer byteBuffer, ByteOrder order, int index, double value) {
        byteBuffer.order(order).asDoubleBuffer().put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeFloat(ByteBuffer byteBuffer, ByteOrder order, int index, float value) {
        byteBuffer.order(order).asFloatBuffer().put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeLong(ByteBuffer byteBuffer, ByteOrder order, int index, long value) {
        byteBuffer.order(order).asLongBuffer().put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeInt(ByteBuffer byteBuffer, ByteOrder order, int index, int value) {
        byteBuffer.order(order).asIntBuffer().put(index, value);
    }
}
