package com.oracle.truffle.espresso._native;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;

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
public class TruffleByteBuffer implements TruffleObject {

    private final ByteBuffer byteBuffer;

    TruffleByteBuffer(@Pointer TruffleObject addressPtr, long size, JavaKind kind) {
        long requestedCapacity = Math.multiplyExact(size, kind.getByteCount());
        this.byteBuffer = NativeEnv.directByteBuffer(NativeEnv.interopAsPointer(addressPtr), requestedCapacity);
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
            return byteBuffer.get(index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferByte(long byteOffset, byte value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            byteBuffer.put(index, value);
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
            return byteBuffer.order(order).asShortBuffer().get(index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    void writeBufferShort(ByteOrder order, long byteOffset, short value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            byteBuffer.order(order).asShortBuffer().put(index, value);
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
            return byteBuffer.order(order).asIntBuffer().get(index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferInt(ByteOrder order, long byteOffset, int value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            byteBuffer.order(order).asIntBuffer().put(index, value);
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
            return byteBuffer.order(order).asLongBuffer().get(index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferLong(ByteOrder order, long byteOffset, long value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            byteBuffer.order(order).asLongBuffer().put(index, value);
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
            return byteBuffer.order(order).asFloatBuffer().get(index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferFloat(ByteOrder order, long byteOffset, float value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            byteBuffer.order(order).asFloatBuffer().put(index, value);
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
            return byteBuffer.order(order).asDoubleBuffer().get(index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferDouble(ByteOrder order, long byteOffset, double value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            byteBuffer.order(order).asDoubleBuffer().put(index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }
}
