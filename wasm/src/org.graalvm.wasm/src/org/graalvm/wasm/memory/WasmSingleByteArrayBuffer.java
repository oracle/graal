package org.graalvm.wasm.memory;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import java.util.Arrays;

import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

public class WasmSingleByteArrayBuffer implements WasmByteArrayBuffer {
    private static final int MAX_CONSTANT_ATTEMPTS = 5;

    @CompilationFinal private Assumption constantMemoryBufferAssumption;

    @CompilationFinal(dimensions = 0) private byte[] constantBuffer;

    private byte[] dynamicBuffer;

    private int constantAttempts = 0;

    WasmSingleByteArrayBuffer() {
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void allocate(long byteSize) {
        assert byteSize <= Integer.MAX_VALUE;
        final int effectiveByteSize = (int) byteSize;
        constantBuffer = null;
        dynamicBuffer = null;
        if (constantAttempts < MAX_CONSTANT_ATTEMPTS) {
            constantMemoryBufferAssumption = Assumption.create("ConstantMemoryBuffer");
            constantAttempts++;
        }
        try {
            if (constantMemoryBufferAssumption.isValid()) {
                constantBuffer = new byte[effectiveByteSize];
            } else {
                dynamicBuffer = new byte[effectiveByteSize];
            }
        } catch (OutOfMemoryError error) {
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
    }

    @Override
    public byte[] segment(long address) {
        return buffer();
    }

    private byte[] buffer() {
        if (constantMemoryBufferAssumption.isValid()) {
            return constantBuffer;
        }
        return dynamicBuffer;
    }

    @Override
    public long segmentOffsetAsLong(long address) {
        assert address <= Integer.MAX_VALUE;
        return address;
    }

    @Override
    public int segmentOffsetAsInt(long address) {
        assert address <= Integer.MAX_VALUE;
        return (int) address;
    }

    @Override
    public long size() {
        return buffer().length / MEMORY_PAGE_SIZE;
    }

    @Override
    public long byteSize() {
        return buffer().length;
    }

    @Override
    public void grow(long targetSize) {
        final byte[] currentBuffer = buffer();
        constantMemoryBufferAssumption.invalidate("Memory grow");
        allocate(targetSize);
        System.arraycopy(currentBuffer, 0, buffer(), 0, currentBuffer.length);
    }

    @Override
    public void reset(long byteSize) {
        constantMemoryBufferAssumption.invalidate("Memory reset");
        allocate(byteSize);
    }

    @Override
    public void close() {
        constantBuffer = null;
        dynamicBuffer = null;
    }

    @Override
    public void copyTo(WasmByteArrayBuffer other) {
        final byte[] currentBuffer = buffer();
        final byte[] otherBuffer = other.segment(0L);
        System.arraycopy(currentBuffer, 0, otherBuffer, 0, currentBuffer.length);
    }

    @Override
    public void copyFrom(WasmByteArrayBuffer other, long sourceAddress, long destinationAddress, long length) {
        assert length <= Integer.MAX_VALUE;
        System.arraycopy(other.segment(sourceAddress), other.segmentOffsetAsInt(sourceAddress), segment(destinationAddress), segmentOffsetAsInt(destinationAddress), (int) length);
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public void fill(long address, long length, byte value) {
        Arrays.fill(buffer(), segmentOffsetAsInt(address), segmentOffsetAsInt(address + length), value);
    }
}
