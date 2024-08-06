/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * This class contains mathematical methods that are not already provided by {@link java.lang.Math}
 * that are generally useful for language implementations.
 *
 * @since 24.1
 */
public final class MathUtils {

    /** The result of {@link Math#log Math.log(2)}. */
    private static final double LN_2 = 6.93147180559945286227e-01; // 0x3fe62e42_fefa39ef
    private static final double TWO_POW_M28 = 0x1.0p-28; // 2**-28, 0x3e300000_00000000
    private static final double TWO_POW_P28 = 0x1.0p+28; // 2**28, 0x41b00000_00000000
    /** Comparing >= against this value is equivalent to comparing the high word > 0x41b00000. */
    private static final double TWO_POW_P28_HI = 0x1.00001p+28; // 0x41b00001_00000000
    /** Comparing >= against this value is equivalent to comparing the high word > 0x40000000. */
    private static final double TWO_HI = 0x1.00001p+1; // 0x40000001_00000000

    private MathUtils() {
    }

    /**
     * Computes the inverse (area) hyperbolic sine (asinh) of a {@code double} value.
     *
     * <pre>
     * {@code
     * asinh(x); derived from fdlibm (s_asinh.c)
     * Method :
     *  Based on
     *      asinh(x) = sign(x) * log [ |x| + sqrt(x*x+1) ]
     *  we have
     *  asinh(x) := x  if  1+x*x=1,
     *           := sign(x)*(log(x)+ln2)) for large |x|, else
     *           := sign(x)*log(2|x|+1/(|x|+sqrt(x*x+1))) if|x|>2, else
     *           := sign(x)*log1p(|x| + x^2/(1 + sqrt(1+x^2)))
     * }
     * </pre>
     *
     * @param x The number whose inverse hyperbolic sine is to be returned.
     * @return The inverse hyperbolic sine of {@code x}.
     * @since 24.1
     */
    @TruffleBoundary(allowInlining = true)
    public static double asinh(double x) {
        if (!Double.isFinite(x)) { /* x is inf or NaN */
            return x + x;
        }
        double ax = Math.abs(x);
        if (ax < TWO_POW_M28) { /* |x| < 2**-28 */
            // if (ix < 0x3e30_0000)
            return x; /* (huge + x); return x inexact except 0 */
        }
        double w;
        if (ax >= TWO_POW_P28_HI) { /* |x| > 2**28 */
            // if (ix > 0x41b0_0000)
            w = Math.log(ax) + LN_2;
        } else if (ax >= TWO_HI) { /* 2**28 > |x| > 2.0 */
            // if (ix > 0x4000_0000)
            w = Math.log(2.0 * ax + 1.0 / (Math.sqrt(x * x + 1.0) + ax));
        } else { /* 2.0 >= |x| > 2**-28 */
            double t = x * x;
            w = Math.log1p(ax + t / (1.0 + Math.sqrt(1.0 + t)));
        }
        return Math.copySign(w, x);
    }

    /**
     * Computes the inverse (area) hyperbolic cosine (acosh) of a {@code double} value.
     *
     * <pre>
     * {@code
     * __ieee754_acosh(x); derived from fdlibm (e_acosh.c)
     * Method :
     *  Based on
     *      acosh(x) = log [ x + sqrt(x*x-1) ]
     *  we have
     *      acosh(x) := log(x)+ln2, if x is large; else
     *      acosh(x) := log(2x-1/(sqrt(x*x-1)+x)) if x>2; else
     *      acosh(x) := log1p(t+sqrt(2.0*t+t*t)); where t=x-1.
     *
     * Special cases:
     *      acosh(x) is NaN with signal if x<1.
     *      acosh(NaN) is NaN without signal.
     * }
     * </pre>
     *
     * @param x The number whose inverse hyperbolic cosine is to be returned.
     * @return The inverse hyperbolic cosine of {@code x}.
     * @since 24.1
     */
    @TruffleBoundary(allowInlining = true)
    public static double acosh(double x) {
        if (x < 1.0) { /* x < 1 */
            return (x - x) / (x - x); /* NaN */
        } else if (x >= TWO_POW_P28) { /* x >= 2**28 */
            // if (hx >= 0x41b0_0000)
            if (!Double.isFinite(x)) { /* x is inf or NaN */
                return x + x;
            } else {
                return Math.log(x) + LN_2; /* acosh(huge) = log(2x) */
            }
        } else if (x == 1.0) {
            return 0.0; /* acosh(1) = 0 */
        } else if (x >= TWO_HI) { /* 2**28 > x > 2 */
            // if (hx > 0x4000_0000)
            double t = x * x;
            return Math.log(2.0 * x - 1.0 / (x + Math.sqrt(t - 1.0)));
        } else { /* 1 < x <= 2 */
            double t = x - 1.0;
            return Math.log1p(t + Math.sqrt(2.0 * t + t * t));
        }
    }

    /**
     * Computes the inverse (area) hyperbolic tangent (atanh) of a {@code double} value.
     *
     * <pre>
     * {@code
     * __ieee754_atanh(x); derived from fdlibm (e_atanh.c)
     * Method :
     *    1.Reduced x to positive by atanh(-x) = -atanh(x)
     *    2.For x>=0.5
     *                  1              2x                          x
     *      atanh(x) = --- * log(1 + -------) = 0.5 * log1p(2 * --------)
     *                  2             1 - x                      1 - x
     *
     *      For x<0.5
     *      atanh(x) = 0.5*log1p(2x+2x*x/(1-x))
     *
     * Special cases:
     *      atanh(x) is NaN if |x| > 1 with signal;
     *      atanh(NaN) is that NaN with no signal;
     *      atanh(+-1) is +-INF with signal.
     * }
     * </pre>
     *
     * @param x The number whose inverse hyperbolic tangent is to be returned.
     * @return The inverse hyperbolic tangent of {@code x}.
     * @since 24.1
     */
    @TruffleBoundary(allowInlining = true)
    public static double atanh(double x) {
        double ax = Math.abs(x);
        if (ax > 1.0) { /* |x| > 1 */
            return (x - x) / (x - x); /* NaN */
        }
        if (ax == 1.0) { /* x == +/-1. */
            return x / 0; /* inf */
        }
        if (ax < TWO_POW_M28) { /* x < 2**-28 */
            // if (ix < 0x3e30_0000)
            return x; /* (huge + x); return x */
        }
        double t;
        if (ax < 0.5) { /* |x| < 0.5 */
            // if (ix < 0x3fe0_0000)
            t = ax + ax;
            t = 0.5 * Math.log1p(t + t * ax / (1.0 - ax));
        } else {
            t = 0.5 * Math.log1p((ax + ax) / (1.0 - ax));
        }
        return Math.copySign(t, x);
    }
}
