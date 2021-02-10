package com.oracle.truffle.espresso._native.nfi;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso._native.Pointer;
import com.oracle.truffle.espresso._native.RawPointer;
import com.oracle.truffle.espresso.jni.ModifiedUtf8;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

public final class NativeUtils {
    public static ByteBuffer directByteBuffer(@Pointer TruffleObject addressPtr, long size, JavaKind kind) {
        return directByteBuffer(interopAsPointer(addressPtr), Math.multiplyExact(size, kind.getByteCount()));
    }

    private static final Constructor<? extends ByteBuffer> constructor;
    private static final Field addressField;

    @SuppressWarnings("unchecked")
    private static Class<? extends ByteBuffer> getByteBufferClass(String className) {
        try {
            return (Class<? extends ByteBuffer>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    static {
        try {
            Class<? extends ByteBuffer> clazz = getByteBufferClass("java.nio.DirectByteBuffer");
            Class<? extends ByteBuffer> bufferClazz = getByteBufferClass("java.nio.Buffer");
            constructor = clazz.getDeclaredConstructor(long.class, int.class);
            addressField = bufferClazz.getDeclaredField("address");
            addressField.setAccessible(true);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    public static ByteBuffer directByteBuffer(long address, long capacity) {
        ByteBuffer buffer = null;
        try {
            buffer = constructor.newInstance(address, Math.toIntExact(capacity));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    @TruffleBoundary
    public static long interopAsPointer(@Pointer TruffleObject interopPtr) {
        try {
            return InteropLibrary.getUncached().asPointer(interopPtr);
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static String interopPointerToString(@Pointer TruffleObject interopPtr) {
        return fromUTF8Ptr(interopAsPointer(interopPtr));
    }

    @TruffleBoundary
    public static ByteBuffer directByteBuffer(@Pointer TruffleObject addressPtr, long capacity) {
        ByteBuffer buffer = null;
        try {
            long address = interopAsPointer(addressPtr);
            buffer = constructor.newInstance(address, Math.toIntExact(capacity));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    @TruffleBoundary
    public static long byteBufferAddress(ByteBuffer byteBuffer) {
        try {
            assert byteBuffer.isDirect();
            return (long) addressField.get(byteBuffer);
        } catch (IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static @Pointer TruffleObject byteBufferPointer(ByteBuffer byteBuffer) {
        return new RawPointer(byteBufferAddress(byteBuffer));
    }

    public static String fromUTF8Ptr(@Pointer TruffleObject buffPtr) {
        return fromUTF8Ptr(interopAsPointer(buffPtr));
    }

    @TruffleBoundary
    public static String fromUTF8Ptr(long rawBytesPtr) {
        if (rawBytesPtr == 0) {
            return null;
        }
        ByteBuffer buf = directByteBuffer(rawBytesPtr, Integer.MAX_VALUE);

        int utfLen = 0;
        while (buf.get() != 0) {
            utfLen++;
        }

        byte[] bytes = new byte[utfLen];
        buf.clear();
        buf.get(bytes);
        try {
            return ModifiedUtf8.toJavaString(bytes);
        } catch (IOException e) {
            // return StaticObject.NULL;
            throw EspressoError.shouldNotReachHere(e);
        }
    }

}
