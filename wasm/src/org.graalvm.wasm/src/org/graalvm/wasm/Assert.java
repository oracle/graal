/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.graalvm.wasm.exception.BinaryParserException;

public class Assert {

    public static void assertByteEqual(byte b1, byte b2, String message) throws BinaryParserException {
        if (b1 != b2) {
            fail(format("%s: 0x%02X should = 0x%02X.", message, b1, b2));
        }
    }

    public static void assertIntEqual(int n1, int n2, String message) throws BinaryParserException {
        if (n1 != n2) {
            fail(format("%s: %d should = %d.", message, n1, n2));
        }
    }

    public static void assertLongEqual(long n1, long n2, String message) throws BinaryParserException {
        if (n1 != n2) {
            fail(format("%s: %d should = %d.", message, n1, n2));
        }
    }

    public static void assertIntIn(int value, int start, int end, String message) {
        if (value < start || value > end) {
            fail(format("%s: value %d should be in range [%d, %d].", message, value, start, end));
        }
    }

    public static void assertLongIn(long value, long start, long end, String message) {
        if (value < start || value > end) {
            fail(format("%s: value %d should be in range [%d, %d].", message, value, start, end));
        }
    }

    public static void assertIntGreater(int n1, int n2, String message) throws BinaryParserException {
        if (n1 <= n2) {
            fail(format("%s: %d should be > %d.", message, n1, n2));
        }
    }

    public static void assertLongGreater(long n1, long n2, String message) throws BinaryParserException {
        if (n1 <= n2) {
            fail(format("%s: %d should be > %d.", message, n1, n2));
        }
    }

    public static void assertIntLessOrEqual(int n1, int n2, String message) throws BinaryParserException {
        if (n1 > n2) {
            fail(format("%s: %d should be <= %d.", message, n1, n2));
        }
    }

    public static void assertLongLessOrEqual(long n1, long n2, String message) throws BinaryParserException {
        if (n1 > n2) {
            fail(format("%s: %d should be <= %d.", message, n1, n2));
        }
    }

    public static void assertNotNull(Object object, String message) throws BinaryParserException {
        if (object == null) {
            fail(format("%s: expected a non-null value.", message));
        }
    }

    public static void assertTrue(boolean condition, String message) throws BinaryParserException {
        if (!condition) {
            fail(format("%s: condition is supposed to be true.", message));
        }
    }

    public static RuntimeException fail(String message) throws BinaryParserException {
        throw new BinaryParserException(message);
    }

    @TruffleBoundary
    public static String format(String format, Object... args) {
        return String.format(format, args);
    }

}
