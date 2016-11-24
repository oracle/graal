/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.bc.impl.parser.bc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.context.LLVMLanguage;

public final class Bitstream {

    public static Bitstream create(Source source) {
        byte[] bytes;
        switch (source.getMimeType()) {
            case LLVMLanguage.LLVM_BITCODE_MIME_TYPE:
                bytes = read(source.getPath());
                break;

            case LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE:
                bytes = Base64.getDecoder().decode(source.getCode());
                break;

            default:
                throw new UnsupportedOperationException();
        }
        return new Bitstream(bytes);
    }

    private static byte[] read(String filename) {
        try {
            return Files.readAllBytes(Paths.get(filename));
        } catch (IOException ignore) {
            return new byte[0];
        }
    }

    private static final long BYTE_MASK = 0xffL;

    private final byte[] bitstream;

    private Bitstream(byte[] bitstream) {
        this.bitstream = bitstream;
    }

    public long read(long offset, long bits) {
        return read(offset) & ((1L << bits) - 1L);
    }

    long readVBR(long offset, long width) {
        long value = 0;
        long shift = 0;
        long datum;
        long o = offset;
        long dmask = 1 << (width - 1);
        do {
            datum = read(o, width);
            o += width;
            value += (datum & (dmask - 1)) << shift;
            shift += width - 1;
        } while ((datum & dmask) != 0);
        return value;
    }

    public long size() {
        return bitstream.length * Byte.SIZE;
    }

    static long widthVBR(long value, long width) {
        long total = 0;
        long v = value;
        do {
            total += width;
            v >>>= (width - 1);
        } while (v != 0);
        return total;
    }

    private long read(long offset) {
        long div = offset / Byte.SIZE;
        long value = 0;
        for (int i = 0; i < Byte.SIZE; i++) {
            value += readAlignedByte(div + i) << (i * Byte.SIZE);
        }
        long mod = offset & (Byte.SIZE - 1L);
        if (mod != 0) {
            value >>>= mod;
            value += readAlignedByte(div + Byte.SIZE) << (Long.SIZE - mod);
        }
        return value;
    }

    private long readAlignedByte(long i) {
        return i < bitstream.length ? bitstream[(int) i] & BYTE_MASK : 0;
    }
}
