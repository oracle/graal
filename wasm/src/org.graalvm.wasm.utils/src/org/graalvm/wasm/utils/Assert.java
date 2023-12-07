/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.utils;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class Assert {
    public static void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail(message);
        }
    }

    public static void assertNotNull(String message, Object object) {
        assertTrue(message, object != null);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        if (!actual.equals(expected)) {
            fail(format("%s '%s' != '%s'", message, expected, actual));
        }
    }

    public static void assertFloatEquals(String message, Float expected, Float actual, Float epsilon) {
        if (Math.abs(actual - expected) > epsilon) {
            fail(format("%s %s \u2209 %s +/- %s", message, actual, expected, epsilon));
        }
    }

    public static void assertDoubleEquals(String message, Double expected, Double actual, Double epsilon) {
        if (Math.abs(actual - expected) > epsilon) {
            fail(format("%s %s \u2209 %s +/- %s", message, actual, expected, epsilon));
        }
    }

    public static void assertArrayEquals(String message, byte[] expected, byte[] actual) {
        if (expected == actual) {
            return;
        }
        if (expected.length != actual.length) {
            fail(format("%s '%s.length' != '%s.length'", message, expected, actual));
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                fail(format("%s '%s[%d] { %d }' != '%s[%d] { %d }'", message, expected, i, expected[i], actual, i, actual[i]));
            }
        }
    }

    public static void fail(String message) {
        throw new RuntimeException(message);
    }

    @TruffleBoundary
    public static String format(String format, Object... args) {
        return String.format(format, args);
    }
}
