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
package uk.ac.man.cs.llvm.ir.types;

public final class IntegerType implements Type {

    public static final IntegerType BOOLEAN = new IntegerType(1);

    public static final IntegerType BYTE = new IntegerType(8);

    public static final IntegerType SHORT = new IntegerType(16);

    public static final IntegerType INTEGER = new IntegerType(32);

    public static final IntegerType LONG = new IntegerType(64);

    private final int bits;

    public IntegerType(int bits) {
        super();
        this.bits = bits;
    }

    @Override
    public int getAlignment() {
        if (bits <= Byte.SIZE) {
            return Byte.BYTES;
        } else if (bits <= Short.SIZE) {
            return Short.BYTES;
        } else if (bits <= Integer.SIZE) {
            return Integer.BYTES;
        }
        return Long.BYTES;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IntegerType) {
            return bits == ((IntegerType) obj).bits;
        }
        return false;
    }

    public int getBitCount() {
        return bits;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + this.bits;
        return hash;
    }

    @Override
    public int sizeof() {
        int sizeof = bits / Byte.SIZE;
        return (bits % Byte.SIZE) == 0 ? sizeof : sizeof + 1;
    }

    @Override
    public String toString() {
        return String.format("i%d", bits);
    }
}
