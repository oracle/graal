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
/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 University of Manchester
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.man.cs.llvm.bc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Bitstream {

    public static Bitstream create(String filename) {
        return new Bitstream(read(filename));
    }

    protected static byte[] read(String filename) {
        try {
            return Files.readAllBytes(Paths.get(filename));
        } catch (@SuppressWarnings("unused") IOException ignore) {
            return new byte[0];
        }
    }

    private static final long BYTE_MASK = 0xffL;

    private final byte[] bitstream;

    protected Bitstream(byte[] bitstream) {
        this.bitstream = bitstream;
    }

    public long read(long offset, long bits) {
        return read(offset) & ((1L << bits) - 1L);
    }

    public long readVBR(long offset, long width) {
        long value = 0;
        long shift = 0;
        long datum;
        long dmask = 1 << (width - 1);
        do {
            datum = read(offset, width);
            offset += width;
            value += (datum & (dmask - 1)) << shift;
            shift += width - 1;
        } while ((datum & dmask) != 0);
        return value;
    }

    public long size() {
        return bitstream.length * Byte.SIZE;
    }

    public long widthVBR(long value, long width) {
        long total = 0;
        do {
            total += width;
            value >>>= (width - 1);
        } while (value != 0);
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
