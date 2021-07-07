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
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

public class Assert {

    public static void assertByteEqual(byte b1, byte b2, Failure failure) throws WasmException {
        if (b1 != b2) {
            fail(failure, format("%s: 0x%02X should = 0x%02X", failure.name, b1, b2));
        }
    }

    public static void assertByteEqual(byte b1, byte b2, String message, Failure failure) throws WasmException {
        if (b1 != b2) {
            fail(failure, format("%s: 0x%02X should = 0x%02X", message, b1, b2));
        }
    }

    public static void assertIntEqual(int actual, int expected, Failure failure) throws WasmException {
        assertIntEqual(actual, expected, failure.name, failure);
    }

    public static void assertIntEqual(int actual, int expected, String message, Failure failure) throws WasmException {
        if (actual != expected) {
            fail(failure, format("%s: %d should = %d", message, actual, expected));
        }
    }

    public static void assertIntGreaterOrEqual(int n1, int n2, Failure failure) throws WasmException {
        if (n1 < n2) {
            fail(failure, format("%s: %d should be > %d", failure.name, n1, n2));
        }
    }

    public static void assertIntGreater(int n1, int n2, String message, Failure failure) throws WasmException {
        if (n1 <= n2) {
            fail(failure, format("%s: %d should be > %d", message, n1, n2));
        }
    }

    public static void assertIntLessOrEqual(int n1, int n2, Failure failure) throws WasmException {
        assertIntLessOrEqual(n1, n2, failure.name, failure);
    }

    public static void assertIntLess(int n1, int n2, Failure failure) throws WasmException {
        if (n1 >= n2) {
            fail(failure, format("%s: %d should be <= %d", failure.name, n1, n2));
        }
    }

    public static void assertUnsignedIntLess(int n1, int n2, Failure failure) throws WasmException {
        assertUnsignedIntLess(n1, n2, failure, failure.name);
    }

    public static void assertUnsignedIntLess(int n1, int n2, Failure failure, String message) throws WasmException {
        if (Integer.compareUnsigned(n1, n2) >= 0) {
            fail(failure, format("%s: %s should be < %s", message, Integer.toUnsignedString(n1), Integer.toUnsignedString(n2)));
        }
    }

    public static void assertIntLessOrEqual(int n1, int n2, String message, Failure failure) throws WasmException {
        if (n1 > n2) {
            fail(failure, format("%s: %d should be <= %d", message, n1, n2));
        }
    }

    public static void assertUnsignedIntLessOrEqual(int n1, int n2, Failure failure) throws WasmException {
        assertUnsignedIntLessOrEqual(n1, n2, failure, failure.name);
    }

    public static void assertUnsignedIntLessOrEqual(int n1, int n2, Failure failure, String message) throws WasmException {
        if (Integer.compareUnsigned(n1, n2) > 0) {
            fail(failure, format("%s: %s should be <= %s", message, Integer.toUnsignedString(n1), Integer.toUnsignedString(n2)));
        }
    }

    public static void assertUnsignedIntGreaterOrEqual(int n1, int n2, Failure failure) throws WasmException {
        assertUnsignedIntGreaterOrEqual(n1, n2, failure, failure.name);
    }

    public static void assertUnsignedIntGreaterOrEqual(int n1, int n2, Failure failure, String message) throws WasmException {
        if (Integer.compareUnsigned(n1, n2) < 0) {
            fail(failure, format("%s: %s should be >= %s", message, Integer.toUnsignedString(n1), Integer.toUnsignedString(n2)));
        }
    }

    public static void assertLongLessOrEqual(long n1, long n2, Failure failure) throws WasmException {
        if (n1 > n2) {
            fail(failure, format("%s: %d should be <= %d", failure.name, n1, n2));
        }
    }

    public static void assertNotNull(Object object, String message, Failure failure) throws WasmException {
        if (object == null) {
            fail(failure, format("%s: expected a non-null value", message));
        }
    }

    public static void assertTrue(boolean condition, Failure failure) throws WasmException {
        assertTrue(condition, failure.name, failure);
    }

    public static void assertTrue(boolean condition, String message, Failure failure) throws WasmException {
        if (!condition) {
            fail(failure, message);
        }
    }

    @TruffleBoundary
    public static RuntimeException fail(Failure failure, String message, Object... args) throws WasmException {
        throw WasmException.format(failure, message, args);
    }

    @TruffleBoundary
    private static String format(String format, Object... args) {
        return String.format(format, args);
    }

}
