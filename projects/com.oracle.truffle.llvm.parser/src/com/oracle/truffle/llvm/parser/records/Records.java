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
package com.oracle.truffle.llvm.parser.records;

public final class Records {

    private Records() {
    }

    public static String describe(long id, long[] args) {
        final StringBuilder builder = new StringBuilder();
        builder.append("<id=").append(id).append(" - ");
        for (int i = 0; i < args.length; i++) {
            builder.append("op").append(i).append('=').append(args[i]);
            if (i != args.length - 1) {
                builder.append(", ");
            }
        }
        builder.append('>');
        return builder.toString();
    }

    public static int[] toIntegers(long[] args) {
        int[] values = new int[args.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (int) args[i];
        }
        return values;
    }

    public static long toSignedValue(long value) {
        long v = value;
        if ((v & 1L) == 1L) {
            v = v >>> 1;
            return v == 0 ? Long.MIN_VALUE : -v;
        } else {
            return v >>> 1;
        }
    }

    public static String toString(long[] operands) {
        return toString(operands, 0, operands.length);
    }

    public static String toString(long[] operands, int from) {
        return toString(operands, from, operands.length);
    }

    private static String toString(long[] operands, int from, int to) {
        StringBuilder string = new StringBuilder();

        for (int i = from; i < to; i++) {
            string.append((char) operands[i]);
        }

        return string.toString();
    }
}
