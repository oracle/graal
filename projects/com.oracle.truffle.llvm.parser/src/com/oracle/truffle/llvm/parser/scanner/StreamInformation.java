/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.scanner;

final class StreamInformation {

    private static final long WRAPPER_MAGIC_WORD = 0x0B17C0DEL;

    private final long offset;
    private final long size;

    private StreamInformation(long offset, long size) {
        this.offset = offset;
        this.size = size;
    }

    static StreamInformation getStreamInformation(BitStream stream, LLVMScanner scanner) {
        long first32bit = scanner.read(Integer.SIZE);
        if (first32bit == WRAPPER_MAGIC_WORD) {
            // offset and size of bitcode stream are specified in bitcode wrapper
            return parseWrapperFormatPrefix(scanner);
        } else {
            return new StreamInformation(0, stream.size());
        }
    }

    /*
     * Bitcode files can have a wrapper prefix: [Magic32, Version32, Offset32, Size32, CPUType32]
     * see: http://llvm.org/docs/BitCodeFormat.html#bitcode-wrapper-format
     */
    private static StreamInformation parseWrapperFormatPrefix(LLVMScanner scanner) {
        // Version32
        scanner.read(Integer.SIZE);
        // Offset32
        long offset = scanner.read(Integer.SIZE) * Byte.SIZE;
        // Size32
        long size = scanner.read(Integer.SIZE) * Byte.SIZE;
        // CPUType32
        scanner.read(Integer.SIZE);
        // End of Wrapper Prefix
        return new StreamInformation(offset, size);
    }

    long getOffset() {
        return offset;
    }

    long totalStreamSize() {
        return offset + size;
    }
}
