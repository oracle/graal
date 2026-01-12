/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.memory.panama;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.ffi.memory.ChunkedNativeMemory;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.substitutions.Collect;

/**
 * An implementation of a chunked native memory where every chunk holds a MemorySegment. How the
 * MemorySegments are allocated depends on the Arena provided in the constructor.
 */
public class MemorySegmentChunkedMemoryImpl extends ChunkedNativeMemory<MemorySegment> {

    private static final VarHandle VH_JAVA_BOOLEAN = ValueLayout.JAVA_BOOLEAN.varHandle();
    private static final VarHandle VH_JAVA_BYTE = ValueLayout.JAVA_BYTE.varHandle();
    private static final VarHandle VH_JAVA_CHAR = ValueLayout.JAVA_CHAR.varHandle();
    private static final VarHandle VH_JAVA_SHORT = ValueLayout.JAVA_SHORT.varHandle();
    private static final VarHandle VH_JAVA_INT = ValueLayout.JAVA_INT.varHandle();
    private static final VarHandle VH_JAVA_FLOAT = ValueLayout.JAVA_FLOAT.varHandle();
    private static final VarHandle VH_JAVA_DOUBLE = ValueLayout.JAVA_DOUBLE.varHandle();
    private static final VarHandle VH_JAVA_LONG = ValueLayout.JAVA_LONG.varHandle();

    protected final Arena arena;

    protected MemorySegmentChunkedMemoryImpl(Arena arena) {
        this.arena = arena;
    }

    @Override
    @TruffleBoundary
    protected MemorySegment allocateChunk(long bytes) throws MemoryAllocationException {
        // At least 8 bytes to ensure aligned accesses work.
        try {
            return arena.allocate(bytes, Long.BYTES);
        } catch (IllegalArgumentException e) {
            // We should not reach here as we control the arguments.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        } catch (OutOfMemoryError e) {
            throw new MemoryAllocationException(e);
        } catch (Exception e) {
            // allocation failed for some system reason
            throw new MemoryAllocationException(e);
        }
    }

    @Override
    protected long getChunkSize(MemorySegment chunk) {
        return chunk.byteSize();
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public void putByteImpl(MemorySegment chunk, long chunkOffset, byte value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_BYTE.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_BYTE.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_BYTE.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_BYTE.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public void putShortImpl(MemorySegment chunk, long chunkOffset, short value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_SHORT.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_SHORT.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_SHORT.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_SHORT.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public void putIntImpl(MemorySegment chunk, long chunkOffset, int value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_INT.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_INT.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_INT.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_INT.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public void putLongImpl(MemorySegment chunk, long chunkOffset, long value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_LONG.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_LONG.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_LONG.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_LONG.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public byte getByteImpl(MemorySegment chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (byte) VH_JAVA_BYTE.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (byte) VH_JAVA_BYTE.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (byte) VH_JAVA_BYTE.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (byte) VH_JAVA_BYTE.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public short getShortImpl(MemorySegment chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (short) VH_JAVA_SHORT.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (short) VH_JAVA_SHORT.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (short) VH_JAVA_SHORT.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (short) VH_JAVA_SHORT.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public int getIntImpl(MemorySegment chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (int) VH_JAVA_INT.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (int) VH_JAVA_INT.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (int) VH_JAVA_INT.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (int) VH_JAVA_INT.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public long getLongImpl(MemorySegment chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (long) VH_JAVA_LONG.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (long) VH_JAVA_LONG.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (long) VH_JAVA_LONG.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (long) VH_JAVA_LONG.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public boolean compareAndSetIntImpl(MemorySegment chunk, long chunkOffset, int expected, int newValue) {
        return VH_JAVA_INT.compareAndSet(chunk, chunkOffset, expected, newValue);
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public boolean compareAndSetLongImpl(MemorySegment chunk, long chunkOffset, long expected, long newValue) {
        return VH_JAVA_LONG.compareAndSet(chunk, chunkOffset, expected, newValue);
    }

    @Override
    public void setMemoryImpl(MemorySegment chunk, long chunkOffset, long bytes, byte value) {
        chunk.asSlice(chunkOffset, bytes).fill(value);
    }

    @Override
    public void copyMemoryImpl(MemorySegment fromChunk, long fromOffset, MemorySegment toChunk, long toOffset, long byteSize) {
        MemorySegment.copy(fromChunk, fromOffset, toChunk, toOffset, byteSize);
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public void writeMemoryImpl(MemorySegment chunk, long chunkOffset, byte[] buf) {
        MemorySegment.copy(buf, 0, chunk, ValueLayout.JAVA_BYTE, chunkOffset, buf.length);
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public void readMemoryImpl(MemorySegment chunk, long chunkOffset, byte[] buf) {
        MemorySegment.copy(chunk, ValueLayout.JAVA_BYTE, chunkOffset, buf, 0, buf.length);
    }

    @Override
    public ByteBuffer wrapChunk(MemorySegment chunk, long chunkOffset, int bytes) {
        return chunk.asSlice(chunkOffset, bytes).asByteBuffer();
    }

    // region overwrites for default methods in NativeMemory

    @Override
    @TruffleBoundary(allowInlining = true)
    public void putBoolean(long address, boolean value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, 1);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_BOOLEAN.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_BOOLEAN.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_BOOLEAN.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_BOOLEAN.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public void putChar(long address, char value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, Character.BYTES);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_CHAR.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_CHAR.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_CHAR.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_CHAR.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public void putFloat(long address, float value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, Float.BYTES);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_FLOAT.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_FLOAT.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_FLOAT.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_FLOAT.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public void putDouble(long address, double value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, Double.BYTES);
        switch (accessMode) {
            case MemoryAccessMode.PLAIN -> VH_JAVA_DOUBLE.set(chunk, chunkOffset, value);
            case MemoryAccessMode.OPAQUE -> VH_JAVA_DOUBLE.setOpaque(chunk, chunkOffset, value);
            case MemoryAccessMode.RELEASE_ACQUIRE -> VH_JAVA_DOUBLE.setRelease(chunk, chunkOffset, value);
            case MemoryAccessMode.VOLATILE -> VH_JAVA_DOUBLE.setVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public boolean getBoolean(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, 1);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (boolean) VH_JAVA_BOOLEAN.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (boolean) VH_JAVA_BOOLEAN.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (boolean) VH_JAVA_BOOLEAN.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (boolean) VH_JAVA_BOOLEAN.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public char getChar(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, Character.BYTES);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (char) VH_JAVA_CHAR.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (char) VH_JAVA_CHAR.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (char) VH_JAVA_CHAR.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (char) VH_JAVA_CHAR.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public float getFloat(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, Float.BYTES);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (float) VH_JAVA_FLOAT.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (float) VH_JAVA_FLOAT.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (float) VH_JAVA_FLOAT.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (float) VH_JAVA_FLOAT.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public double getDouble(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, Double.BYTES);
        return switch (accessMode) {
            case MemoryAccessMode.PLAIN -> (double) VH_JAVA_DOUBLE.get(chunk, chunkOffset);
            case MemoryAccessMode.OPAQUE -> (double) VH_JAVA_DOUBLE.getOpaque(chunk, chunkOffset);
            case MemoryAccessMode.RELEASE_ACQUIRE -> (double) VH_JAVA_DOUBLE.getAcquire(chunk, chunkOffset);
            case MemoryAccessMode.VOLATILE -> (double) VH_JAVA_DOUBLE.getVolatile(chunk, chunkOffset);
        };
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public long compareAndExchangeLong(long address, long expected, long newValue) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, Long.BYTES);
        return (long) VH_JAVA_LONG.compareAndExchange(chunk, chunkOffset, expected, newValue);
    }

    @Override
    @TruffleBoundary(allowInlining = true)
    public int compareAndExchangeInt(long address, int expected, int newValue) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        MemorySegment chunk = getChunk(address);
        validateAccess(chunk.byteSize(), chunkOffset, Integer.BYTES);
        return (int) VH_JAVA_INT.compareAndExchange(chunk, chunkOffset, expected, newValue);
    }

    // endregion

    @Collect(NativeMemory.class)
    public static final class Provider implements NativeMemory.Provider {

        public static final String ID = "MemorySegmentChunkedMemory";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public NativeMemory create() {
            // Memory Segments will be garbage collected
            return new MemorySegmentChunkedMemoryImpl(Arena.ofAuto());
        }

        @Override
        public boolean needsNativeAccess() {
            return false;
        }
    }
}
