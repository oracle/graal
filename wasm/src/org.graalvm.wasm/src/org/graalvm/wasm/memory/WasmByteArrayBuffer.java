package org.graalvm.wasm.memory;

interface WasmByteArrayBuffer {

    void allocate(long byteSize);

    byte[] segment(long address);

    long segmentOffsetAsLong(long address);

    int segmentOffsetAsInt(long address);

    long size();

    long byteSize();

    void grow(long targetSize);

    void reset(long byteSize);

    void close();

    void copyTo(WasmByteArrayBuffer other);

    void copyFrom(WasmByteArrayBuffer other, long sourceAddress, long destinationAddress, long length);

    void fill(long address, long length, byte value);
}
