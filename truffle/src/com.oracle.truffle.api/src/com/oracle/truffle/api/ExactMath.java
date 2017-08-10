/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api;

/**
 * This class contains methods that will be part of java.lang.Math starting with JDK 8. Until JDK 8
 * is release, we duplicate them here because they are generally useful for dynamic language
 * implementations.
 *
 * @since 0.8 or earlier
 */
public class ExactMath {
    /**
     * @deprecated accidentally public - don't use
     * @since 0.8 or earlier
     */
    @Deprecated
    public ExactMath() {
    }

    /**
     * @deprecated use {@link Math#addExact(int, int)}
     * @since 0.8 or earlier
     */
    @Deprecated
    public static int addExact(int x, int y) {
        return Math.addExact(x, y);
    }

    /**
     * @deprecated use {@link Math#addExact(long, long)}
     * @since 0.8 or earlier
     */
    @Deprecated
    public static long addExact(long x, long y) {
        return Math.addExact(x, y);
    }

    /**
     * @deprecated use {@link Math#subtractExact(int, int)}
     * @since 0.8 or earlier
     */
    @Deprecated
    public static int subtractExact(int x, int y) {
        return Math.subtractExact(x, y);
    }

    /**
     * @deprecated use {@link Math#subtractExact(long, long)}
     * @since 0.8 or earlier
     */
    @Deprecated
    public static long subtractExact(long x, long y) {
        return Math.subtractExact(x, y);
    }

    /**
     * @deprecated use {@link Math#multiplyExact(int, int)}
     * @since 0.8 or earlier
     */
    @Deprecated
    public static int multiplyExact(int x, int y) {
        return Math.multiplyExact(x, y);
    }

    /**
     * @deprecated use {@link Math#multiplyExact(long, long)}
     * @since 0.8 or earlier
     */
    @Deprecated
    public static long multiplyExact(long x, long y) {
        return Math.multiplyExact(x, y);
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
