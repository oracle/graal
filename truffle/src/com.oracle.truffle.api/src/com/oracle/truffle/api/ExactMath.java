/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * This class contains methods that will be part of java.lang.Math starting with JDK 8. Until JDK 8
 * is release, we duplicate them here because they are generally useful for dynamic language
 * implementations.
 *
 * @since 0.8 or earlier
 */
public final class ExactMath {

    private ExactMath() {
    }

    /** @since 0.8 or earlier */
    public static int multiplyHigh(int x, int y) {
        long r = (long) x * (long) y;
        return (int) (r >> 32);
    }

    /** @since 0.8 or earlier */
    public static int multiplyHighUnsigned(int x, int y) {
        long xl = x & 0xFFFFFFFFL;
        long yl = y & 0xFFFFFFFFL;
        long r = xl * yl;
        return (int) (r >> 32);
    }

    /** @since 0.8 or earlier */
    public static long multiplyHigh(long x, long y) {
        // Checkstyle: stop
        long x0, y0, z0;
        long x1, y1, z1, z2, t;
        // Checkstyle: resume

        x0 = x & 0xFFFFFFFFL;
        x1 = x >> 32;

        y0 = y & 0xFFFFFFFFL;
        y1 = y >> 32;

        z0 = x0 * y0;
        t = x1 * y0 + (z0 >>> 32);
        z1 = t & 0xFFFFFFFFL;
        z2 = t >> 32;
        z1 += x0 * y1;

        return x1 * y1 + z2 + (z1 >> 32);
    }

    /** @since 0.8 or earlier */
    public static long multiplyHighUnsigned(long x, long y) {
        // Checkstyle: stop
        long x0, y0, z0;
        long x1, y1, z1, z2, t;
        // Checkstyle: resume

        x0 = x & 0xFFFFFFFFL;
        x1 = x >>> 32;

        y0 = y & 0xFFFFFFFFL;
        y1 = y >>> 32;

        z0 = x0 * y0;
        t = x1 * y0 + (z0 >>> 32);
        z1 = t & 0xFFFFFFFFL;
        z2 = t >>> 32;
        z1 += x0 * y1;

        return x1 * y1 + z2 + (z1 >>> 32);
    }
}
