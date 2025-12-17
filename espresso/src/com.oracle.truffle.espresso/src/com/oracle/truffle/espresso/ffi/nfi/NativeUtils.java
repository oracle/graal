/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.ffi.nfi;

import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.IllegalMemoryAccessException;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;

/**
 * Utility class for performing low-level operations.
 */
public final class NativeUtils {
    /**
     * Returns a MemoryBuffer of the address represented by the given addressPtr with the capacity
     * to hold size many elements of the kind specified by the argument.
     *
     * @param addressPtr the address pointer
     * @param size How many elements the buffer can store.
     * @param kind the kind of elements the buffer should store
     * @param nativeMemory the NativeMemory implementation
     * @return The MemoryBuffer of the specified memory region.
     * @throws IllegalMemoryAccessException if {@code addressPtr} does not point to a valid memory
     *             region.
     */
    public static ByteBuffer wrapNativeMemory(@Pointer TruffleObject addressPtr, long size, JavaKind kind, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        return wrapNativeMemory(addressPtr, Math.multiplyExact(size, kind.getByteCount()), nativeMemory);
    }

    /**
     * Works as specified by
     * {@link NativeUtils#wrapNativeMemory(TruffleObject, long, JavaKind, NativeMemory)} with the
     * only difference that the returned {@link ByteBuffer} will hold exactly capacity many bytes.
     */
    @TruffleBoundary
    public static ByteBuffer wrapNativeMemory(@Pointer TruffleObject addressPtr, long capacity, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        return wrapNativeMemory(interopAsPointer(addressPtr), capacity, nativeMemory);
    }

    /**
     * Works as specified by
     * {@link NativeUtils#wrapNativeMemory(TruffleObject, long, JavaKind, NativeMemory)} with the
     * only difference that the returned {@link ByteBuffer} will hold exactly capacity many bytes.
     */
    @TruffleBoundary
    public static ByteBuffer wrapNativeMemory(long address, long longCapacity, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        return nativeMemory.wrapNativeMemory(address, Math.toIntExact(longCapacity));
    }

    /**
     * Performs {@link NativeUtils#wrapNativeMemory(TruffleObject, long, JavaKind, NativeMemory)}
     * but throws a guest {@link InternalError} if a host {@link IllegalMemoryAccessException}
     * occurs.
     */
    @TruffleBoundary
    public static ByteBuffer wrapNativeMemoryOrThrow(@Pointer TruffleObject addressPtr, long size, JavaKind kind, NativeMemory nativeMemory, Meta meta) {
        return wrapNativeMemoryOrThrow(addressPtr, Math.multiplyExact(size, kind.getByteCount()), nativeMemory, meta);
    }

    /**
     * Performs {@link NativeUtils#wrapNativeMemory(long, long, NativeMemory)} but throws a guest
     * {@link InternalError} if a host {@link IllegalMemoryAccessException} occurs.
     */
    @TruffleBoundary
    public static ByteBuffer wrapNativeMemoryOrThrow(@Pointer TruffleObject addressPtr, long capacity, NativeMemory nativeMemory, Meta meta) {
        try {
            return wrapNativeMemory(addressPtr, capacity, nativeMemory);
        } catch (IllegalMemoryAccessException e) {
            throw meta.throwInternalErrorBoundary(e.toString());
        }
    }

    /**
     * Performs {@link NativeUtils#interopPointerToString(TruffleObject, NativeMemory)} but throws a
     * guest {@link InternalError} if a host {@link IllegalMemoryAccessException} occurs.
     */
    @TruffleBoundary
    public static String interopPointerToStringOrThrow(@Pointer TruffleObject interopPtr, NativeMemory nativeMemory, Meta meta) {
        try {
            return NativeUtils.interopPointerToString(interopPtr, nativeMemory);
        } catch (IllegalMemoryAccessException e) {
            throw meta.throwInternalErrorBoundary(e.toString());
        }
    }

    @TruffleBoundary
    public static long interopAsPointer(@Pointer TruffleObject interopPtr) {
        try {
            return InteropLibrary.getUncached().asPointer(interopPtr);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    /**
     * Returns a String by decoding the memory region of interopPtr using UTF-8.
     *
     * @param interopPtr the interop pointer to decode
     * @param nativeMemory the native memory implementation
     * @return the decoded string
     * @throws IllegalMemoryAccessException if {@code interopPtr} does not point to a valid memory
     *             region.
     */
    public static String interopPointerToString(@Pointer TruffleObject interopPtr, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        long rawBytesPtr = interopAsPointer(interopPtr);
        if (rawBytesPtr == 0) {
            return null;
        }
        ByteBuffer buf = wrapNativeMemory(rawBytesPtr, Integer.MAX_VALUE, nativeMemory);
        while (buf.get() != 0) {
            // find the end of the utf8
        }
        buf.limit(buf.position() - 1);
        buf.position(0);
        return ModifiedUTF8.toJavaStringSafe(buf);
    }

    /**
     * Writes the provided int to the pointer.
     * 
     * @throws IllegalMemoryAccessException if {@code pointer} does not point to a valid memory
     *             region.
     */
    @TruffleBoundary
    public static void writeToIntPointer(InteropLibrary library, TruffleObject pointer, int value, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        if (library.isNull(pointer)) {
            throw new NullPointerException();
        }
        IntBuffer resultPointer = NativeUtils.wrapNativeMemory(pointer, 1, JavaKind.Int, nativeMemory).asIntBuffer();
        resultPointer.put(value);
    }

    /**
     * Writes the provided long to the pointer.
     *
     * @throws IllegalMemoryAccessException if {@code pointer} does not point to a valid memory
     *             region.
     */
    @TruffleBoundary
    public static void writeToLongPointer(InteropLibrary library, TruffleObject pointer, long value, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        if (library.isNull(pointer)) {
            throw new NullPointerException();
        }
        LongBuffer resultPointer = NativeUtils.wrapNativeMemory(pointer, 1, JavaKind.Long, nativeMemory).asLongBuffer();
        resultPointer.put(value);
    }

    /**
     * Writes the long value of the value pointer to the address given by the address pointer.
     *
     * @param library the InteropLibrary
     * @param pointer the address pointer
     * @param value the value provided as a pointer
     * @param nativeMemory the NativeMemory implementation
     * @throws IllegalMemoryAccessException if {@code pointer} or {@code value} does not point to a
     *             valid memory region.
     */
    public static void writeToPointerPointer(InteropLibrary library, TruffleObject pointer, TruffleObject value, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        writeToLongPointer(library, pointer, NativeUtils.interopAsPointer(value), nativeMemory);
    }

    /**
     * Dereferences the given pointer.
     * 
     * @throws IllegalMemoryAccessException if {@code pointer} does not point to a valid memory
     *             region.
     */
    @TruffleBoundary
    public static TruffleObject dereferencePointerPointer(InteropLibrary library, TruffleObject pointer, NativeMemory nativeMemory) throws IllegalMemoryAccessException {
        if (library.isNull(pointer)) {
            throw new NullPointerException();
        }
        LongBuffer buffer = NativeUtils.wrapNativeMemory(pointer, 1, JavaKind.Long, nativeMemory).asLongBuffer();
        return RawPointer.create(buffer.get());
    }
}
