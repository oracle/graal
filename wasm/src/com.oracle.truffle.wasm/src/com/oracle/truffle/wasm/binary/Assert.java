/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.wasm.binary.exception.BinaryReaderException;

public class Assert {

    public static void assertEquals(int n1, int n2, String message) throws BinaryReaderException {
        if (n1 != n2) {
            fail(format("%s: should be equal: %d != %d", message, n1, n2));
        }
    }

    public static void assertEquals(long n1, long n2, String message) throws BinaryReaderException {
        if (n1 != n2) {
            fail(format("%s: should be equal: %d != %d", message, n1, n2));
        }
    }

    public static void assertInRange(int value, int start, int end, String message) {
        if (value < start || value > end) {
            fail(format("%s: value %d should be in range [%d, %d]", message, value, start, end));
        }
    }

    public static void assertInRange(long value, long start, long end, String message) {
        if (value < start || value > end) {
            fail(format("%s: value %d should be in range [%d, %d]", message, value, start, end));
        }
    }

    public static void assertLarger(int n1, int n2, String message) throws BinaryReaderException {
        if (n1 <= n2) {
            fail(format("%s: should be larger: %d <= %d", message, n1, n2));
        }
    }

    public static void assertLess(int n1, int n2, String message) throws BinaryReaderException {
        if (n1 > n2) {
            fail(format("%s: should be less: %d > %d", message, n1, n2));
        }
    }

    public static void assertNotNull(Object object, String message) throws BinaryReaderException {
        if (object == null) {
            fail(format("%s: expected a non-null value", message));
        }
    }

    public static void fail(String message) throws BinaryReaderException {
        throw new BinaryReaderException(message);
    }

    @TruffleBoundary
    public static String format(String format, Object... args) {
        return String.format(format, args);
    }

}
