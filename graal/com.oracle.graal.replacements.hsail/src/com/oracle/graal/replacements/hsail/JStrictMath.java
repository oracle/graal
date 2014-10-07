/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.graal.replacements.hsail;

import java.util.*;

import sun.misc.*;

import com.oracle.graal.api.replacements.*;

/**
 * This class contains methods for performing basic numeric operations such as the elementary
 * exponential, logarithm, square root, and trigonometric functions. It is a Java port of the native
 * methods in {@link StrictMath} and can thus be used by Graal backends to provide
 * {@link MethodSubstitution}s for the native methods.
 *
 * <p>
 * To help ensure portability of Java programs, the definitions of some of the numeric functions in
 * this package require that they produce the same results as certain published algorithms. These
 * algorithms are available from the well-known network library {@code netlib} as the package
 * "Freely Distributable Math Library," <a href="ftp://ftp.netlib.org/fdlibm.tar">{@code fdlibm}
 * </a>. These algorithms, which were originally written in the C programming language, are then to
 * be understood as executed with all floating-point operations following the rules of Java
 * floating-point arithmetic.
 *
 * <p>
 * The Java math library is defined with respect to {@code fdlibm} version 5.3. Where {@code fdlibm}
 * provides more than one definition for a function (such as {@code acos}), use the
 * "IEEE 754 core function" version (residing in a file whose name begins with the letter {@code e}
 * ). The methods which require {@code fdlibm} semantics are {@link #sin}, {@link #cos},
 * {@link #tan}, {@link #asin}, {@link #acos}, {@link #atan}, {@link #exp}, {@link #log},
 * {@link #log10}, {@link #cbrt}, {@link #atan2}, {@link #pow}, {@link #sinh}, {@link #cosh},
 * {@link #tanh}, {@link #hypot}, {@link #expm1}, and {@link #log1p}.
 *
 * @author Gustav Trede (port to Java)
 */
// JaCoCo Exclude
public final class JStrictMath {

    /**
     * Don't let anyone instantiate this class.
     */
    private JStrictMath() {
    }

    /**
     * The {@code double} value that is closer than any other to <i>e</i>, the base of the natural
     * logarithms.
     */
    public static final double E = 2.7182818284590452354;
    /**
     * The {@code double} value that is closer than any other to <i>pi</i>, the ratio of the
     * circumference of a circle to its diameter.
     */
    public static final double PI = 3.14159265358979323846;

    /**
     * Returns the trigonometric sine of an angle. Special cases:
     * <ul>
     * <li>If the argument is NaN or an infinity, then the result is NaN.
     * <li>If the argument is zero, then the result is a zero with the same sign as the argument.
     * </ul>
     *
     * @param a an angle, in radians.
     * @return the sine of the argument.
     */
    @SuppressWarnings("javadoc")
    public static double sin(double x) {
        int hx = (int) (Double.doubleToRawLongBits(x) >> 32);
        int ix = hx & 0x7fffffff;
        if (ix >= 0x7ff00000) {
            // x is inf or NaN
            return Double.NaN;
        }
        return ix <= 0x3fe921fb ? __kernel_sin_(x, ix) : __ieee754_rem_pio2_(x, hx, sintype);
    }

    /**
     * Returns the trigonometric cosine of an angle. Special cases:
     * <ul>
     * <li>If the argument is NaN or an infinity, then the result is NaN.
     * </ul>
     *
     * @param x an angle, in radians.
     * @return the cosine of the argument.
     */
    public static double cos(double x) {
        int hx = (int) (Double.doubleToRawLongBits(x) >> 32);
        int ix = hx & 0x7fffffff;
        if (ix >= 0x7ff00000) {
            // x is inf or NaN
            return Double.NaN;
        }
        return ix <= 0x3fe921fb ? __kernel_cos_(x, 0, ix) : __ieee754_rem_pio2_(x, hx, costype);
    }

    /**
     * Returns the trigonometric tangent of an angle. Special cases:
     * <ul>
     * <li>If the argument is NaN or an infinity, then the result is NaN.
     * <li>If the argument is zero, then the result is a zero with the same sign as the argument.
     * </ul>
     *
     * @param x an angle, in radians.
     * @return the tangent of the argument.
     */
    public static double tan(double x) {
        final int hx = (int) (Double.doubleToRawLongBits(x) >> 32);
        int ix = hx & 0x7fffffff;
        if (ix >= 0x7ff00000) {
            // x is inf or NaN
            return Double.NaN;
        }
        return ix <= 0x3fe921fb ? __kernel_tan(x, 0d, 1, hx) : __ieee754_rem_pio2_(x, hx, tantype);
    }

    /**
     * kernel tan function on [-pi/4, pi/4], pi/4 ~ 0.7854
     *
     * Algorithm 1. Since tan(-x) = -tan(x), we need only to consider positive x. 2. if x < 2^-28
     * (hx<0x3e300000 0), return x with inexact if x!=0. 3. tan(x) is approximated by a odd
     * polynomial of degree 27 on [0,0.67434] 3 27 tan(x) ~ x + T1*x + ... + T13*x where
     *
     * |tan(x) 2 4 26 | -59.2 |----- - (1+T1*x +T2*x +.... +T13*x )| <= 2 | x |
     *
     * Note: tan(x+y) = tan(x) + tan'(x)*y ~ tan(x) + (1+x*x)*y Therefore, for better accuracy in
     * computing tan(x+y), let 3 2 2 2 2 r = x *(T2+x *(T3+x *(...+x *(T12+x *T13)))) then 3 2
     * tan(x+y) = x + (T1*x + (x *(r+y)+y))
     *
     * 4. For x in [0.67434,pi/4], let y = pi/4 - x, then tan(x) = tan(pi/4-y) =
     * (1-tan(y))/(1+tan(y)) = 1 - 2*(tan(y) - (tan(y)^2)/(1+tan(y)))
     *
     * @param xx is assumed to be bounded by ~pi/4 in magnitude.
     * @param yy is the tail of x
     * @param k indicates whether tan (if k=1) or -1/tan (if k= -1) is returned.
     * @param hx high bits of x
     * @return
     */
    @SuppressWarnings("javadoc")
    private static double __kernel_tan(double xx, double yy, int k, int hx) {
        double w, r;
        double x = xx;
        double y = yy;
        final int ix = hx & 0x7fffffff;
        if (ix < 0x3e300000 && (int) x == 0) { // |x| < 2**-28
            if (k == 1) {
                return x;
            }
            if ((ix | hx) == 0) {
                return one / abs(x); // generate inexact
            }
            // compute -1 / (x+y) carefully
            r = y;
            w = x + y;
        } else {
            if (ix >= 0x3FE59428) { // |x|>=0.67434
                if (hx < 0) {
                    x = 0.0d - x;
                    y = 0.0d - y;
                }
                x = (pio4 - x) + (pio4lo - y);
                y = 0.0;
            }
            final double z = x * x;
            w = z * z;

            /*
             * Break x^5*(T[1]+x^2*T[2]+...) into x^5(T[1]+x^4*T[3]+...+x^20*T[11]) +
             * x^5(x^2*(T[2]+x^4*T[4]+...+x^22*[T12]))
             */

            final double s = z * x;
            double v = z * (T2 + w * (T4 + w * (T6 + w * (T8 + w * (T10 + w * T12)))));
            r = (y + z * (s * ((T1 + w * (T3 + w * (T5 + w * (T7 + w * (T9 + w * T11))))) + v) + y)) + T0 * s;
            w = x + r;
            if (ix >= 0x3FE59428) {
                v = k;
                return (1 - ((hx >> 30) & 2)) * (v - 2.0d * (x - (((w * w) / (w + v)) - r)));
            }
            if (k == 1) {
                return w;
            }
            // if allow error up to 2 ulp, simply return -1.0/(x+r) here
        }
        // compute -1.0/(x+r) accurately
        final double a = negone / w;
        final double t = clearLow32bits(a);
        final double z = clearLow32bits(w);
        return t + a * ((1.0d + t * z) + t * (r - (z - x)));
    }

    private static final double S1 = -1.66666666666666324348e-01, S2 = 8.33333333332248946124e-03, /*
                                                                                                    * 0x3F811111
                                                                                                    * ,
                                                                                                    * 0x1110F8A6
                                                                                                    */
    S3 = -1.98412698298579493134e-04, /* 0xBF2A01A0, 0x19C161D5 */
    S4 = 2.75573137070700676789e-06, /* 0x3EC71DE3, 0x57B1FE7D */
    S5 = -2.50507602534068634195e-08, /* 0xBE5AE5E6, 0x8A2B9CEB */
    S6 = 1.58969099521155010221e-10; /* 0x3DE5D93A, 0x5ACFD57C */

    /**
     * kernel sin function on [-pi/4, pi/4], pi/4 ~ 0.7854 Input x is assumed to be bounded by ~pi/4
     * in magnitude. Input y is the tail of x. Input iy indicates whether y is 0. (if iy=0, y assume
     * to be 0).
     *
     * Algorithm 1. Since sin(-x) = -sin(x), we need only to consider positive x. 2. if x < 2^-27
     * (hx<0x3e400000 0), return x with inexact if x!=0. 3. sin(x) is approximated by a polynomial
     * of degree 13 on [0,pi/4] 3 13 sin(x) ~ x + S1*x + ... + S6*x where
     *
     * |sin(x) 2 4 6 8 10 12 | -58 |----- - (1+S1*x +S2*x +S3*x +S4*x +S5*x +S6*x )| <= 2 | x |
     *
     * 4. sin(x+y) = sin(x) + sin'(x')*y ~ sin(x) + (1-x*x/2)*y For better accuracy, let 3 2 2 2 2 r
     * = x *(S2+x *(S3+x *(S4+x *(S5+x *S6)))) then 3 2 sin(x) = x + (S1*x + (x *(r-y/2)+y))
     *
     * @param x
     * @param y
     * @return
     */
    @SuppressWarnings("javadoc")
    private static double __kernel_sin(double x, double y, int ix) {
        if (ix < 0x3e400000 && (int) x == 0) {
            return x; // |x| < 2**-27 generate inexact
        }
        final double z = x * x;
        final double v = z * x;
        final double r = v * (S2 + z * (S3 + z * (S4 + z * (S5 + z * S6))));
        return x - ((z * (half * y - r) - y) - v * S1);
    }

    private static double __kernel_sin_(double x, int ix) {
        if (ix < 0x3e400000 && (int) x == 0) {
            return x; // |x| < 2**-27 generate inexact
        }
        final double z = x * x;
        return x + (z * x) * (S1 + z * (S2 + z * (S3 + z * (S4 + z * (S5 + z * S6)))));
    }

    private static final double two = 2.0d, one = Double.longBitsToDouble(0x3ff0000000000000L), negone = -one, C1 = 4.16666666666666019037e-02, C2 = -1.38888888888741095749e-03,
                    C3 = 2.48015872894767294178e-05, C4 = -2.75573143513906633035e-07, C5 = 2.08757232129817482790e-09, C6 = -1.13596475577881948265e-11;

    /**
     * kernel cos function on [-pi/4, pi/4], pi/4 ~ 0.785398164 Input x is assumed to be bounded by
     * ~pi/4 in magnitude. Input y is the tail of x.
     *
     * Algorithm 1. Since cos(-x) = cos(x), we need only to consider positive x. 2. if x < 2^-27
     * (hx<0x3e400000 0), return 1 with inexact if x!=0. 3. cos(x) is approximated by a polynomial
     * of degree 14 on [0,pi/4] 4 14 cos(x) ~ 1 - x*x/2 + C1*x + ... + C6*x where the remez error is
     *
     * | 2 4 6 8 10 12 14 | -58 |cos(x)-(1-.5*x +C1*x +C2*x +C3*x +C4*x +C5*x +C6*x )| <= 2 | |
     *
     * 4 6 8 10 12 14 4. let r = C1*x +C2*x +C3*x +C4*x +C5*x +C6*x , then cos(x) = 1 - x*x/2 + r
     * since cos(x+y) ~ cos(x) - sin(x)*y ~ cos(x) - x*y, a correction term is necessary in cos(x)
     * and hence cos(x+y) = 1 - (x*x/2 - (r - x*y)) For better accuracy when x > 0.3, let qx = |x|/4
     * with the last 32 bits mask off, and if x > 0.78125, let qx = 0.28125. Then cos(x+y) = (1-qx)
     * - ((x*x/2-qx) - (r-x*y)). Note that 1-qx and (x*x/2-qx) is EXACT here, and the magnitude of
     * the latter is at least a quarter of x*x/2, thus, reducing the rounding error in the
     * subtraction.
     *
     * @param x
     * @param y
     * @param ix
     * @return
     */
    @SuppressWarnings("javadoc")
    private static double __kernel_cos_(double x, double y, int ix) {
        if (ix < 0x3e400000 && ((int) x) == 0) {
            return one; // x < 2**27 generate inexact
        }
        final double z = x * x;
        double r = z * (z * (C1 + z * (C2 + z * (C3 + z * (C4 + z * (C5 + z * C6)))))) - x * y;
        if (ix < 0x3FD33333) { // |x| < 0.3
            return one - (0.5 * z - r);
        }
        final double qx = ix > 0x3fe90000 ? 0.28125 : // x > 0.78125
                        Double.longBitsToDouble((long) (ix - 0x00200000) << 32); // x/4
        return (one - qx) - ((0.5 * z - qx) - r);
    }

    private static final int sintype = 0;
    private static final int costype = 1;
    private static final int tantype = 3;

    /**
     * Returns final values for tan , cos and sin.
     *
     * @param trigtype
     * @param NN
     * @param y0
     * @param y1
     * @author gustav trede
     * @return
     */
    @SuppressWarnings("javadoc")
    private static double getTrigres(int trigtype, int NN, double y0, double y1) {
        int N = NN;
        int hx = ((int) (Double.doubleToRawLongBits(y0) >> 32));
        if (trigtype == tantype) {
            return __kernel_tan(y0, y1, 1 - ((N & 1) << 1), hx);
        }
        hx &= 0x7fffffff;
        N = (N & 3) - trigtype;
        double v = (N == 0 || N == 2) ? __kernel_sin(y0, y1, hx) : __kernel_cos_(y0, y1, hx);

        if (N == 0 || N == 1) {
            return trigtype == 0 ? v : 0.0d - v;
        }
        return trigtype == 0 ? 0.0d - v : v;
    }

    private static final double pio4 = 7.85398163397448278999e-01, pio4lo = 3.06161699786838301793e-17, T0 = 3.33333333333334091986e-01, T1 = 1.33333333333201242699e-01,
                    T2 = 5.39682539762260521377e-02, T3 = 2.18694882948595424599e-02, T4 = 8.86323982359930005737e-03, T5 = 3.59207910759131235356e-03, T6 = 1.45620945432529025516e-03,
                    T7 = 5.88041240820264096874e-04, T8 = 2.46463134818469906812e-04, T9 = 7.81794442939557092300e-05, T10 = 7.14072491382608190305e-05, T11 = -1.85586374855275456654e-05,
                    T12 = 2.59073051863633712884e-05;

    /*
     * Table of constants for 2/pi, 396 Hex digits (476 decimal) of 2/pi
     */
    private static final int[] two_over_pi = {0xa2f983, 0x6e4e44, 0x1529fc, 0x2757d1, 0xf534dd, 0xc0db62, 0x95993c, 0x439041, 0xfe5163, 0xabdebb, 0xc561b7, 0x246e3a, 0x424dd2, 0xe00649, 0x2eea09,
                    0xd1921c, 0xfe1deb, 0x1cb129, 0xa73ee8, 0x8235f5, 0x2ebb44, 0x84e99c, 0x7026b4, 0x5f7e41, 0x3991d6, 0x398353, 0x39f49c, 0x845f8b, 0xbdf928, 0x3b1ff8, 0x97ffde, 0x05980f, 0xef2f11,
                    0x8b5a0a, 0x6d1f6d, 0x367ecf, 0x27cb09, 0xb74f46, 0x3f669e, 0x5fea2d, 0x7527ba, 0xc7ebe5, 0xf17b3d, 0x0739f7, 0x8a5292, 0xea6bfb, 0x5fb11f, 0x8d5d08, 0x560330, 0x46fc7b, 0x6babf0,
                    0xcfbc20, 0x9af436, 0x1da9e3, 0x91615e, 0xe61b08, 0x659985, 0x5f14a0, 0x68408d, 0xffd880, 0x4d7327, 0x310606, 0x1556ca, 0x73a8c9, 0x60e27b, 0xc08c6b};

    private static final int[] npio2_hw = {0x3ff921fb, 0x400921fb, 0x4012d97c, 0x401921fb, 0x401f6a7a, 0x4022d97c, 0x4025fdbb, 0x402921fb, 0x402c463a, 0x402f6a7a, 0x4031475c, 0x4032d97c, 0x40346b9c,
                    0x4035fdbb, 0x40378fdb, 0x403921fb, 0x403ab41b, 0x403c463a, 0x403dd85a, 0x403f6a7a, 0x40407e4c, 0x4041475c, 0x4042106c, 0x4042d97c, 0x4043a28c, 0x40446b9c, 0x404534ac, 0x4045fdbb,
                    0x4046c6cb, 0x40478fdb, 0x404858eb, 0x404921fb};

    private static final double zero = 0.00000000000000000000e+00, half = 5.00000000000000000000e-01,
    /* 1.67772160000000000000e+07 */
    two24 = Double.longBitsToDouble(0x4170000000000000L),
    /* 6.36619772367581382433e-01 53 bits of 2/pi */
    invpio2 = Double.longBitsToDouble(0x3fe45f306dc9c883L),
    /* 1.57079632673412561417e+00 first 33 bit of pi/2 */
    pio2_1 = Double.longBitsToDouble(0x3ff921fb54400000L),
    /* 6.07710050650619224932e-11 pi/2 - pio2_1 */
    pio2_1t = Double.longBitsToDouble(0x3dd0b4611a626331L),
    /* 6.07710050630396597660e-11 second 33 bit of pi/2 */
    pio2_2 = Double.longBitsToDouble(0x3dd0b4611a600000L),
    /* 2.02226624879595063154e-21 pi/2 - (pio2_1+pio2_2) */
    pio2_2t = Double.longBitsToDouble(0x3ba3198a2e037073L),
    /* 2.02226624871116645580e-21 third 33 bit of pi/2 */
    pio2_3 = Double.longBitsToDouble(0x3ba3198a2e000000L),
    /* 8.47842766036889956997e-32 pi/2 - (pio2_1+pio2_2+pio2_3) */
    pio2_3t = Double.longBitsToDouble(0x397b839a252049c1L);

    private static double __ieee754_rem_pio2_(double xx, int hx, int trigtype) {
        double x = xx;
        int ix = hx & 0x7fffffff;
        if (ix <= 0x413921fb) { // |x| ~<= 2^19*(pi/2)
            double y0, y1;
            int n;
            if (ix < 0x4002d97c) { // |x| < 3pi/4, special case with n=+-1
                if (hx > 0) {
                    x -= pio2_1;
                    if (ix != 0x3ff921fb) { // 33+53 bit pi is good enough
                        y0 = x - pio2_1t;
                        y1 = (x - y0) - pio2_1t;
                    } else { // near pi/2, use 33+33+53 bit pi
                        x -= pio2_2;
                        y0 = x - pio2_2t;
                        y1 = (x - y0) - pio2_2t;
                    }
                    n = 1;
                } else { // negative x
                    x += pio2_1;
                    if (ix != 0x3ff921fb) { // 33+53 bit pi is good enough
                        y0 = x + pio2_1t;
                        y1 = (x - y0) + pio2_1t;
                    } else { // near pi/2, use 33+33+53 bit pi
                        x += pio2_2;
                        y0 = x + pio2_2t;
                        y1 = (x - y0) + pio2_2t;
                    }
                    n = -1;
                }
            } else {
                double t = abs(x);
                n = (int) (t * invpio2 + half);
                final double fn = n;
                double w = fn * pio2_1t; // 1st round good to 85 bit
                double r = t - fn * pio2_1;
                y0 = r - w;
                // quick check no cancellation
                if (n >= 32 || ix == npio2_hw[n - 1]) {
                    int i = ix - (((int) (Double.doubleToRawLongBits(y0) >> 32)) & (0x7ff << 20));
                    if (i > (16 << 20)) { // 2nd iteration needed, good to 118
                        w = fn * pio2_2;
                        t = r;
                        r -= w;
                        w = fn * pio2_2t - ((t - r) - w);
                        y0 = r - w;
                        i = ix - (((int) (Double.doubleToRawLongBits(y0) >> 32)) & (0x7ff << 20));
                        if (i > (49 << 20)) {// 3rd iteration need, 151 bits acc
                            w = fn * pio2_3;
                            t = r;
                            r -= w;
                            w = fn * pio2_3t - ((t - r) - w);
                            y0 = r - w;
                        }
                    }
                }
                y1 = (r - y0) - w;
                if (hx < 0) {
                    y1 = 0.0d - y1;
                    y0 = 0.0d - y0;
                    n = -n;
                }
            }
            return getTrigres(trigtype, n, y0, y1);
        }

        return ix >= 0x7ff00000 ? x - x : // x is inf or NaN
                        __kernel_rem_pio2(x, hx, trigtype);
    }

    private static final double[] PIo2 = {1.57079625129699707031e+00, 7.54978941586159635335e-08, 5.39030252995776476554e-15, 3.28200341580791294123e-22, 1.27065575308067607349e-29,
                    1.22933308981111328932e-36, 2.73370053816464559624e-44, 2.16741683877804819444e-51};

    private static final double twon24 = 5.96046447753906250000e-08;

    /**
     * Used for tlocal storage to save __kernel_rem_pio2 from major mem allocs.
     *
     * @author gustav trede
     */
    private static class Rempiostruct {
        final double[] f = new double[20];
        final double[] q = new double[20];
        final double[] fq = new double[20];
        final double[] x = new double[3];
        final int[] iq = new int[20];
    }

    /*******
     * private static final ThreadLocal<Rempiostruct> rempstruct = new ThreadLocal<Rempiostruct>() {
     *
     * @Override protected Rempiostruct initialValue() { return new Rempiostruct(); } };
     *******/

    private static class RempStructAcccessor {
        Rempiostruct get() {
            return new Rempiostruct();
        }
    }

    static RempStructAcccessor rempstruct;

    /**
     *
     * __kernel_rem_pio2 return the last three digits of N with y = x - N*pi/2 so that |y| < pi/2.
     *
     * The method is to compute the integer (mod 8) and fraction parts of (2/pi)*x without doing the
     * full multiplication. In general we skip the part of the product that are known to be a huge
     * integer (more accurately, = 0 mod 8 ). Thus the number of operations are independent of the
     * exponent of the input.
     *
     * (2/pi) is represented by an array of 24-bit integers in two_over_pi[].
     *
     * Input parameters: x[] The input value (must be positive) is broken into nx pieces of 24-bit
     * integers in double precision format. x[i] will be the i-th 24 bit of x. The scaled exponent
     * of x[0] is given in input parameter e0 (i.e., x[0]*2^e0 match x's up to 24 bits.
     *
     * Example of breaking a double positive z into x[0]+x[1]+x[2]: e0 = ilogb(z)-23 z =
     * scalbn(z,-e0) for i = 0,1,2 x[i] = floor(z) z = (z-x[i])*2**24
     *
     *
     * y[] ouput result in an array of double precision numbers. The dimension of y[] is: 24-bit
     * precision 1 53-bit precision 2 64-bit precision 2 113-bit precision 3 The actual value is the
     * sum of them. Thus for 113-bit precison, one may have to do something like:
     *
     * long double t,w,r_head, r_tail; t = (long double)y[2] + (long double)y[1]; w = (long
     * double)y[0]; r_head = t+w; r_tail = w - (r_head - t);
     *
     * e0 The exponent of x[0]
     *
     * nx dimension of x[]
     *
     * prec an integer indicating the precision: 0 24 bits (single) 1 53 bits (double) 2 64 bits
     * (extended) 3 113 bits (quad)
     *
     * two_over_pi[] integer array, contains the (24*i)-th to (24*i+23)-th bit of 2/pi after binary
     * point. The corresponding floating value is
     *
     * two_over_pi[i] * 2^(-24(i+1)).
     *
     * External function: double scalbn(), floor();
     *
     *
     * Here is the description of some local variables:
     *
     * jk jk+1 is the initial number of terms of two_over_pi[] needed in the computation. The
     * recommended value is 2,3,4, 6 for single, double, extended,and quad.
     *
     * jz local integer variable indicating the number of terms of two_over_pi[] used.
     *
     * jx nx - 1
     *
     * jv index for pointing to the suitable two_over_pi[] for the computation. In general, we want
     * ( 2^e0*x[0] * two_over_pi[jv-1]*2^(-24jv) )/8 is an integer. Thus e0-3-24*jv >= 0 or
     * (e0-3)/24 >= jv Hence jv = max(0,(e0-3)/24).
     *
     * jp jp+1 is the number of terms in PIo2[] needed, jp = jk.
     *
     * q[] double array with integral value, representing the 24-bits chunk of the product of x and
     * 2/pi.
     *
     * q0 the corresponding exponent of q[0]. Note that the exponent for q[i] would be q0-24*i.
     *
     * PIo2[] double precision array, obtained by cutting pi/2 into 24 bits chunks.
     *
     * f[] two_over_pi[] in floating point
     *
     * iq[] integer array by breaking up q[] in 24-bits chunk.
     *
     * fq[] final product of x*(2/pi) in fq[0],..,fq[jk]
     *
     * ih integer. If >0 it indicates q[] is >= 0.5, hence it also indicates the *sign* of the
     * result.
     *
     * @param xv
     * @param hx
     * @param trig trigtype
     * @return
     */
    @SuppressWarnings("javadoc")
    private static double __kernel_rem_pio2(double xv, int hx, final int trig) {
        Rempiostruct rmp = rempstruct.get();
        double[] x = rmp.x;
        double[] f = rmp.f;
        double[] q = rmp.q;
        double[] fq = rmp.fq;
        int[] iq = rmp.iq;

        /* set z = scalbn(|x|,ilogb(x)-23) */
        long lx = Double.doubleToRawLongBits(xv);
        long exp = ((lx & 0x7ff0000000000000L) >> 52) - 1046;
        lx = (lx - (exp << 52)) & 0x7fffffffffffffffL;
        double zz = Double.longBitsToDouble(lx);
        for (int i = 0; i < 2; i++) {
            x[i] = (int) zz;
            zz = (zz - x[i]) * two24;
        }
        x[2] = zz;
        int nx = 3;
        while (x[nx - 1] == zero) { /* skip zero term */
            nx--;
        }
        double z, fw;
        int ih, n;
        int jk = 4;
        int jp = jk;
        /* determine jx,jv,q0, note that 3>q0 */
        int jx = nx - 1;
        int e0 = (int) exp;
        int jv = (e0 - 3) / 24;
        if (jv < 0) {
            jv = 0;
        }
        int q0 = e0 - (24 * (jv + 1));
        /* set up f[0] to f[jx+jk] where f[jx+jk] = two_over_pi[jv+jk] */
        int j = jv - jx;
        int m = jx + jk;
        for (int i = 0; i <= m; i++, j++) {
            f[i] = ((j < 0) ? zero : two_over_pi[j]);
        }

        /* compute q[0],q[1],...q[jk] */
        for (int i = 0; i <= jk; i++) {
            for (j = 0, fw = 0.0; j <= jx; j++) {
                fw += (x[j] * f[(jx + i) - j]);
            }
            q[i] = fw;
        }
        int jz = jk;
        while (true) { // recompute:
            /* distill q[] into iq[] reversingly */
            j = jz;
            z = q[jz];
            for (int i = 0; j > 0; i++, j--) {
                fw = ((int) (twon24 * z));
                iq[i] = (int) (z - (two24 * fw));
                z = q[j - 1] + fw;
            }
            /* compute n */
            z = scalb(z, q0); /* actual value of z */
            z -= (8.0 * floor(z * 0.125)); /* trim off integer >= 8 */
            n = (int) z;
            z -= n;
            ih = 0;
            if (q0 > 0) { /* need iq[jz-1] to determine n */
                int i = (iq[jz - 1] >> (24 - q0));
                n += i;
                iq[jz - 1] -= (i << (24 - q0));
                ih = iq[jz - 1] >> (23 - q0);
            } else if (q0 == 0) {
                ih = iq[jz - 1] >> 23;
            } else if (z >= 0.5) {
                ih = 2;
            }
            if (ih > 0) { /* q > 0.5 */
                n += 1;
                int carry = 0;
                for (int i = 0; i < jz; i++) { /* compute 1-q */
                    j = iq[i];
                    if (carry == 0) {
                        if (j != 0) {
                            carry = 1;
                            iq[i] = 0x1000000 - j;
                        }
                    } else {
                        iq[i] = 0xffffff - j;
                    }
                }
                /* rare case: chance is 1 in 12 */
                if (q0 == 1) {
                    iq[jz - 1] &= 0x7fffff;
                } else if (q0 == 2) {
                    iq[jz - 1] &= 0x3fffff;
                }
                if (ih == 2) {
                    z = one - z;
                    if (carry != 0) {
                        z -= scalb(one, q0);
                    }
                }
            }
            /* check if recomputation is needed */
            if (z == zero) {
                j = 0;
                for (int i = jz - 1; i >= jk; i--) {
                    j |= iq[i];
                }
                if (j == 0) { /* need recomputation */
                    int k = 1;
                    for (; iq[jk - k] == 0; k++) {
                    }/* k = no. of terms needed */
                    for (int i = jz + 1; i <= (jz + k); i++) {
                        // add q[jz+1] to q[jz+k]
                        f[jx + i] = two_over_pi[jv + i];
                        for (j = 0, fw = 0.0; j <= jx; j++) {
                            fw += (x[j] * f[(jx + i) - j]);
                        }
                        q[i] = fw;
                    }
                    jz += k;
                    continue;
                }
            }
            break;
        }
        /* chop off zero terms */
        if (z == 0.0d) {
            jz--;
            q0 -= 24;
            while (iq[jz] == 0) {
                jz--;
                q0 -= 24;
            }
        } else { /* break z into 24-bit if necessary */
            z = scalb(z, -q0);
            if (z >= two24) {
                fw = (int) (twon24 * z);
                iq[jz] = (int) (z - (two24 * fw));
                jz++;
                q0 += 24;
                iq[jz] = (int) fw;
            } else {
                iq[jz] = (int) z;
            }
        }
        /* convert integer "bit" chunk to floating-point value */
        fw = scalb(one, q0);
        for (int i = jz; i >= 0; i--) {
            q[i] = fw * iq[i];
            fw *= twon24;
        }
        /* compute PIo2[0,...,jp]*q[jz,...,0] */
        for (int i = jz; i >= 0; i--) {
            int k = 0;
            for (fw = 0.0; (k <= jp) && (k <= (jz - i)); k++) {
                fw += PIo2[k] * q[i + k];
            }
            fq[jz - i] = fw;
        }
        /* compress fq[] into y[] */
        fw = 0.0d;
        for (int i = jz; i >= 0; i--) {
            fw += fq[i];
        }
        double y0 = (ih == 0) ? fw : (0.0d - fw);
        fw = fq[0] - fw;
        for (int i = 1; i <= jz; i++) {
            fw += fq[i];
        }
        double y1 = ((ih == 0) ? fw : (0.0d - fw));
        n &= 7;
        if (hx < 0) {
            y0 = 0.0d - y0;
            y1 = 0.0d - y1;
            n = -n;
        }
        return getTrigres(trig, n, y0, y1);
    }

    private static final double pio2_hi = 1.57079632679489655800e+00;
    private static final double pio2_lo = 6.12323399573676603587e-17;
    private static final double pio4_hi = 7.85398163397448278999e-01;
    private static final double PIret = PI + 2.0 * pio2_lo;

    /* coefficient for R(x^2) */
    private static final double pS0 = 1.66666666666666657415e-01;
    private static final double pS1 = -3.25565818622400915405e-01;
    private static final double pS2 = 2.01212532134862925881e-01;
    private static final double pS3 = -4.00555345006794114027e-02;
    private static final double pS4 = 7.91534994289814532176e-04;
    private static final double pS5 = 3.47933107596021167570e-05;
    private static final double qS1 = -2.40339491173441421878e+00;
    private static final double qS2 = 2.02094576023350569471e+00;
    private static final double qS3 = -6.88283971605453293030e-01;
    private static final double qS4 = 7.70381505559019352791e-02;

    /**
     * Returns the arc sine of a value; the returned angle is in the range -<i>pi</i>/2 through
     * <i>pi</i>/2. Special cases:
     * <ul>
     * <li>If the argument is NaN or its absolute value is greater than 1, then the result is NaN.
     * <li>If the argument is zero, then the result is a zero with the same sign as the argument.
     * </ul>
     *
     * @param a the value whose arc sine is to be returned.
     * @return the arc sine of the argument.
     */
    public static double asin(double a) {
        /*
         * asin(x) Method : Since asin(x) = x + x^3/6 + x^5*3/40 + x^7*15/336 + ... we approximate
         * asin(x) on [0,0.5] by asin(x) = x + x*x^2*R(x^2) where R(x^2) is a rational approximation
         * of (asin(x)-x)/x^3 and its remez error is bounded by |(asin(x)-x)/x^3 - R(x^2)| <
         * 2^(-58.75)
         *
         * For x in [0.5,1] asin(x) = pi/2-2*asin(sqrt((1-x)/2)) Let y = (1-x), z = y/2, s :=
         * sqrt(z),and pio2_hi+pio2_lo=pi/2; then for x>0.98 asin(x) = pi/2 - 2*(s+s*z*R(z)) =
         * pio2_hi - (2*(s+s*z*R(z)) - pio2_lo) For x<=0.98, let pio4_hi = pio2_hi/2, then f = hi
         * part of s; c = sqrt(z) - f = (z-f*f)/(s+f) ...f+c=sqrt(z) and asin(x) = pi/2 -
         * 2*(s+s*z*R(z)) = pio4_hi+(pio4-2s)-(2s*z*R(z)-pio2_lo) =
         * pio4_hi+(pio4-2f)-(2s*z*R(z)-(pio2_lo+2c))
         *
         * Special cases: if x is NaN, return x itself; if |x|>1, return NaN with invalid signal.
         */
        int hx = __HI(a);
        int ix = hx & 0x7fffffff;
        if (ix >= 0x3ff00000) { /* |x|>= 1 */
            return ((ix - 0x3ff00000) | __LO(a)) == 0 ? a * pio2_hi + a * pio2_lo : // asin(1)=+-pi/2
                                                                                    // with inexact
                            (a - a) / (a - a); // asin(|x|>1) is NaN
        }
        if (ix < 0x3fe00000) { /* |x|<0.5 */
            if (ix < 0x3e400000) { /* if |x| < 2**-27 */
                if (huge + a > one)
                    return a; /* return x with inexact if x!=0 */
            } else
                return a + (a * getPdivQ(a * a));
        }
        // 1> |x|>= 0.5
        double t = (one - abs(a)) * 0.5;
        double pdivq = getPdivQ(t);
        double s = java.lang.Math.sqrt(t);
        if (ix >= 0x3FEF3333) { /* if |x| > 0.975 */
            t = pio2_hi - (2.0 * (s + s * pdivq) - pio2_lo);
        } else {
            double w = clearLow32bits(s);
            double p = 2.0d * s * pdivq - (pio2_lo - 2.0d * ((t - (w * w)) / (s + w)));
            t = pio4_hi - (p - (pio4_hi - 2.0d * w));
        }
        return hx > 0 ? t : -t;
    }

    /**
     * Returns the arc cosine of a value; the returned angle is in the range 0.0 through <i>pi</i>.
     * Special case:
     * <ul>
     * <li>If the argument is NaN or its absolute value is greater than 1, then the result is NaN.
     * </ul>
     *
     * @param a the value whose arc cosine is to be returned.
     * @return the arc cosine of the argument.
     */
    public static double acos(double a) {
        /*
         * Method : acos(x) = pi/2 - asin(x) acos(-x) = pi/2 + asin(x) For |x|<=0.5 acos(x) = pi/2 -
         * (x + x*x^2*R(x^2)) (see asin.c) For x>0.5 acos(x) = pi/2 - (pi/2 - 2asin(sqrt((1-x)/2)))
         * = 2asin(sqrt((1-x)/2)) = 2s + 2s*z*R(z) ...z=(1-x)/2, s=sqrt(z) = 2f + (2c + 2s*z*R(z))
         * where f=hi part of s, and c= (z-f*f)/(s+f) is the correction term for f so that f+c ~
         * sqrt(z). For x<-0.5 acos(x) = pi - 2asin(sqrt((1-|x|)/2)) = pi - 0.5*(s+s*z*R(z)), where
         * z=(1-|x|)/2,s=sqrt(z)
         *
         * Special cases: if x is NaN, return x itself; if |x|>1, return NaN with invalid signal.
         */

        final int hx = __HI(a);
        final int ix = hx & 0x7fffffff;
        if (ix >= 0x3ff00000) { // |x| >= 1
            if (((ix - 0x3ff00000) | __LO(a)) == 0) { // |x|==1
                return a > 0 ? 0.0 : PIret;
            }
            return (a - a) / (a - a); // acos(|x|>1) is NaN
        }

        if (ix < 0x3fe00000) { // |x| < 0.5
            return ix <= 0x3c600000 ? pio2_hi + pio2_lo : // |x|<2**-57
                            pio2_hi - (a - (pio2_lo - a * getPdivQ(a * a)));
        }

        final double z = 0.5 * ((hx < 0) ? (one + a) : (one - a));
        final double s = java.lang.Math.sqrt(z);
        final double pdivq = s * getPdivQ(z);

        if (hx < 0) { // x < -0.5
            return PI - 2.0 * (s + (pdivq - pio2_lo));
        }
        // x > 0.5
        final double df = clearLow32bits(s);
        return 2.0 * (df + (pdivq + (z - df * df) / (s + df)));
    }

    private static double getPdivQ(double z) {
        return (z * (pS0 + z * (pS1 + z * (pS2 + z * (pS3 + z * (pS4 + z * pS5)))))) / (one + z * (qS1 + z * (qS2 + (z * (qS3 + (z * qS4))))));
    }

    private static final double atanhi[] = {4.63647609000806093515e-01, // atan(0.5)hi
                    7.85398163397448278999e-01, // atan(1.0)hi
                    9.82793723247329054082e-01, // atan(1.5)hi
                    1.57079632679489655800e+00};// atan(inf)hi

    private static final double atanlo[] = {2.26987774529616870924e-17, // atan(0.5)lo
                    3.06161699786838301793e-17, // atan(1.0)lo
                    1.39033110312309984516e-17, // atan(1.5)lo
                    6.12323399573676603587e-17};// atan(inf)lo)

    private static final double ln2_hi = 6.93147180369123816490e-01, ln2_lo = 1.90821492927058770002e-10, Lg1 = 6.666666666666735130e-01, Lg2 = 3.999999999940941908e-01,
                    Lg3 = 2.857142874366239149e-01, Lg4 = 2.222219843214978396e-01, Lg5 = 1.818357216161805012e-01, Lg6 = 1.531383769920937332e-01, Lg7 = 1.479819860511658591e-01,
                    two54 = 1.80143985094819840000e+16, aT0 = 3.33333333333329318027e-01, aT1 = -1.99999999998764832476e-01, aT2 = 1.42857142725034663711e-01, aT3 = -1.11111104054623557880e-01,
                    aT4 = 9.09088713343650656196e-02, aT5 = -7.69187620504482999495e-02, aT6 = 6.66107313738753120669e-02, aT7 = -5.83357013379057348645e-02, aT8 = 4.97687799461593236017e-02,
                    aT9 = -3.65315727442169155270e-02, aT10 = 1.62858201153657823623e-02, atanhi3PLUSatanlo3 = atanhi[3] + atanlo[3], negatanhi3MINUSatanlo3 = -atanhi[3] - atanlo[3],
                    pi_o_4 = 7.8539816339744827900e-01, pi_o_2 = 1.5707963267948965580e+00, pi_lo = 1.2246467991473531772e-16, huge = 1.0e+300, tiny = 1.0e-300;

    /**
     * Returns the arc tangent of a value; the returned angle is in the range -<i>pi</i>/2 through
     * <i>pi</i>/2. Special cases:
     * <ul>
     * <li>If the argument is NaN, then the result is NaN.
     * <li>If the argument is zero, then the result is a zero with the same sign as the argument.
     * </ul>
     *
     * @param aa the value whose arc tangent is to be returned.
     * @return the arc tangent of the argument.
     */
    public static double atan(double aa) {
        /*
         * Method 1. Reduce x to positive by atan(x) = -atan(-x). 2. According to the integer
         * k=4t+0.25 chopped, t=x, the argument is further reduced to one of the following intervals
         * and the arctangent of t is evaluated by the corresponding formula:
         *
         * [0,7/16] atan(x) = t-t^3*(a1+t^2*(a2+...(a10+t^2*a11)...) [7/16,11/16] atan(x) =
         * atan(1/2) + atan( (t-0.5)/(1+t/2) ) [11/16.19/16] atan(x) = atan( 1 ) + atan( (t-1)/(1+t)
         * ) [19/16,39/16] atan(x) = atan(3/2) + atan( (t-1.5)/(1+1.5t) ) [39/16,INF] atan(x) =
         * atan(INF) + atan( -1/t )
         */

        double a = aa;
        int hx = __HI(a);
        int ix = hx & 0x7fffffff;
        if (ix >= 0x44100000) { /* if |x| >= 2^66 */
            if ((ix > 0x7ff00000) || (ix == 0x7ff00000 && __LO(a) != 0)) {
                return a + a; /* NaN */
            }
            return hx > 0 ? atanhi3PLUSatanlo3 : negatanhi3MINUSatanlo3;
        }
        int id;
        if (ix < 0x3fdc0000) { /* |x| < 0.4375 */
            if (ix < 0x3e200000 && /* |x| < 2^-29 */
            (huge + a > one)) {
                return a; /* raise inexact */
            }
            id = -1;
        } else {
            a = abs(a);
            if (ix < 0x3ff30000) { /* |x| < 1.1875 */
                if (ix < 0x3fe60000) { /* 7/16 <=|x|<11/16 */
                    id = 0;
                    a = (2.0d * a - one) / (2.0d + a);
                } else { /* 11/16<=|x|< 19/16 */
                    id = 1;
                    a = (a - one) / (a + one);
                }
            } else {
                if (ix < 0x40038000) { /* |x| < 2.4375 */
                    id = 2;
                    a = (a - 1.5d) / (one + 1.5d * a);
                } else { /* 2.4375 <= |x| < 2^66 */
                    id = 3;
                    a = negone / a;
                }
            }
        }

        /* end of argument reduction */
        double z = a * a;
        double w = z * z;

        /* break sum from i=0 to 10 aTi z**(i+1) into odd and even poly */
        double ss = a * (z * (aT0 + w * (aT2 + w * (aT4 + w * (aT6 + w * (aT8 + w * aT10))))) + w * (aT1 + w * (aT3 + w * (aT5 + w * (aT7 + w * aT9)))));
        if (id < 0) {
            return a - ss;
        }
        z = atanhi[id] - ((ss - atanlo[id]) - a);
        return hx < 0 ? 0.0d - z : z;
    }

    /**
     * Converts an angle measured in degrees to an approximately equivalent angle measured in
     * radians. The conversion from degrees to radians is generally inexact.
     *
     * @param angdeg an angle, in degrees
     * @return the measurement of the angle {@code angdeg} in radians.
     */
    public static strictfp double toRadians(double angdeg) {
        return angdeg / 180.0 * PI;
    }

    /**
     * Converts an angle measured in radians to an approximately equivalent angle measured in
     * degrees. The conversion from radians to degrees is generally inexact; users should <i>not</i>
     * expect {@code cos(toRadians(90.0))} to exactly equal {@code 0.0}.
     *
     * @param angrad an angle, in radians
     * @return the measurement of the angle {@code angrad} in degrees.
     */
    public static strictfp double toDegrees(double angrad) {
        return angrad * 180.0 / PI;
    }

    private static final double ivln10 = 4.34294481903251816668e-01, log10_2hi = 3.01029995663611771306e-01, log10_2lo = 3.69423907715893078616e-13, halF[] = {0.5, -0.5},
                    twom1000 = 9.33263618503218878990e-302,// 2**-1000=0x01700000,0
                    o_threshold = 7.09782712893383973096e+02, u_threshold = -7.45133219101941108420e+02, ln2HI[] = {6.93147180369123816490e-01, -6.93147180369123816490e-01,}, ln2LO[] = {
                                    1.90821492927058770002e-10, -1.90821492927058770002e-10,}, invln2 = 1.44269504088896338700e+00, ln2HI0 = ln2HI[0], ln2LO0 = ln2LO[0];

    /**
     * Returns Euler's number <i>e</i> raised to the power of a {@code double} value. Special cases:
     * <ul>
     * <li>If the argument is NaN, the result is NaN.
     * <li>If the argument is positive infinity, then the result is positive infinity.
     * <li>If the argument is negative infinity, then the result is positive zero.
     * </ul>
     *
     * @param aa the exponent to raise <i>e</i> to.
     * @return the value <i>e</i><sup>{@code a}</sup>, where <i>e</i> is the base of the natural
     *         logarithms.
     */
    public static double exp(double aa) {
        /*
         * exp(x) Returns the exponential of x.
         *
         * Method 1. Argument reduction: Reduce x to an r so that |r| <= 0.5*ln2 ~ 0.34658. Given x,
         * find r and integer k such that
         *
         * x = k*ln2 + r, |r| <= 0.5*ln2.
         *
         * Here r will be represented as r = hi-lo for better accuracy.
         *
         * 2. Approximation of exp(r) by a special rational function on the interval [0,0.34658]:
         * Write R(r**2) = r*(exp(r)+1)/(exp(r)-1) = 2 + r*r/6 - r**4/360 + ... We use a special
         * Reme algorithm on [0,0.34658] to generate a polynomial of degree 5 to approximate R. The
         * maximum error of this polynomial approximation is bounded by 2**-59. In other words, R(z)
         * ~ 2.0 + P1*z + P2*z**2 + P3*z**3 + P4*z**4 + P5*z**5 (where z=r*r, and the values of P1
         * to P5 are listed below) and | 5 | -59 | 2.0+P1*z+...+P5*z - R(z) | <= 2 | | The
         * computation of exp(r) thus becomes 2*r exp(r) = 1 + ------- R - r r*R1(r) = 1 + r +
         * ----------- (for better accuracy) 2 - R1(r) where 2 4 10 R1(r) = r - (P1*r + P2*r + ... +
         * P5*r ).
         *
         * 3. Scale back to obtain exp(x): From step 1, we have exp(x) = 2^k * exp(r)
         *
         * Special cases: exp(INF) is INF, exp(NaN) is NaN; exp(-INF) is 0, and for finite argument,
         * only exp(0)=1 is exact.
         *
         * Accuracy: according to an error analysis, the error is always less than 1 ulp (unit in
         * the last place).
         *
         * Misc. info. For IEEE double if x > 7.09782712893383973096e+02 then exp(x) overflow if x <
         * -7.45133219101941108420e+02 then exp(x) underflow
         */
        double a = aa;
        int hx = __HI(a); /* high word of x */
        final int xsb = (hx >>> 31) & 1; /* sign bit of x */
        final boolean xsb0 = xsb == 0;
        hx &= 0x7fffffff; /* high word of |x| */
        // preliminary check for Nan or Infinity
        if (hx >= 0x7ff00000) {
            // aa is inf or NaN
            if (xsb0) { // aa == Double.NaN || aa == Double.POSITIVE_INFINITY)
                return aa;
            } else {
                // negative infinity?
                return 0.0;
            }
        }

        /* filter out non-finite argument */
        if (hx >= 0x40862E42) { /* if |x|>=709.78... */
            if (hx >= 0x7ff00000) {
                if (((hx & 0xfffff) | __LO(a)) != 0) {
                    return a + a; /* NaN */
                }
                return xsb0 ? a : 0.0d; /* exp(+-inf)={inf,0} */
            }
            if (a > o_threshold) {
                return huge * huge; /* overflow */
            }
            if (a < u_threshold) {
                return twom1000 * twom1000; /* underflow */
            }
        }

        double hi = 0;
        double lo = 0;
        int k = 0;
        /* argument reduction */
        if (hx > 0x3fd62e42) { /* if |x| > 0.5 ln2 */
            if (hx < 0x3FF0A2B2) { /* and |x| < 1.5 ln2 */
                hi = a - ln2HI[xsb];
                lo = ln2LO[xsb];
                k = 1 - xsb - xsb;
            } else {
                k = (int) (invln2 * a + halF[xsb]);
                double t = k;
                hi = a - t * ln2HI0; /* t*ln2HI is exact here */
                lo = t * ln2LO0;
            }
            a = hi - lo;
        } else if (hx < 0x3e300000) { /* when |x|<2**-28 */
            if (huge + a > one) {
                return one + a; /* trigger inexact */
            }
        }

        /* x is now in primary range */
        final double t = a * a;
        final double c = a - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))));
        if (k == 0) {
            return one - (((a * c) / (c - 2.0)) - a);
        }
        final long ybits = Double.doubleToRawLongBits(one - ((lo - ((a * c) / (2.0 - c))) - hi));
        return (k >= -1021) ? addToHighBits(ybits, k << 20) : addToHighBits(ybits, (k + 1000) << 20) * twom1000;
    }

    /**
     * Returns the natural logarithm (base <i>e</i>) of a {@code double} value. Special cases:
     * <ul>
     * <li>If the argument is NaN or less than zero, then the result is NaN.
     * <li>If the argument is positive infinity, then the result is positive infinity.
     * <li>If the argument is positive zero or negative zero, then the result is negative infinity.
     * </ul>
     *
     * @param xx a value
     * @return the value ln&nbsp;{@code a}, the natural logarithm of {@code a}.
     */
    public static double log(double xx) {
        /*
         * __ieee754_log(x) Return the logrithm of x
         *
         * Method : 1. Argument Reduction: find k and f such that x = 2^k * (1+f), where sqrt(2)/2 <
         * 1+f < sqrt(2) .
         *
         * 2. Approximation of log(1+f). Let s = f/(2+f) ; based on log(1+f) = log(1+s) - log(1-s) =
         * 2s + 2/3 s**3 + 2/5 s**5 + ....., = 2s + s*R We use a special Reme algorithm on
         * [0,0.1716] to generate a polynomial of degree 14 to approximate R The maximum error of
         * this polynomial approximation is bounded by 2**-58.45. In other words, 2 4 6 8 10 12 14
         * R(z) ~ Lg1*s +Lg2*s +Lg3*s +Lg4*s +Lg5*s +Lg6*s +Lg7*s (the values of Lg1 to Lg7 are
         * listed in the program) and | 2 14 | -58.45 | Lg1*s +...+Lg7*s - R(z) | <= 2 | | Note that
         * 2s = f - s*f = f -hfsq + s*hfsq, where hfsq = f*f/2. In order to guarantee error in log
         * below 1ulp, we compute log by log(1+f) = f - s*(f - R) (if f is not too large) log(1+f) =
         * f - (hfsq - s*(hfsq+R)). (better accuracy)
         *
         * 3. Finally, log(x) =k*ln2 + log(1+f). =k*ln2_hi+(f-(hfsq-(s*(hfsq+R)+k*ln2_lo))) Here ln2
         * is split into two floating point number: ln2_hi + ln2_lo, where n*ln2_hi is always exact
         * for |n| < 2000.
         *
         * Special cases: log(x) is NaN with signal if x < 0 (including -INF) ; log(+INF) is +INF;
         * log(0) is -INF with signal; log(NaN) is that NaN with no signal.
         *
         * Accuracy: according to an error analysis, the error is always less than 1 ulp (unit in
         * the last place).
         */
        double x = xx;
        int hx = (int) (Double.doubleToRawLongBits(x) >> 32);
        int k = 0;
        if (hx < 0x00100000) { /* x < 2**-1022 */
            if (((hx & 0x7fffffff) | (__LO(x) & 0xFFFFFFFF)) == 0) {
                return Double.NEGATIVE_INFINITY;  // -two54 / zero; /* log(+-0)=-inf */
            }
            if (hx < 0) {
                return Double.NaN; // (x - x) / zero; /* log(-#) = NaN */
            }
            k = -54;
            x *= two54; /* subnormal number, scale up x */
            hx = __HI(x); /* high word of x */
        }
        if (hx >= 0x7ff00000) {
            return x + x;
        }
        k += (hx >> 20) - 1023;
        hx &= 0x000fffff;
        final int i = (hx + 0x95f64) & 0x100000;
        k += i >> 20;
        /* normalize x or x/2 */
        final double dkln2hi = k * ln2_hi;
        final double dkln2lo = k * ln2_lo;
        final boolean KisZero = k == 0;
        final double f = setHigh32bits(x, hx | (i ^ 0x3ff00000)) - 1.0;
        if ((0x000fffff & (2 + hx)) < 3) { /* |f| < 2**-20 */
            if (f == zero) {
                return KisZero ? zero : dkln2hi + dkln2lo;
            }
            double R = f * f * (0.5 - (0.33333333333333333 * f));
            return KisZero ? f - R : dkln2hi - ((R - dkln2lo) - f);
        }
        double s = f / (2.0 + f);
        final double z = s * s;
        final double w = z * z;
        final double R = w * (Lg2 + w * (Lg4 + w * Lg6)) + z * (Lg1 + w * (Lg3 + w * (Lg5 + w * Lg7)));
        if (((hx - 0x6147a) | (0x6b851 - hx)) > 0) {
            double hfsq = 0.5 * f * f;
            s *= hfsq + R;
            return KisZero ? f - (hfsq - s) : dkln2hi - ((hfsq - (s + dkln2lo)) - f);
        }
        s *= f - R;
        return KisZero ? f - s : dkln2hi - ((s - dkln2lo) - f);
    }

    /**
     * Returns the base 10 logarithm of a {@code double} value. Special cases:
     *
     * <ul>
     * <li>If the argument is NaN or less than zero, then the result is NaN.
     * <li>If the argument is positive infinity, then the result is positive infinity.
     * <li>If the argument is positive zero or negative zero, then the result is negative infinity.
     * <li>If the argument is equal to 10<sup><i>n</i></sup> for integer <i>n</i>, then the result
     * is <i>n</i>.
     * </ul>
     *
     * @param aa a value
     * @return the base 10 logarithm of {@code a}.
     * @since 1.5
     */
    public static double log10(double aa) {
        /*
         * __ieee754_log10(x) Return the base 10 logarithm of x
         *
         * Method : Let log10_2hi = leading 40 bits of log10(2) and log10_2lo = log10(2) -
         * log10_2hi, ivln10 = 1/log(10) rounded. Then n = ilogb(x), if(n<0) n = n+1; x =
         * scalbn(x,-n); log10(x) := n*log10_2hi + (n*log10_2lo + ivln10*log(x))
         *
         * Note 1: To guarantee log10(10**n)=n, where 10**n is normal, the rounding mode must set to
         * Round-to-Nearest. Note 2: [1/log(10)] rounded to 53 bits has error .198 ulps; log10 is
         * monotonic at all binary break points.
         *
         * Special cases: log10(x) is NaN with signal if x < 0; log10(+INF) is +INF with no signal;
         * log10(0) is -INF with signal; log10(NaN) is that NaN with no signal; log10(10**N) = N for
         * N=0,1,...,22.
         */
        double a = aa;
        int hx = (int) (Double.doubleToRawLongBits(a) >> 32);
        int k = 0;
        if (hx < 0x00100000) { /* x < 2**-1022 */
            // unsigned low bits
            if (((hx & 0x7fffffff) | (__LO(a) & 0xFFFFFFFF)) == 0) {
                return Double.NEGATIVE_INFINITY;  // -two54 / zero; /* log(+-0)=-inf */
            }
            if (hx < 0) {
                return Double.NaN; // (x - x) / zero; /* log(-#) = NaN */
            }
            k = -54;
            a *= two54; /* subnormal number, scale up x */
            hx = __HI(a); /* high word of x */
        }
        if (hx >= 0x7ff00000) {
            return a + a;
        }
        k += (hx >> 20) - 1023;
        // i = ((unsigned)k&0x80000000)>>31;
        final int i = (k & 0x80000000) >>> 31;
        hx = (hx & 0x000fffff) | ((0x3ff - i) << 20);
        final double y = k + i;
        return (y * log10_2lo + ivln10 * log(setHigh32bits(a, hx))) + y * log10_2hi;
    }

    /**
     * Returns the correctly rounded positive square root of a {@code double} value. Special cases:
     * <ul>
     * <li>If the argument is NaN or less than zero, then the result is NaN.
     * <li>If the argument is positive infinity, then the result is positive infinity.
     * <li>If the argument is positive zero or negative zero, then the result is the same as the
     * argument.
     * </ul>
     * Otherwise, the result is the {@code double} value closest to the true mathematical square
     * root of the argument value.
     *
     * @param a a value.
     * @return the positive square root of {@code a}.
     */
    public static native double sqrt(double a);

    private static final int B1 = 715094163;// B1 = (682-0.03306235651)*2**20
    private static final long B2highbits = 696219795L << 32;// (664-0.03306235651)*2**20 << 32

    private static final double C_ = 5.42857142857142815906e-01, // 19/35 = 0x3FE15F15, 0xF15F15F1
                    D_ = -7.05306122448979611050e-01,// -864/1225= 0xBFE691DE, 0x2532C834
                    E_ = 1.41428571428571436819e+00, // 99/70 = 0x3FF6A0EA, 0x0EA0EA0F
                    F_ = 1.60714285714285720630e+00, // 45/28 = 0x3FF9B6DB, 0x6DB6DB6E
                    G_ = 3.57142857142857150787e-01, // 5/14 = 0x3FD6DB6D, 0xB6DB6DB7
                    TWOpow54 = Double.longBitsToDouble(0x43500000L << 32);

    /**
     * Returns the cube root of a {@code double} value. For positive finite {@code x},
     * {@code cbrt(-x) ==
     * -cbrt(x)}; that is, the cube root of a negative value is the negative of the cube root of
     * that value's magnitude. Special cases:
     *
     * <ul>
     *
     * <li>If the argument is NaN, then the result is NaN.
     *
     * <li>If the argument is infinite, then the result is an infinity with the same sign as the
     * argument.
     *
     * <li>If the argument is zero, then the result is a zero with the same sign as the argument.
     *
     * </ul>
     *
     * @param xx x value.
     * @return the cube root of {@code xx}.
     * @since 1.5
     */
    public static double cbrt(double xx) {
        double x = xx;
        long xb = Double.doubleToRawLongBits(x);
        long hx = xb >> 32; // todo check if faster to not store sign
        final long sign = hx & 0x80000000;// unsigned in c /* sign= sign(x) */
        hx ^= sign;
        if (hx >= 0x7ff00000L) {
            return x + x; /* cbrt(NaN,INF) is itself */
        }
        if ((hx | (xb & 0x00000000FFFFFFFFL)) == 0) {
            return x; /* cbrt(0) is itself */
        }

        x = setHigh32bits(xb, hx); /* x <- |x| */
        /* rough cbrt to 5 bits */
        if (hx < 0x00100000L) { /* subnormal number */
            // HI(t)=0x43500000; /* set t= 2**54 */
            // t*=x; __HI(t)=__HI(t)/3+B2;
            long tb = Double.doubleToRawLongBits(TWOpow54 * x);
            hx = (tb & 0x00000000FFFFFFFFL) | (tb / 3 + B2highbits);
        } else {
            hx = (hx / 3 + B1) << 32;
        }
        double t = Double.longBitsToDouble(hx);

        /* new cbrt to 23 bits, may be implemented in single precision */
        final double s = C_ + (t * t / x) * t;
        t *= G_ + F_ / (s + E_ + D_ / s);

        /* chopped to 20 bits and make it larger than cbrt(x) */
        // __LO(t)=0; __HI(t)+=0x00000001;
        t = Double.longBitsToDouble(((Double.doubleToRawLongBits(t) >> 32) + 0x00000001) << 32);
        // one step newton iteration to 53 bits with error less than 0.667 ulps
        final double r = x / (t * t); /* t*t is exact */
        t += t * ((r - t) / ((t + t) + r)); /* r-s is exact */
        /* retore the sign bit */
        return Double.longBitsToDouble(Double.doubleToRawLongBits(t) | (sign << 32));
    }

    /**
     * Computes the remainder operation on two arguments as prescribed by the IEEE 754 standard. The
     * remainder value is mathematically equal to <code>f1&nbsp;-&nbsp;f2</code>
     * &nbsp;&times;&nbsp;<i>n</i>, where <i>n</i> is the mathematical integer closest to the exact
     * mathematical value of the quotient {@code f1/f2}, and if two mathematical integers are
     * equally close to {@code f1/f2}, then <i>n</i> is the integer that is even. If the remainder
     * is zero, its sign is the same as the sign of the first argument. Special cases:
     * <ul>
     * <li>If either argument is NaN, or the first argument is infinite, or the second argument is
     * positive zero or negative zero, then the result is NaN.
     * <li>If the first argument is finite and the second argument is infinite, then the result is
     * the same as the first argument.
     * </ul>
     *
     * @param f1in the dividend.
     * @param f2in the divisor.
     * @return the remainder when {@code f1} is divided by {@code f2}.
     */
    public static double IEEEremainder(double f1in, double f2in) {
        /*
         * __ieee754_remainder(x,p) Return : returns x REM p = x - [x/p]*p as if in infinite precise
         * arithmetic, where [x/p] is the (infinite bit) integer nearest x/p (in half way case
         * choose the even one). Method : Based on fmod() return x-[x/p]chopped*p exactlp.
         */
        double f1 = f1in;
        double f2 = f2in;
        // todo need to verify that this implementation works.
        long t = Double.doubleToRawLongBits(f1);
        int hx = (int) (t >> 32);
        final long sx = (long) (hx & 0x80000000) << 32; // unsigned in c
        hx &= 0x7fffffff;

        int lx = (int) t; // unsigned in c
        t = Double.doubleToRawLongBits(f2);
        int hp = (int) (t >> 32) & 0x7fffffff;
        int lp = (int) t; // unsigned in c

        /* purge off exception values */
        if (((hp | lp) == 0) || /* p = 0 */
        (hx >= 0x7ff00000) || /* x not finite */
        ((hp >= 0x7ff00000) && /* p is NaN */
        (((hp - 0x7ff00000) | lp) != 0))) {
            return (f1 * f2) / (f1 * f2);
        }

        if (hp <= 0x7fdfffff) {
            // f1 = __ieee754_fmod(f1,f2+f2); /* now x < 2p */
            // f1 = f1 % (f2 + f2); /* now x < 2p */
            // until we get the DREM bytecode working, call fmod here
            f1 = fmod(f1, f2 + f2); /* now x < 2p */
        }

        if (((hx - hp) | (lx - lp)) == 0) {
            return zero * f1;
        }

        f1 = abs(f1);
        f2 = abs(f2);
        if (hp < 0x00200000) {
            if ((f1 + f1) > f2) {
                f1 -= f2;
                if ((f1 + f1) >= f2) {
                    f1 -= f2;
                }
            }
        } else {
            final double p_half = 0.5 * f2;
            if (f1 > p_half) {
                f1 -= f2;
                if (f1 >= p_half) {
                    f1 -= f2;
                }
            }
        }
        return setHigh32bitsXOR(f1, sx);
    }

    /**
     * Returns the smallest (closest to negative infinity) {@code double} value that is greater than
     * or equal to the argument and is equal to a mathematical integer. Special cases:
     * <ul>
     * <li>If the argument value is already equal to a mathematical integer, then the result is the
     * same as the argument.
     * <li>If the argument is NaN or an infinity or positive zero or negative zero, then the result
     * is the same as the argument.
     * <li>If the argument value is less than zero but greater than -1.0, then the result is
     * negative zero.
     * </ul>
     * Note that the value of {@code JStrictMath.ceil(x)} is exactly the value of
     * {@code -JStrictMath.floor(-x)}.
     *
     * @param a a value.
     * @return the smallest (closest to negative infinity) floating-point value that is greater than
     *         or equal to the argument and is equal to a mathematical integer.
     */
    public static double ceil(double a) {
        return Math.ceil(a);
        // return floorOrCeil(a, false);
    }

    /**
     * Returns the largest (closest to positive infinity) {@code double} value that is less than or
     * equal to the argument and is equal to a mathematical integer. Special cases:
     * <ul>
     * <li>If the argument value is already equal to a mathematical integer, then the result is the
     * same as the argument.
     * <li>If the argument is NaN or an infinity or positive zero or negative zero, then the result
     * is the same as the argument.
     * </ul>
     *
     * @param a a value.
     * @return the largest (closest to positive infinity) floating-point value that less than or
     *         equal to the argument and is equal to a mathematical integer.
     */
    public static double floor(double a) {
        return Math.floor(a);
        // return floorOrCeil(a, true);
    }

    /**
     * FDLIBM 5.3 s_ceil.c and s_floor.c combined in one method.
     *
     * @param x value to perform floor or ceil on.
     * @param isfloor true if floor operation, false if ceil
     * @author gustav trede
     * @return
     */
    @SuppressWarnings("javadoc")
    private static double floorOrCeil(double x, boolean isfloor) {
        long xb = Double.doubleToRawLongBits(x);
        long i0 = xb >> 32;
        long i1 = (int) xb;
        final int j0 = (int) (((i0 >> 20) & 0x7ff) - 0x3ff);
        if (j0 < 20) {
            if (j0 < 0) { /* raise inexact if x != 0 */
                if (huge + x > 0.0d) {/* return 0*sign(x) if |x|<1 */
                    if (isfloor) { // floor version
                        if (i0 >= 0) {
                            i0 = i1 = 0;
                        } else if (((i0 & 0x7fffffff) | i1) != 0) {
                            i0 = 0xbff00000;
                            i1 = 0;
                        }
                    } else {   // ceil version
                        if (i0 < 0) {
                            i0 = 0x80000000;
                            i1 = 0;
                        } else if ((i0 | i1) != 0) {
                            i0 = 0x3ff00000;
                            i1 = 0;
                        }
                    }
                }
            } else {
                int i = (0x000fffff) >>> j0; // unsigned declared in c
                if (((i0 & i) | i1) == 0) {
                    return x; /* x is integral */
                }
                if (huge + x > 0.0d) { /* raise inexact flag */
                    if (isfloor && i0 < 0 || !isfloor && i0 > 0) {
                        i0 += (0x00100000) >> j0;
                    }
                    i0 &= (~i);
                    i1 = 0;
                }
            }
        } else if (j0 > 51) {
            return j0 == 0x400 ? x + x : x; /* inf or NaN , integral */
        } else {
            // i = ((unsigned)(0xffffffff))>>(j0-20);
            int i = 0xffffffff >>> (j0 - 20); // unsigned declared in c
            if ((i1 & i) == 0) {
                return x; /* x is integral */
            }
            if (huge + x > 0.0d) { /* raise inexact flag */
                if (isfloor && i0 < 0 || !isfloor && i0 > 0) {
                    if (j0 == 20) {
                        i0 += 1;
                    } else {
                        // unsigned j = i1+(1<<(52-j0));
                        // if(j<i1)
                        long j = i1 + (1L << (52 - j0));
                        if ((j & 0x7fffffffffffffffL) < (i1 & 0x7fffffffffffffffL)) {
                            i0 += 1; /* got a carry */
                        }
                        i1 = j;
                    }
                }
                i1 &= ~i;
            }
        }
        return Double.longBitsToDouble((i0 << 32) | (i1 & 0x00000000FFFFFFFFL));
    }

    /**
     * Returns the {@code double} value that is closest in value to the argument and is equal to a
     * mathematical integer. If two {@code double} values that are mathematical integers are equally
     * close to the value of the argument, the result is the integer value that is even. Special
     * cases:
     * <ul>
     * <li>If the argument value is already equal to a mathematical integer, then the result is the
     * same as the argument.
     * <li>If the argument is NaN or an infinity or positive zero or negative zero, then the result
     * is the same as the argument.
     * </ul>
     *
     * @param aa a value.
     * @return the closest floating-point value to {@code a} that is equal to a mathematical
     *         integer.
     * @author Joseph D. Darcy
     */
    @SuppressWarnings("deprecation")
    public static double rint(double aa) {
        /*
         * If the absolute value of a is not less than 2^52, it is either a finite integer (the
         * double format does not have enough significand bits for a number that large to have any
         * fractional portion), an infinity, or a NaN. In any of these cases, rint of the argument
         * is the argument.
         *
         * Otherwise, the sum (twoToThe52 + a ) will properly round away any fractional portion of a
         * since ulp(twoToThe52) == 1.0; subtracting out twoToThe52 from this sum will then be exact
         * and leave the rounded integer portion of a.
         *
         * This method does *not* need to be declared strictfp to get fully reproducible results.
         * Whether or not a method is declared strictfp can only make a difference in the returned
         * result if some operation would overflow or underflow with strictfp semantics. The
         * operation (twoToThe52 + a ) cannot overflow since large values of a are screened out; the
         * add cannot underflow since twoToThe52 is too large. The subtraction ((twoToThe52 + a ) -
         * twoToThe52) will be exact as discussed above and thus cannot overflow or meaningfully
         * underflow. Finally, the last multiply in the return statement is by plus or minus 1.0,
         * which is exact too.
         */
        double a = aa;
        double twoToThe52 = 1L << 52; // 2^52
        double sign = FpUtils.rawCopySign(1.0, a); // preserve sign info
        a = Math.abs(a);

        if (a < twoToThe52) { // E_min <= ilogb(a) <= 51
            a = ((twoToThe52 + a) - twoToThe52);
        }

        return sign * a; // restore original sign
    }

    /**
     * Returns the angle <i>theta</i> from the conversion of rectangular coordinates ({@code x}
     * ,&nbsp;{@code y}) to polar coordinates (r,&nbsp;<i>theta</i>). This method computes the phase
     * <i>theta</i> by computing an arc tangent of {@code y/x} in the range of -<i>pi</i> to
     * <i>pi</i>. Special cases:
     * <ul>
     * <li>If either argument is NaN, then the result is NaN.
     * <li>If the first argument is positive zero and the second argument is positive, or the first
     * argument is positive and finite and the second argument is positive infinity, then the result
     * is positive zero.
     * <li>If the first argument is negative zero and the second argument is positive, or the first
     * argument is negative and finite and the second argument is positive infinity, then the result
     * is negative zero.
     * <li>If the first argument is positive zero and the second argument is negative, or the first
     * argument is positive and finite and the second argument is negative infinity, then the result
     * is the {@code double} value closest to <i>pi</i>.
     * <li>If the first argument is negative zero and the second argument is negative, or the first
     * argument is negative and finite and the second argument is negative infinity, then the result
     * is the {@code double} value closest to -<i>pi</i>.
     * <li>If the first argument is positive and the second argument is positive zero or negative
     * zero, or the first argument is positive infinity and the second argument is finite, then the
     * result is the {@code double} value closest to <i>pi</i>/2.
     * <li>If the first argument is negative and the second argument is positive zero or negative
     * zero, or the first argument is negative infinity and the second argument is finite, then the
     * result is the {@code double} value closest to -<i>pi</i>/2.
     * <li>If both arguments are positive infinity, then the result is the {@code double} value
     * closest to <i>pi</i>/4.
     * <li>If the first argument is positive infinity and the second argument is negative infinity,
     * then the result is the {@code double} value closest to 3*<i>pi</i>/4.
     * <li>If the first argument is negative infinity and the second argument is positive infinity,
     * then the result is the {@code double} value closest to -<i>pi</i>/4.
     * <li>If both arguments are negative infinity, then the result is the {@code double} value
     * closest to -3*<i>pi</i>/4.
     * </ul>
     *
     * @param y the ordinate coordinate
     * @param x the abscissa coordinate
     * @return the <i>theta</i> component of the point (<i>r</i>,&nbsp;<i>theta</i>) in polar
     *         coordinates that corresponds to the point (<i>x</i>,&nbsp;<i>y</i>) in Cartesian
     *         coordinates.
     */
    public static double atan2(double y, double x) {
        /*
         * __ieee754_atan2(y,x) Method : 1. Reduce y to positive by atan2(y,x)=-atan2(-y,x). 2.
         * Reduce x to positive by (if x and y are unexceptional): ARG (x+iy) = arctan(y/x) ... if x
         * > 0, ARG (x+iy) = pi - arctan[y/(-x)] ... if x < 0,
         *
         * Special cases:
         *
         * ATAN2((anything), NaN ) is NaN; ATAN2(NAN , (anything) ) is NaN; ATAN2(+-0, +(anything
         * but NaN)) is +-0 ; ATAN2(+-0, -(anything but NaN)) is +-pi ; ATAN2(+-(anything but 0 and
         * NaN), 0) is +-pi/2; ATAN2(+-(anything but INF and NaN), +INF) is +-0 ; ATAN2(+-(anything
         * but INF and NaN), -INF) is +-pi; ATAN2(+-INF,+INF ) is +-pi/4 ; ATAN2(+-INF,-INF ) is
         * +-3pi/4; ATAN2(+-INF, (anything but,0,NaN, and INF)) is +-pi/2;
         */
        long t = Double.doubleToRawLongBits(x);
        int hx = (int) (t >> 32);
        int lx = (int) t;
        int ix = hx & 0x7fffffff;
        t = Double.doubleToRawLongBits(y);
        int hy = (int) (t >> 32);
        int ly = (int) t;
        int iy = hy & 0x7fffffff;
        if (((ix | ((lx | -lx) >>> 31)) > 0x7ff00000) || ((iy | ((ly | -ly) >>> 31)) > 0x7ff00000)) {/*
                                                                                                      * x
                                                                                                      * or
                                                                                                      * y
                                                                                                      * is
                                                                                                      * NaN
                                                                                                      */
            return x + y;
        }
        if (((hx - 0x3ff00000) | lx) == 0) {
            return atan(y); /* x=1.0 */
        }
        int m = ((hy >> 31) & 1) | ((hx >> 30) & 2); /* 2*sign(x)+sign(y) */

        /* when y = 0 */
        if ((iy | ly) == 0) {
            switch (m) {
                case 0:
                case 1:
                    return y; /* atan(+-0,+anything)=+-0 */
                case 2:
                    return PI + tiny; /* atan(+0,-anything) = pi */
                case 3:
                    return -PI - tiny; /* atan(-0,-anything) =-pi */
            }
        }

        /* when x = 0 */
        if ((ix | lx) == 0) {
            return hy < 0 ? -pi_o_2 - tiny : pi_o_2 + tiny;
        }

        /* when x is INF */
        if (ix == 0x7ff00000) {
            if (iy == 0x7ff00000) {
                switch (m) {
                    case 0:
                        return pi_o_4 + tiny; /* atan(+INF,+INF) */
                    case 1:
                        return -pi_o_4 - tiny; /* atan(-INF,+INF) */
                    case 2:
                        return (3.0 * pi_o_4) + tiny; /* atan(+INF,-INF) */
                    case 3:
                        return (-3.0 * pi_o_4) - tiny; /* atan(-INF,-INF) */
                }
            } else {
                switch (m) {
                    case 0:
                        return zero; /* atan(+...,+INF) */
                    case 1:
                        return -zero; /* atan(-...,+INF) */
                    case 2:
                        return PI + tiny; /* atan(+...,-INF) */
                    case 3:
                        return -PI - tiny; /* atan(-...,-INF) */
                }
            }
        }

        /* when y is INF */
        if (iy == 0x7ff00000) {
            return hy < 0 ? -pi_o_2 - tiny : pi_o_2 + tiny;
        }

        /* compute y/x */
        double z;
        int k = (iy - ix) >> 20;
        if (k > 60) { /* |y/x| > 2**60 */
            z = pi_o_2 + (0.5 * pi_lo);
        } else if ((hx < 0) && (k < -60)) {
            z = 0.0; /* |y|/x < -2**60 */
        } else {
            z = atan(abs(y / x)); /* safe to do y/x */
        }

        switch (m) {
            case 0:
                return z; /* atan(+,+) */
            case 1:
                return setHigh32bitsXOR(z, (0x80000000L << 32));// atan(-,+)
            case 2:
                return PI - (z - pi_lo); /* atan(+,-) */
            default:
                return (z - pi_lo) - PI; /* atan(-,-) */
        }
    }

    private static final double bp[] = {1.0, 1.5,}, dp_h[] = {0.0, 5.84962487220764160156e-01,}, dp_l[] = {0.0, 1.35003920212974897128e-08,}, two53 = 9007199254740992.0,
    /* poly coefs for (3/2)*(log(x)-2s-2/3*s**3 */
    L1 = 5.99999999999994648725e-01, L2 = 4.28571428578550184252e-01, L3 = 3.33333329818377432918e-01, L4 = 2.72728123808534006489e-01, L5 = 2.30660745775561754067e-01,
                    L6 = 2.06975017800338417784e-01, P1 = 1.66666666666666019037e-01, P2 = -2.77777777770155933842e-03, P3 = 6.61375632143793436117e-05, P4 = -1.65339022054652515390e-06,
                    P5 = 4.13813679705723846039e-08, lg2 = 6.93147180559945286227e-01, lg2_h = 6.93147182464599609375e-01, lg2_l = -1.90465429995776804525e-09, ovt = 8.0085662595372944372e-0017,// -(1024-log2(ovfl+.5ulp))
                    cp = 9.61796693925975554329e-01, // =2/(3ln2)
                    cp_h = 9.61796700954437255859e-01, // =(float)cp
                    cp_l = -7.02846165095275826516e-09,// =tail of cp_h
                    ivln2 = 1.44269504088896338700e+00,  // =1/ln2
                    ivln2_h = 1.44269502162933349609e+00,// =24b 1/ln2
                    ivln2_l = 1.92596299112661746887e-08;// =1/ln2 tail

    /**
     * Returns the value of the first argument raised to the power of the second argument. Special
     * cases:
     *
     * <ul>
     * <li>If the second argument is positive or negative zero, then the result is 1.0.
     * <li>If the second argument is 1.0, then the result is the same as the first argument.
     * <li>If the second argument is NaN, then the result is NaN.
     * <li>If the first argument is NaN and the second argument is nonzero, then the result is NaN.
     *
     * <li>If
     * <ul>
     * <li>the absolute value of the first argument is greater than 1 and the second argument is
     * positive infinity, or
     * <li>the absolute value of the first argument is less than 1 and the second argument is
     * negative infinity,
     * </ul>
     * then the result is positive infinity.
     *
     * <li>If
     * <ul>
     * <li>the absolute value of the first argument is greater than 1 and the second argument is
     * negative infinity, or
     * <li>the absolute value of the first argument is less than 1 and the second argument is
     * positive infinity,
     * </ul>
     * then the result is positive zero.
     *
     * <li>If the absolute value of the first argument equals 1 and the second argument is infinite,
     * then the result is NaN.
     *
     * <li>If
     * <ul>
     * <li>the first argument is positive zero and the second argument is greater than zero, or
     * <li>the first argument is positive infinity and the second argument is less than zero,
     * </ul>
     * then the result is positive zero.
     *
     * <li>If
     * <ul>
     * <li>the first argument is positive zero and the second argument is less than zero, or
     * <li>the first argument is positive infinity and the second argument is greater than zero,
     * </ul>
     * then the result is positive infinity.
     *
     * <li>If
     * <ul>
     * <li>the first argument is negative zero and the second argument is greater than zero but not
     * a finite odd integer, or
     * <li>the first argument is negative infinity and the second argument is less than zero but not
     * a finite odd integer,
     * </ul>
     * then the result is positive zero.
     *
     * <li>If
     * <ul>
     * <li>the first argument is negative zero and the second argument is a positive finite odd
     * integer, or
     * <li>the first argument is negative infinity and the second argument is a negative finite odd
     * integer,
     * </ul>
     * then the result is negative zero.
     *
     * <li>If
     * <ul>
     * <li>the first argument is negative zero and the second argument is less than zero but not a
     * finite odd integer, or
     * <li>the first argument is negative infinity and the second argument is greater than zero but
     * not a finite odd integer,
     * </ul>
     * then the result is positive infinity.
     *
     * <li>If
     * <ul>
     * <li>the first argument is negative zero and the second argument is a negative finite odd
     * integer, or
     * <li>the first argument is negative infinity and the second argument is a positive finite odd
     * integer,
     * </ul>
     * then the result is negative infinity.
     *
     * <li>If the first argument is finite and less than zero
     * <ul>
     * <li>if the second argument is a finite even integer, the result is equal to the result of
     * raising the absolute value of the first argument to the power of the second argument
     *
     * <li>if the second argument is a finite odd integer, the result is equal to the negative of
     * the result of raising the absolute value of the first argument to the power of the second
     * argument
     *
     * <li>if the second argument is finite and not an integer, then the result is NaN.
     * </ul>
     *
     * <li>If both arguments are integers, then the result is exactly equal to the mathematical
     * result of raising the first argument to the power of the second argument if that result can
     * in fact be represented exactly as a {@code double} value.
     * </ul>
     *
     * <p>
     * (In the foregoing descriptions, a floating-point value is considered to be an integer if and
     * only if it is finite and a fixed point of the method {@link #ceil ceil} or, equivalently, a
     * fixed point of the method {@link #floor floor}. A value is a fixed point of a one-argument
     * method if and only if the result of applying the method to the value is equal to the value.)
     *
     * @param x base.
     * @param y the exponent.
     * @return the value {@code a}<sup>{@code b}</sup>.
     */
    public static double pow(double x, double y) {
        /*
         * __ieee754_pow(x,y) return x**y
         *
         * n Method: Let x = 2 * (1+f) 1. Compute and return log2(x) in two pieces: log2(x) = w1 +
         * w2, where w1 has 53-24 = 29 bit trailing zeros. 2. Perform y*log2(x) = n+y' by simulating
         * muti-precision arithmetic, where |y'|<=0.5. 3. Return x**y = 2**n*exp(y'*log2)
         *
         * Special cases: 1. (anything) ** 0 is 1 2. (anything) ** 1 is itself 3. (anything) ** NAN
         * is NAN 4. NAN ** (anything except 0) is NAN 5. +-(|x| > 1) ** +INF is +INF 6. +-(|x| > 1)
         * ** -INF is +0 7. +-(|x| < 1) ** +INF is +0 8. +-(|x| < 1) ** -INF is +INF 9. +-1 ** +-INF
         * is NAN 10. +0 ** (+anything except 0, NAN) is +0 11. -0 ** (+anything except 0, NAN, odd
         * integer) is +0 12. +0 ** (-anything except 0, NAN) is +INF 13. -0 ** (-anything except 0,
         * NAN, odd integer) is +INF 14. -0 ** (odd integer) = -( +0 ** (odd integer) ) 15. +INF **
         * (+anything except 0,NAN) is +INF 16. +INF ** (-anything except 0,NAN) is +0 17. -INF **
         * (anything) = -0 ** (-anything) 18. (-anything) ** (integer) is
         * (-1)**(integer)*(+anything**integer) 19. (-anything except 0 and inf) ** (non-integer) is
         * NAN
         *
         * Accuracy: pow(x,y) returns x**y nearly rounded. In particular pow(integer,integer) always
         * returns the correct integer provided it is representable.
         */

        // i0 = ((*(int*)&one)>>29)^1; i1=1-i0;

        long xb = Double.doubleToRawLongBits(x);
        int hx = (int) (xb >> 32);
        int lx = (int) xb & 0x7fffffff;  // unsigned
        int ix = hx & 0x7fffffff;
        xb = Double.doubleToRawLongBits(y);
        int hy = (int) (xb >> 32);
        int ly = (int) xb & 0x7fffffff; // unsigned
        int iy = hy & 0x7fffffff;

        /* y==zero: x**0 = 1 */
        if ((iy | ly) == 0)
            return one;

        /* +-NaN return x+y */
        if (ix > 0x7ff00000 || ((ix == 0x7ff00000) && (lx != 0)) || iy > 0x7ff00000 || ((iy == 0x7ff00000) && (ly != 0)))
            return x + y;

        /*
         * determine if y is an odd int when x < 0 yisint = 0 ... y is not an integer yisint = 1 ...
         * y is an odd int yisint = 2 ... y is an even int
         */
        int yisint = 0;
        if (hx < 0) {
            if (iy >= 0x43400000)
                yisint = 2; /* even integer y */
            else if (iy >= 0x3ff00000) {
                int k = (iy >> 20) - 0x3ff; /* exponent */
                if (k > 20) {
                    int j = ly >> (52 - k);
                    if ((j << (52 - k)) == ly)
                        yisint = 2 - (j & 1);
                } else if (ly == 0) {
                    int j = iy >> (20 - k);
                    if ((j << (20 - k)) == iy)
                        yisint = 2 - (j & 1);
                }
            }
        }

        /* special value of y */
        if (ly == 0) {
            if (iy == 0x7ff00000) { /* y is +-inf */
                if (((ix - 0x3ff00000) | lx) == 0)
                    return y - y; /* inf**+-1 is NaN */
                return (ix >= 0x3ff00000) ? /* (|x|>1)**+-inf = inf,0 */
                (hy >= 0) ? y : zero : /* (|x|<1)**-,+inf = inf,0 */
                (hy < 0) ? -y : zero;
            }
            if (iy == 0x3ff00000) { /* y is +-1 */
                return (hy < 0) ? one / x : x;
            }
            if (hy == 0x40000000)
                return x * x; /* y is 2 */
            if (hy == 0x3fe00000) { /* y is 0.5 */
                if (hx >= 0) /* x >= +0 */
                    return Math.sqrt(x);
            }
        }

        double ax = abs(x);
        /* special value of x */
        if (lx == 0) {
            if (ix == 0x7ff00000 || ix == 0 || ix == 0x3ff00000) {
                double z = ax; /* x is +-0,+-inf,+-1 */
                if (hy < 0)
                    z = one / z; /* z = (1/|x|) */
                if (hx < 0) {
                    if (((ix - 0x3ff00000) | yisint) == 0) {
                        z = (z - z) / (z - z); /* (-1)**non-int is NaN */
                    } else if (yisint == 1)
                        z = -z; /* (x<0)**odd = -(|x|**odd) */
                }
                return z;
            }
        }

        int n = (hx >> 31) + 1;

        /* (x<0)**(non-int) is NaN */
        if ((n | yisint) == 0)
            return (x - x) / (x - x);

        double s = one; /* s (sign of result -ve**odd) = -1 else = 1 */
        if ((n | (yisint - 1)) == 0)
            s = -one;/* (-ve)**(odd int) */

        double t1, t2;
        /* |y| is huge */
        if (iy > 0x41e00000) { /* if |y| > 2**31 */
            if (iy > 0x43f00000) { /* if |y| > 2**64, must o/uflow */
                if (ix <= 0x3fefffff)
                    return (hy < 0) ? huge * huge : tiny * tiny;
                if (ix >= 0x3ff00000)
                    return (hy > 0) ? huge * huge : tiny * tiny;
            }
            /* over/underflow if x is not close to one */
            if (ix < 0x3fefffff)
                return (hy < 0) ? s * huge * huge : s * tiny * tiny;
            if (ix > 0x3ff00000)
                return (hy > 0) ? s * huge * huge : s * tiny * tiny;
            /*
             * now |1-x| is tiny <= 2**-20, suffice to compute log(x) by x-x^2/2+x^3/3-x^4/4
             */
            double t = ax - one; /* t has 20 trailing zeros */
            double w = (t * t) * (0.5 - t * (0.3333333333333333333333 - t * 0.25));
            double u = ivln2_h * t; /* ivln2_h has 21 sig. bits */
            double v = t * ivln2_l - w * ivln2;
            t1 = clearLow32bits(u + v);
            t2 = v - (t1 - u);
        } else {
            n = 0;
            /* take care subnormal number */
            if (ix < 0x00100000) {
                ax *= two53;
                n -= 53;
                ix = __HI(ax);
            }
            n += ((ix) >> 20) - 0x3ff;
            int j = ix & 0x000fffff;
            /* determine interval */
            ix = j | 0x3ff00000; /* normalize ix */
            int k = 0;
            if (j <= 0x3988E)
                k = 0; /* |x|<sqrt(3/2) */
            else if (j < 0xBB67A)
                k = 1; /* |x|<sqrt(3) */
            else {
                n += 1;
                ix -= 0x00100000;
            }

            ax = setHigh32bits(ax, ix); // __HI(ax) = ix;

            /* compute ss = s_h+s_l = (x-1)/(x+1) or (x-1.5)/(x+1.5) */
            double u = ax - bp[k]; /* bp[0]=1.0, bp[1]=1.5 */
            double v = one / (ax + bp[k]);
            double ss = u * v;
            double s_h = clearLow32bits(ss);
            /* t_h=ax+bp[k] High */
            // __HI(t_h)=((ix>>1)|0x20000000)+0x00080000+(k<<18);
            double t_h = setHigh32bitsDontMask(zerobitshigh, ((ix >> 1) | 0x20000000) + 0x00080000 + (k << 18));
            double s_l = v * ((u - s_h * t_h) - s_h * (ax - (t_h - bp[k])));
            /* compute log(ax) */
            double s2 = ss * ss;
            double r = s2 * s2 * (L1 + s2 * (L2 + s2 * (L3 + s2 * (L4 + s2 * (L5 + s2 * L6))))) + s_l * (s_h + ss);
            s2 = s_h * s_h;
            t_h = clearLow32bits(3.0 + s2 + r);
            /* u+v = ss*(1+...) */
            u = s_h * t_h;
            v = s_l * t_h + (r - ((t_h - 3.0) - s2)) * ss;
            /* 2/(3log2)*(ss+...) */
            double p_h = clearLow32bits(u + v); // __LO(p_h) = 0;
            double z_h = cp_h * p_h; /* cp_h+cp_l = 2/(3*log2) */
            double z_l = cp_l * p_h + (v - (p_h - u)) * cp + dp_l[k];
            /* log2(ax) = (ss+..)*2/(3*log2) = n + dp_h + z_h + z_l */
            double t = n;
            t1 = clearLow32bits((((z_h + z_l) + dp_h[k]) + t)); // __LO(t1) = 0;
            t2 = z_l - (((t1 - t) - dp_h[k]) - z_h);
        }

        /* split up y into y1+y2 and compute (y1+y2)*(t1+t2) */
        double y1 = clearLow32bits(y); // __LO(y1) = 0;
        double p_l = (y - y1) * t1 + y * t2;
        double p_h = y1 * t1;
        double z = p_l + p_h;
        long zb = Double.doubleToRawLongBits(z);
        int j = (int) (zb >> 32);
        if (j >= 0x40900000) { /* z >= 1024 */
            if (((j - 0x40900000) | ((int) zb)) != 0) /* if z > 1024 */
                return s * huge * huge; /* overflow */
            else {
                if (p_l + ovt > z - p_h)
                    return s * huge * huge; /* overflow */
            }
        } else if ((j & 0x7fffffff) >= 0x4090cc00) { /* z <= -1075 */
            if (((j - 0xc090cc00) | ((int) zb)) != 0) /* z < -1075 */
                return s * tiny * tiny; /* underflow */
            else {
                if (p_l <= z - p_h)
                    return s * tiny * tiny; /* underflow */
            }
        }
        /*
         * compute 2**(p_h+p_l)
         */
        int i = j & 0x7fffffff;
        n = 0;
        if (i > 0x3fe00000) { /* if |z| > 0.5, set n = [z+0.5] */
            n = j + (0x00100000 >> ((i >> 20) - 0x3ff + 1));
            final int k = ((n & 0x7fffffff) >> 20) - 0x3ff; /* new k for n */
            double t = setHigh32bitsDontMask(zerobitshigh, (n & ~(0x000fffff >> k)));
            n = ((n & 0x000fffff) | 0x00100000) >> (20 - k);
            if (j < 0)
                n = -n;
            p_h -= t;
        }
        double t = clearLow32bits(p_l + p_h);// __LO(t) = 0;
        double u = t * lg2_h;
        double v = (p_l - (t - p_h)) * lg2 + t * lg2_l;
        z = u + v;
        double w = v - (z - u);
        t = z * z;
        t1 = z - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))));
        z = one - (((z * t1) / (t1 - two) - (w + z * w)) - z);
        j = __HI(z) + (n << 20);
        return s * ((j >> 20) <= 0 ? scalb(z, n) : /* subnormal output */
        addToHighBits(z, (long) n << (20)));// __HI(z) += (n<<20);
    }

    /**
     * Returns the closest {@code int} to the argument. The result is rounded to an integer by
     * adding 1/2, taking the floor of the result, and casting the result to type {@code int}. In
     * other words, the result is equal to the value of the expression:
     * <p>
     * {@code (int)Math.floor(a + 0.5f)}
     *
     * <p>
     * Special cases:
     * <ul>
     * <li>If the argument is NaN, the result is 0.
     * <li>If the argument is negative infinity or any value less than or equal to the value of
     * {@code Integer.MIN_VALUE}, the result is equal to the value of {@code Integer.MIN_VALUE}.
     * <li>If the argument is positive infinity or any value greater than or equal to the value of
     * {@code Integer.MAX_VALUE}, the result is equal to the value of {@code Integer.MAX_VALUE}.
     * </ul>
     *
     * @param a a floating-point value to be rounded to an integer.
     * @return the value of the argument rounded to the nearest {@code int} value.
     * @see java.lang.Integer#MAX_VALUE
     * @see java.lang.Integer#MIN_VALUE
     */
    public static int round(float a) {
        return (int) floor(a + 0.5f);
    }

    /**
     * Returns the closest {@code long} to the argument. The result is rounded to an integer by
     * adding 1/2, taking the floor of the result, and casting the result to type {@code long}. In
     * other words, the result is equal to the value of the expression:
     * <p>
     * {@code (long)Math.floor(a + 0.5d)}
     *
     * <p>
     * Special cases:
     * <ul>
     * <li>If the argument is NaN, the result is 0.
     * <li>If the argument is negative infinity or any value less than or equal to the value of
     * {@code Long.MIN_VALUE}, the result is equal to the value of {@code Long.MIN_VALUE}.
     * <li>If the argument is positive infinity or any value greater than or equal to the value of
     * {@code Long.MAX_VALUE}, the result is equal to the value of {@code Long.MAX_VALUE}.
     * </ul>
     *
     * @param a a floating-point value to be rounded to a {@code long}.
     * @return the value of the argument rounded to the nearest {@code long} value.
     * @see java.lang.Long#MAX_VALUE
     * @see java.lang.Long#MIN_VALUE
     */
    public static long round(double a) {
        return (long) floor(a + 0.5d);
    }

    private static Random randomNumberGenerator;

    private static synchronized void initRNG() {
        if (randomNumberGenerator == null) {
            randomNumberGenerator = new Random();
        }
    }

    /**
     * Returns a {@code double} value with a positive sign, greater than or equal to {@code 0.0} and
     * less than {@code 1.0}. Returned values are chosen pseudorandomly with (approximately) uniform
     * distribution from that range.
     *
     * <p>
     * When this method is first called, it creates a single new pseudorandom-number generator,
     * exactly as if by the expression <blockquote>{@code new java.util.Random}</blockquote> This
     * new pseudorandom-number generator is used thereafter for all calls to this method and is used
     * nowhere else.
     *
     * <p>
     * This method is properly synchronized to allow correct use by more than one thread. However,
     * if many threads need to generate pseudorandom numbers at a great rate, it may reduce
     * contention for each thread to have its own pseudorandom number generator.
     *
     * @return a pseudorandom {@code double} greater than or equal to {@code 0.0} and less than
     *         {@code 1.0}.
     * @see java.util.Random#nextDouble()
     */
    public static double random() {
        if (randomNumberGenerator == null) {
            initRNG();
        }
        return randomNumberGenerator.nextDouble();
    }

    /**
     * Returns the absolute value of an {@code int} value.. If the argument is not negative, the
     * argument is returned. If the argument is negative, the negation of the argument is returned.
     *
     * <p>
     * Note that if the argument is equal to the value of {@link Integer#MIN_VALUE}, the most
     * negative representable {@code int} value, the result is that same value, which is negative.
     *
     * @param a the argument whose absolute value is to be determined.
     * @return the absolute value of the argument.
     */
    public static int abs(int a) {
        return Math.abs(a);
        // return (a < 0) ? -a : a;
    }

    /**
     * Returns the absolute value of a {@code long} value. If the argument is not negative, the
     * argument is returned. If the argument is negative, the negation of the argument is returned.
     *
     * <p>
     * Note that if the argument is equal to the value of {@link Long#MIN_VALUE}, the most negative
     * representable {@code long} value, the result is that same value, which is negative.
     *
     * @param a the argument whose absolute value is to be determined.
     * @return the absolute value of the argument.
     */
    public static long abs(long a) {
        return Math.abs(a);
        // return (a < 0) ? -a : a;
    }

    /**
     * Returns the absolute value of a {@code float} value. If the argument is not negative, the
     * argument is returned. If the argument is negative, the negation of the argument is returned.
     * Special cases:
     * <ul>
     * <li>If the argument is positive zero or negative zero, the result is positive zero.
     * <li>If the argument is infinite, the result is positive infinity.
     * <li>If the argument is NaN, the result is NaN.
     * </ul>
     * In other words, the result is the same as the value of the expression:
     * <p>
     * {@code Float.intBitsToFloat(0x7fffffff & Float.floatToIntBits(a))}
     *
     * @param a the argument whose absolute value is to be determined
     * @return the absolute value of the argument.
     */
    public static float abs(float a) {
        return Math.abs(a);
        // return (a <= 0.0F) ? 0.0F - a : a;
    }

    /**
     * Returns the absolute value of a {@code double} value. If the argument is not negative, the
     * argument is returned. If the argument is negative, the negation of the argument is returned.
     * Special cases:
     * <ul>
     * <li>If the argument is positive zero or negative zero, the result is positive zero.
     * <li>If the argument is infinite, the result is positive infinity.
     * <li>If the argument is NaN, the result is NaN.
     * </ul>
     * In other words, the result is the same as the value of the expression:
     * <p>
     * {@code Double.longBitsToDouble((Double.doubleToLongBits(a)<<1)>>>1)}
     *
     * @param a the argument whose absolute value is to be determined
     * @return the absolute value of the argument.
     */
    public static double abs(double a) {
        return Math.abs(a);
        // return (a <= 0.0D) ? 0.0D - a : a;
    }

    /**
     * Returns the greater of two {@code int} values. That is, the result is the argument closer to
     * the value of {@link Integer#MAX_VALUE}. If the arguments have the same value, the result is
     * that same value.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static int max(int a, int b) {
        return Math.max(a, b);
        // return (a >= b) ? a : b;
    }

    /**
     * Returns the greater of two {@code long} values. That is, the result is the argument closer to
     * the value of {@link Long#MAX_VALUE}. If the arguments have the same value, the result is that
     * same value.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static long max(long a, long b) {
        return Math.max(a, b);
        // return (a >= b) ? a : b;
    }

    private final static long negativeZeroFloatBits = Float.floatToIntBits(-0.0f), negativeZeroDoubleBits = Double.doubleToLongBits(-0.0d);

    /**
     * Returns the greater of two {@code float} values. That is, the result is the argument closer
     * to positive infinity. If the arguments have the same value, the result is that same value. If
     * either value is NaN, then the result is NaN. Unlike the numerical comparison operators, this
     * method considers negative zero to be strictly smaller than positive zero. If one argument is
     * positive zero and the other negative zero, the result is positive zero.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static float max(float a, float b) {
        return Math.max(a, b);
        /*****
         * if (a != a) { return a; // a is NaN } if ((a == 0.0f) && (b == 0.0f) &&
         * (Float.floatToIntBits(a) == negativeZeroFloatBits)) { return b; } return (a >= b) ? a :
         * b;
         *****/
    }

    /**
     * Returns the greater of two {@code double} values. That is, the result is the argument closer
     * to positive infinity. If the arguments have the same value, the result is that same value. If
     * either value is NaN, then the result is NaN. Unlike the numerical comparison operators, this
     * method considers negative zero to be strictly smaller than positive zero. If one argument is
     * positive zero and the other negative zero, the result is positive zero.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    public static double max(double a, double b) {
        return Math.max(a, b);
        /******
         * if (a != a) { return a; // a is NaN } if ((a == 0.0d) && (b == 0.0d) &&
         * (Double.doubleToLongBits(a) == negativeZeroDoubleBits)) { return b; } return (a >= b) ? a
         * : b;
         ******/
    }

    /**
     * Returns the smaller of two {@code int} values. That is, the result the argument closer to the
     * value of {@link Integer#MIN_VALUE}. If the arguments have the same value, the result is that
     * same value.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    public static int min(int a, int b) {
        return Math.min(a, b);
        // return (a <= b) ? a : b;
    }

    /**
     * Returns the smaller of two {@code long} values. That is, the result is the argument closer to
     * the value of {@link Long#MIN_VALUE}. If the arguments have the same value, the result is that
     * same value.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    public static long min(long a, long b) {
        return Math.min(a, b);
        // return (a <= b) ? a : b;
    }

    /**
     * Returns the smaller of two {@code float} values. That is, the result is the value closer to
     * negative infinity. If the arguments have the same value, the result is that same value. If
     * either value is NaN, then the result is NaN. Unlike the numerical comparison operators, this
     * method considers negative zero to be strictly smaller than positive zero. If one argument is
     * positive zero and the other is negative zero, the result is negative zero.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b.}
     */
    public static float min(float a, float b) {
        return Math.min(a, b);
        /*******
         * if (a != a) { return a; // a is NaN } if ((a == 0.0f) && (b == 0.0f) &&
         * (Float.floatToIntBits(b) == negativeZeroFloatBits)) { return b; } return (a <= b) ? a :
         * b;
         *****/
    }

    /**
     * Returns the smaller of two {@code double} values. That is, the result is the value closer to
     * negative infinity. If the arguments have the same value, the result is that same value. If
     * either value is NaN, then the result is NaN. Unlike the numerical comparison operators, this
     * method considers negative zero to be strictly smaller than positive zero. If one argument is
     * positive zero and the other is negative zero, the result is negative zero.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    public static double min(double a, double b) {
        return Math.min(a, b);
        /*********
         * if (a != a) { return a; // a is NaN } if ((a == 0.0d) && (b == 0.0d) &&
         * (Double.doubleToLongBits(b) == negativeZeroDoubleBits)) { return b; } return (a <= b) ? a
         * : b;
         *******/
    }

    /**
     * Returns the size of an ulp of the argument. An ulp of a {@code double} value is the positive
     * distance between this floating-point value and the {@code double} value next larger in
     * magnitude. Note that for non-NaN <i>x</i>, <code>ulp(-<i>x</i>) == ulp(<i>x</i>)</code>.
     *
     * <p>
     * Special Cases:
     * <ul>
     * <li>If the argument is NaN, then the result is NaN.
     * <li>If the argument is positive or negative infinity, then the result is positive infinity.
     * <li>If the argument is positive or negative zero, then the result is {@code Double.MIN_VALUE}.
     * <li>If the argument is &plusmn;{@code Double.MAX_VALUE}, then the result is equal to
     * 2<sup>971</sup>.
     * </ul>
     *
     * @param d the floating-point value whose ulp is to be returned
     * @return the size of an ulp of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    @SuppressWarnings("deprecation")
    public static double ulp(double d) {
        return sun.misc.FpUtils.ulp(d);
    }

    /**
     * Returns the size of an ulp of the argument. An ulp of a {@code float} value is the positive
     * distance between this floating-point value and the {@code float} value next larger in
     * magnitude. Note that for non-NaN <i>x</i>, <code>ulp(-<i>x</i>) == ulp(<i>x</i>)</code>.
     *
     * <p>
     * Special Cases:
     * <ul>
     * <li>If the argument is NaN, then the result is NaN.
     * <li>If the argument is positive or negative infinity, then the result is positive infinity.
     * <li>If the argument is positive or negative zero, then the result is {@code Float.MIN_VALUE}.
     * <li>If the argument is &plusmn;{@code Float.MAX_VALUE}, then the result is equal to
     * 2<sup>104</sup>.
     * </ul>
     *
     * @param f the floating-point value whose ulp is to be returned
     * @return the size of an ulp of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    @SuppressWarnings("deprecation")
    public static float ulp(float f) {
        return sun.misc.FpUtils.ulp(f);
    }

    /**
     * Returns the signum function of the argument; zero if the argument is zero, 1.0 if the
     * argument is greater than zero, -1.0 if the argument is less than zero.
     *
     * <p>
     * Special Cases:
     * <ul>
     * <li>If the argument is NaN, then the result is NaN.
     * <li>If the argument is positive zero or negative zero, then the result is the same as the
     * argument.
     * </ul>
     *
     * @param d the floating-point value whose signum is to be returned
     * @return the signum function of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    @SuppressWarnings("deprecation")
    public static double signum(double d) {
        return sun.misc.FpUtils.signum(d);
    }

    /**
     * Returns the signum function of the argument; zero if the argument is zero, 1.0f if the
     * argument is greater than zero, -1.0f if the argument is less than zero.
     *
     * <p>
     * Special Cases:
     * <ul>
     * <li>If the argument is NaN, then the result is NaN.
     * <li>If the argument is positive zero or negative zero, then the result is the same as the
     * argument.
     * </ul>
     *
     * @param f the floating-point value whose signum is to be returned
     * @return the signum function of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    @SuppressWarnings("deprecation")
    public static float signum(float f) {
        return sun.misc.FpUtils.signum(f);
    }

    private static final double shuge = 1.0e307;

    /**
     * Returns the hyperbolic sine of a {@code double} value. The hyperbolic sine of <i>x</i> is
     * defined to be (<i>e<sup>x</sup>&nbsp;-&nbsp;e<sup>-x</sup></i>)/2 where <i>e</i> is
     * {@linkplain Math#E Euler's number}.
     *
     * <p>
     * Special cases:
     * <ul>
     *
     * <li>If the argument is NaN, then the result is NaN.
     *
     * <li>If the argument is infinite, then the result is an infinity with the same sign as the
     * argument.
     *
     * <li>If the argument is zero, then the result is a zero with the same sign as the argument.
     *
     * </ul>
     *
     * @param x The number whose hyperbolic sine is to be returned.
     * @return The hyperbolic sine of {@code x}.
     * @since 1.5
     */
    public static double sinh(double x) {
        /*
         * __ieee754_sinh(x) Method : mathematically sinh(x) if defined to be (exp(x)-exp(-x))/2 1.
         * Replace x by |x| (sinh(-x) = -sinh(x)). 2. E + E/(E+1) 0 <= x <= 22 : sinh(x) :=
         * --------------, E=expm1(x) 2
         *
         * 22 <= x <= lnovft : sinh(x) := exp(x)/2 lnovft <= x <= ln2ovft: sinh(x) := exp(x/2)/2 *
         * exp(x/2) ln2ovft < x : sinh(x) := x*shuge (overflow)
         *
         * Special cases: sinh(x) is |x| if x is +INF, -INF, or NaN. only sinh(0)=0 is exact for
         * finite x.
         */

        final long xb = Double.doubleToLongBits(x);
        final double h = (xb >> 32) < 0 ? -0.5 : 0.5;
        final int ix = (int) ((xb >> 32) & 0x7fffffff);

        if (ix >= 0x7ff00000) { /* x is INF or NaN */
            return x + x;
        }

        /* |x| in [0,22], return sign(x)*0.5*(E+E/(E+1))) */
        if (ix < 0x40360000) { /* |x|<22 */
            if (ix < 0x3e300000) /* |x|<2**-28 */
                if (shuge + x > one) {
                    return x;/* sinh(tiny) = tiny with inexact */
                }
            final double t = expm1(abs(x));
            return (ix < 0x3ff00000) ? h * (2.0 * t - t * t / (t + one)) : h * (t + t / (t + one));
        }

        /* |x| in [22, log(maxdouble)] return 0.5*exp(|x|) */
        if (ix < 0x40862E42)
            return h * exp(abs(x));

        // |x| in [log(Double.MAX_VALUE), overflowthreshold]
        if (ix < 0x408633ce || (ix == 0x408633ce && (xb & 0x00000000ffffffffL) <= 0x8fb9f87dL)) {
            final double w = exp(0.5 * abs(x));
            return (h * w) * w;
        }
        /* |x| > overflowthresold, sinh(x) overflow */
        return x * shuge;

    }

    /**
     * Returns the hyperbolic cosine of a {@code double} value. The hyperbolic cosine of <i>x</i> is
     * defined to be (<i>e<sup>x</sup>&nbsp;+&nbsp;e<sup>-x</sup></i>)/2 where <i>e</i> is
     * {@linkplain Math#E Euler's number}.
     *
     * <p>
     * Special cases:
     * <ul>
     *
     * <li>If the argument is NaN, then the result is NaN.
     *
     * <li>If the argument is infinite, then the result is positive infinity.
     *
     * <li>If the argument is zero, then the result is {@code 1.0}.
     *
     * </ul>
     *
     * @param x The number whose hyperbolic cosine is to be returned.
     * @return The hyperbolic cosine of {@code x}.
     * @since 1.5
     */
    public static double cosh(double x) {
        /*
         * __ieee754_cosh(x) Method : mathematically cosh(x) if defined to be (exp(x)+exp(-x))/2 1.
         * Replace x by |x| (cosh(x) = cosh(-x)). 2. [ exp(x) - 1 ]^2 0 <= x <= ln2/2 : cosh(x) := 1
         * + ------------------- 2*exp(x)
         *
         * exp(x) + 1/exp(x) ln2/2 <= x <= 22 : cosh(x) := ------------------- 2 22 <= x <= lnovft :
         * cosh(x) := exp(x)/2 lnovft <= x <= ln2ovft: cosh(x) := exp(x/2)/2 * exp(x/2) ln2ovft < x
         * : cosh(x) := huge*huge (overflow)
         *
         * Special cases: cosh(x) is |x| if x is +INF, -INF, or NaN. only cosh(0)=1 is exact for
         * finite x.
         */

        final long xb = Double.doubleToRawLongBits(x);
        final int ix = ((int) (xb >> 32)) & 0x7fffffff;

        /* x is INF or NaN */
        if (ix >= 0x7ff00000) {
            return x * x;
        }

        /* |x| in [0,0.5*ln2], return 1+expm1(|x|)^2/(2*exp(|x|)) */
        if (ix < 0x3fd62e43) {
            final double t = expm1(abs(x));
            final double w = one + t;
            // for tiny arguments return 1.
            return (ix < 0x3c800000) ? w : one + (t * t) / (w + w);
        }

        /* |x| in [0.5*ln2,22], return (exp(|x|)+1/exp(|x|)/2; */
        if (ix < 0x40360000) {
            final double t = exp(abs(x));
            return half * t + half / t;
        }

        /* |x| in [22, log(maxdouble)] return half*exp(|x|) */
        if (ix < 0x40862e42) {
            return half * exp(abs(x));
        }

        /* |x| in [log(maxdouble), overflowthresold] */
        if (ix < 0x408633ce || (ix == 0x408633ce && (xb & 0x00000000ffffffffL) <= 0x8fb9f87dL)) {
            final double w = exp(half * abs(x));
            return (half * w) * w;
        }
        /* |x| > overflowthresold, cosh(x) overflow */
        return huge * huge;
    }

    /**
     * Returns the hyperbolic tangent of a {@code double} value. The hyperbolic tangent of <i>x</i>
     * is defined to be
     * (<i>e<sup>x</sup>&nbsp;-&nbsp;e<sup>-x</sup></i>)/(<i>e<sup>x</sup>&nbsp;+&nbsp
     * ;e<sup>-x</sup></i>), in other words, {@linkplain Math#sinh sinh(<i>x</i>)}/
     * {@linkplain Math#cosh cosh(<i>x</i>)}. Note that the absolute value of the exact tanh is
     * always less than 1.
     *
     * <p>
     * Special cases:
     * <ul>
     *
     * <li>If the argument is NaN, then the result is NaN.
     *
     * <li>If the argument is zero, then the result is a zero with the same sign as the argument.
     *
     * <li>If the argument is positive infinity, then the result is {@code +1.0}.
     *
     * <li>If the argument is negative infinity, then the result is {@code -1.0}.
     *
     * </ul>
     *
     * @param xx The number whose hyperbolic tangent is to be returned.
     * @return The hyperbolic tangent of {@code x}.
     * @since 1.5
     */
    public static double tanh(double xx) {
        /*
         * Tanh(x) Return the Hyperbolic Tangent of x
         *
         * Method : x -x e - e 0. tanh(x) is defined to be ----------- x -x e + e 1. reduce x to
         * non-negative by tanh(-x) = -tanh(x). 2. 0 <= x <= 2**-55 : tanh(x) := x*(one+x) -t 2**-55
         * < x <= 1 : tanh(x) := -----; t = expm1(-2x) t + 2 2 1 <= x <= 22.0 : tanh(x) := 1- -----
         * ; t=expm1(2x) t + 2 22.0 < x <= INF : tanh(x) := 1.
         *
         * Special cases: tanh(NaN) is NaN; only tanh(0)=0 is exact for finite argument.
         */

        double x = xx;
        /* High word of |x|. */
        final int hx = (int) (Double.doubleToRawLongBits(x) >> 32);
        final int ix = hx & 0x7fffffff;

        /* x is INF or NaN */
        if (ix >= 0x7ff00000) {
            return hx >= 0 ? one / x + one : /* tanh(+-inf)=+-1 */
            one / x - one; /* tanh(NaN) = NaN */
        }

        double z;
        /* |x| < 22 */
        if (ix < 0x40360000) { /* |x|<22 */
            if (ix < 0x3c800000) { /* |x|<2**-55 */
                return x * (one + x); /* tanh(small) = small */
            }
            x = abs(x);
            if (ix >= 0x3ff00000) { /* |x|>=1 */
                z = one - two / (expm1(2.0d * x) + two);
            } else {
                double t = expm1(-two * x);
                z = -t / (t + two);
            }
            /* |x| > 22, return +-1 */
        } else {
            z = one - tiny; /* raised inexact flag */
        }
        return hx >= 0 ? z : 0.0d - z;
    }

    private static final double TWOpow1022 = setHigh32bits(0x7fd00000);
    private static final long onebits = Double.doubleToLongBits(one), clearHighmask = 0x00000000FFFFFFFFL, onebitshigh = onebits & clearHighmask, zerobitshigh = Double.doubleToLongBits(zero) &
                    clearHighmask;

    /**
     * Returns sqrt(<i>x</i><sup>2</sup>&nbsp;+<i>y</i><sup>2</sup>) without intermediate overflow
     * or underflow.
     *
     * <p>
     * Special cases:
     * <ul>
     *
     * <li>If either argument is infinite, then the result is positive infinity.
     *
     * <li>If either argument is NaN and neither argument is infinite, then the result is NaN.
     *
     * </ul>
     *
     * @param aa a value
     * @param bb a value
     * @return sqrt(<i>x</i><sup>2</sup>&nbsp;+<i>y</i><sup>2</sup>) without intermediate overflow
     *         or underflow
     * @since 1.5
     */
    public static double hypot(double aa, double bb) {
        /*
         * __ieee754_hypot(x,y)
         *
         * Method : If (assume round-to-nearest) z=x*x+y*y has error less than sqrt(2)/2 ulp, than
         * sqrt(z) has error less than 1 ulp (exercise).
         *
         * So, compute sqrt(x*x+y*y) with some care as follows to get the error below 1 ulp:
         *
         * Assume x>y>0; (if possible, set rounding to round-to-nearest) 1. if x > 2y use
         * x1*x1+(y*y+(x2*(x+x1))) for x*x+y*y where x1 = x with lower 32 bits cleared, x2 = x-x1;
         * else 2. if x <= 2y use t1*y1+((x-y)*(x-y)+(t1*y2+t2*y)) where t1 = 2x with lower 32 bits
         * cleared, t2 = 2x-t1, y1= y with lower 32 bits chopped, y2 = y-y1.
         *
         * NOTE: scaling may be necessary if some argument is too large or too tiny
         *
         * Special cases: hypot(x,y) is INF if x or y is +INF or -INF; else hypot(x,y) is NAN if x
         * or y is NAN.
         *
         * Accuracy: hypot(x,y) returns sqrt(x^2+y^2) with error less than 1 ulps (units in the last
         * place)
         */

        double a = aa;
        double b = bb;
        long hx = (Double.doubleToRawLongBits(a) >>> 32) & 0x7fffffff;
        long hy = (Double.doubleToRawLongBits(b) >>> 32) & 0x7fffffff;
        if (hy > hx) {
            double at = a;
            a = b;
            b = at;
            long j = hx;
            hx = hy;
            hy = j;
        }

        a = abs(a);
        b = abs(b);

        // a = setHigh32bits(a,hx);
        // b = setHigh32bits(b,hy);

        // System.err.println("a1 ");
        if (hx - hy > 0x3c00000) {
            return a + b;
        } /* x/y > 2**60 */
        // System.err.println("a2");
        int k = 0;
        if (hx > 0x5f300000) { /* a>2**500 */
            // System.err.println("b1 "+hx +" >= "+ 0x7ff00000);
            if (hx >= 0x7ff00000) { /* Inf or NaN */
                // System.err.println("b2");
                return Double.isInfinite(a) || Double.isInfinite(b) ? Double.POSITIVE_INFINITY : Double.NaN;
            }
            /* scale a and b by 2**-600 */
            k = 600;
            a = setHigh32bits(a, hx -= 0x25800000);
            b = setHigh32bits(b, hy -= 0x25800000);
        }
        if (hy < 0x20b00000) { /* b < 2**-500 */
            if (hy <= 0x000fffff) { /* subnormal b or 0 */
                // if ((hy | (bbits&clearHighmask)) == 0) {
                if ((hy | __LO(b)) == 0) {
                    return a;
                }
                b *= TWOpow1022;
                a *= TWOpow1022;
                k -= 1022;
            } else { /* scale a and b by 2^600 */
                k -= 600;
                a = setHigh32bits(a, hx += 0x25800000); /* a *= 2^600 */
                b = setHigh32bits(b, hy += 0x25800000); /* b *= 2^600 */
            }
        }

        /* medium size a and b */
        double w = a - b;
        if (w > b) {
            final double t1 = Double.longBitsToDouble(hx << 32);
            w = t1 * t1 - (b * -b - (a - t1) * (a + t1));
        } else {
            final double y1 = Double.longBitsToDouble(hy << 32);
            final double t1 = Double.longBitsToDouble((hx + 0x00100000) << 32);
            w = t1 * y1 - (w * -w - (t1 * (b - y1) + b * ((a + a) - t1)));
        }
        w = java.lang.Math.sqrt(w);
        return k != 0 ? w * setHhighbitsAddSome(onebits, (long) k << (20 + 32)) : w;
    }

    /* scaled coefficients related to expm1 */
    private static final double Q1 = -3.33333333333331316428e-02, /* BFA11111 111110F4 */
    Q2 = 1.58730158725481460165e-03, /* 3F5A01A0 19FE5585 */
    Q3 = -7.93650757867487942473e-05, /* BF14CE19 9EAADBB7 */
    Q4 = 4.00821782732936239552e-06, /* 3ED0CFCA 86E65239 */
    Q5 = -2.01099218183624371326e-07; /* BE8AFDB7 6E09C32D */

    /**
     * Returns <i>e</i><sup>x</sup>&nbsp;-1. Note that for values of <i>x</i> near 0, the exact sum
     * of {@code expm1(x)}&nbsp;+&nbsp;1 is much closer to the true result of <i>e</i><sup>x</sup>
     * than {@code exp(x)}.
     *
     * <p>
     * Special cases:
     * <ul>
     * <li>If the argument is NaN, the result is NaN.
     *
     * <li>If the argument is positive infinity, then the result is positive infinity.
     *
     * <li>If the argument is negative infinity, then the result is -1.0.
     *
     * <li>If the argument is zero, then the result is a zero with the same sign as the argument.
     *
     * </ul>
     *
     * @param xx the exponent to raise <i>e</i> to in the computation of <i>e</i><sup>{@code x}
     *            </sup>&nbsp;-1.
     * @return the value <i>e</i><sup>{@code x}</sup>&nbsp;-&nbsp;1.
     * @since 1.5
     */
    public static double expm1(double xx) {
        /*
         * expm1(x) Returns exp(x)-1, the exponential of x minus 1.
         *
         * Method 1. Argument reduction: Given x, find r and integer k such that
         *
         * x = k*ln2 + r, |r| <= 0.5*ln2 ~ 0.34658
         *
         * Here a correction term c will be computed to compensate the error in r when rounded to a
         * floating-point number.
         *
         * 2. Approximating expm1(r) by a special rational function on the interval [0,0.34658]:
         * Since r*(exp(r)+1)/(exp(r)-1) = 2+ r^2/6 - r^4/360 + ... we define R1(r*r) by
         * r*(exp(r)+1)/(exp(r)-1) = 2+ r^2/6 * R1(r*r) That is, R1(r**2) = 6/r
         * *((exp(r)+1)/(exp(r)-1) - 2/r) = 6/r * ( 1 + 2.0*(1/(exp(r)-1) - 1/r)) = 1 - r^2/60 +
         * r^4/2520 - r^6/100800 + ... We use a special Reme algorithm on [0,0.347] to generate a
         * polynomial of degree 5 in r*r to approximate R1. The maximum error of this polynomial
         * approximation is bounded by 2**-61. In other words, R1(z) ~ 1.0 + Q1*z + Q2*z**2 +
         * Q3*z**3 + Q4*z**4 + Q5*z**5 where Q1 = -1.6666666666666567384E-2, Q2 =
         * 3.9682539681370365873E-4, Q3 = -9.9206344733435987357E-6, Q4 = 2.5051361420808517002E-7,
         * Q5 = -6.2843505682382617102E-9; (where z=r*r, and the values of Q1 to Q5 are listed
         * below) with error bounded by | 5 | -61 | 1.0+Q1*z+...+Q5*z - R1(z) | <= 2 | |
         *
         * expm1(r) = exp(r)-1 is then computed by the following specific way which minimize the
         * accumulation rounding error: 2 3 r r [ 3 - (R1 + R1*r/2) ] expm1(r) = r + --- + --- *
         * [--------------------] 2 2 [ 6 - r*(3 - R1*r/2) ]
         *
         * To compensate the error in the argument reduction, we use expm1(r+c) = expm1(r) + c +
         * expm1(r)*c ~ expm1(r) + c + r*c Thus c+r*c will be added in as the correction terms for
         * expm1(r+c). Now rearrange the term to avoid optimization screw up: ( 2 2 ) ({ ( r [ R1 -
         * (3 - R1*r/2) ] ) } r ) expm1(r+c)~r - ({r*(--- * [--------------------]-c)-c} - --- ) ({
         * ( 2 [ 6 - r*(3 - R1*r/2) ] ) } 2 ) ( )
         *
         * = r - E 3. Scale back to obtain expm1(x): From step 1, we have expm1(x) = either
         * 2^k*[expm1(r)+1] - 1 = or 2^k*[expm1(r) + (1-2^-k)] 4. Implementation notes: (A). To save
         * one multiplication, we scale the coefficient Qi to Qi*2^i, and replace z by (x^2)/2. (B).
         * To achieve maximum accuracy, we compute expm1(x) by (i) if x < -56*ln2, return -1.0,
         * (raise inexact if x!=inf) (ii) if k=0, return r-E (iii) if k=-1, return 0.5*(r-E)-0.5
         * (iv) if k=1 if r < -0.25, return 2*((r+0.5)- E) else return 1.0+2.0*(r-E); (v) if
         * (k<-2||k>56) return 2^k(1-(E-r)) - 1 (or exp(x)-1) (vi) if k <= 20, return
         * 2^k((1-2^-k)-(E-r)), else (vii) return 2^k(1-((E+2^-k)-r))
         *
         * Special cases: expm1(INF) is INF, expm1(NaN) is NaN; expm1(-INF) is -1, and for finite
         * argument, only expm1(0)=0 is exact.
         *
         * Accuracy: according to an error analysis, the error is always less than 1 ulp (unit in
         * the last place).
         *
         * Misc. info. For IEEE double if x > 7.09782712893383973096e+02 then expm1(x) overflow
         *
         * Constants: The hexadecimal values are the intended ones for the following constants. The
         * decimal values may be used, provided that the compiler will convert from decimal to
         * binary accurately enough to produce the hexadecimal values shown.
         */

        double x = xx;
        long xb = Double.doubleToRawLongBits(x);
        int hx = (int) (xb >> 32); /* high word of x UNSIGNED */
        final boolean xsbzero = (hx & 0x80000000) == 0; /* sign bit of x */
        // double y = xsbzero ? x : 0.0d - x; /* y = |x| */
        hx &= 0x7fffffff; /* high word of |x| */

        /* filter out huge and non-finite argument */
        if (hx >= 0x4043687A) { /* if |x|>=56*ln2 */
            if (hx >= 0x40862E42) { /* if |x|>=709.78... */
                if (hx >= 0x7ff00000) {
                    return (((hx & 0xfffff) | (int) xb) != 0) ? x + x : /* NaN */
                    xsbzero ? x : -1.0; /* exp(+-inf)={inf,-1} */
                }
                if (x > o_threshold) {
                    return huge * huge; /* overflow */
                }
            }
            /* x < -56*ln2, return -1.0 with inexact */
            if (!xsbzero && (x + tiny < 0.0)) { /* raise inexact */
                return tiny - one; /* return -1 */
            }
        }

        double hi, lo, c = 0;
        int k = 0;
        /* argument reduction */
        if (hx > 0x3fd62e42) { /* if |x| > 0.5 ln2 */
            if (hx < 0x3FF0A2B2) { /* and |x| < 1.5 ln2 */
                if (xsbzero) {
                    hi = x - ln2_hi;
                    lo = ln2_lo;
                    k = 1;
                } else {
                    hi = x + ln2_hi;
                    lo = -ln2_lo;
                    k = -1;
                }
            } else {
                // loss of precision here ?:
                k = (int) (invln2 * x + (xsbzero ? 0.5 : -0.5));
                hi = x - k * ln2_hi; /* t*ln2_hi is exact here */
                lo = k * ln2_lo;
            }
            x = hi - lo;
            c = (hi - x) - lo;
        } else if (hx < 0x3c900000) { /* when |x|<2**-54, return x */
            /* return x with inexact flags when x!=0 */
            return x - ((huge + x) - (huge + x));
        }

        /* x is now in primary range */
        final double hfx = 0.5 * x;
        final double hxs = x * hfx;
        final double r1 = one + hxs * (Q1 + hxs * (Q2 + hxs * (Q3 + hxs * (Q4 + hxs * Q5))));
        final double t = 3.0 - r1 * hfx;
        double e = hxs * ((r1 - t) / (6.0 - x * t));
        if (k == 0) {
            return x - (x * e - hxs); /* c is 0 */
        }
        e = (x * (e - c) - c) - hxs;
        if (k == -1) {
            return 0.5 * (x - e) - 0.5;
        }
        if (k == 1) {
            return x < -0.25 ? -2.0 * (e - (x + 0.5)) : one + 2.0 * (x - e);
        }
        if (k <= -2 || k > 56) { /* suffice to return exp(x)-1 */
            /* add k to y's exponent */
            return setHhighbitsAddSome(one - (e - x), (long) k << (20 + 32)) - one;
        }
        final double y = k < 20 ? /* 1-2^-k */
        setHigh32bitsDontMask(onebitshigh, 0x3ff00000 - (0x200000 >> k)) - (e - x) : (x - (e + setHigh32bitsDontMask(onebitshigh, (0x3ff - k) << 20))) + one;// 2^-k
        /* add k to y's exponent */
        return setHhighbitsAddSome(y, (long) k << (20 + 32));
    }

    private static final double Lp1 = 6.666666666666735130e-01, /* 3FE55555 55555593 */
    Lp2 = 3.999999999940941908e-01, /* 3FD99999 9997FA04 */
    Lp3 = 2.857142874366239149e-01, /* 3FD24924 94229359 */
    Lp4 = 2.222219843214978396e-01, /* 3FCC71C5 1D8E78AF */
    Lp5 = 1.818357216161805012e-01, /* 3FC74664 96CB03DE */
    Lp6 = 1.531383769920937332e-01, /* 3FC39A09 D078C69F */
    Lp7 = 1.479819860511658591e-01; /* 3FC2F112 DF3E5244 */

    /**
     * Returns the natural logarithm of the sum of the argument and 1. Note that for small values
     * {@code x}, the result of {@code log1p(x)} is much closer to the true result of ln(1 +
     * {@code x}) than the floating-point evaluation of {@code log(1.0+x)}.
     *
     * <p>
     * Special cases:
     * <ul>
     *
     * <li>If the argument is NaN or less than -1, then the result is NaN.
     *
     * <li>If the argument is positive infinity, then the result is positive infinity.
     *
     * <li>If the argument is negative one, then the result is negative infinity.
     *
     * <li>If the argument is zero, then the result is a zero with the same sign as the argument.
     *
     * </ul>
     *
     * @param x a value
     * @return the value ln({@code x}&nbsp;+&nbsp;1), the natural log of {@code x}&nbsp;+&nbsp;1
     * @since 1.5
     */
    @SuppressWarnings("cast")
    public static double log1p(double x) {
        /*
         * double log1p(double x)
         *
         * Method : 1. Argument Reduction: find k and f such that 1+x = 2^k * (1+f), where sqrt(2)/2
         * < 1+f < sqrt(2) .
         *
         * Note. If k=0, then f=x is exact. However, if k!=0, then f may not be representable
         * exactly. In that case, a correction term is need. Let u=1+x rounded. Let c = (1+x)-u,
         * then log(1+x) - log(u) ~ c/u. Thus, we proceed to compute log(u), and add back the
         * correction term c/u. (Note: when x > 2**53, one can simply return log(x))
         *
         * 2. Approximation of log1p(f). Let s = f/(2+f) ; based on log(1+f) = log(1+s) - log(1-s) =
         * 2s + 2/3 s**3 + 2/5 s**5 + ....., = 2s + s*R We use a special Reme algorithm on
         * [0,0.1716] to generate a polynomial of degree 14 to approximate R The maximum error of
         * this polynomial approximation is bounded by 2**-58.45. In other words, 2 4 6 8 10 12 14
         * R(z) ~ Lp1*s +Lp2*s +Lp3*s +Lp4*s +Lp5*s +Lp6*s +Lp7*s (the values of Lp1 to Lp7 are
         * listed in the program) and | 2 14 | -58.45 | Lp1*s +...+Lp7*s - R(z) | <= 2 | | Note that
         * 2s = f - s*f = f - hfsq + s*hfsq, where hfsq = f*f/2. In order to guarantee error in log
         * below 1ulp, we compute log by log1p(f) = f - (hfsq - s*(hfsq+R)).
         *
         * 3. Finally, log1p(x) = k*ln2 + log1p(f). = k*ln2_hi+(f-(hfsq-(s*(hfsq+R)+k*ln2_lo))) Here
         * ln2 is split into two floating point number: ln2_hi + ln2_lo, where n*ln2_hi is always
         * exact for |n| < 2000.
         *
         * Special cases: log1p(x) is NaN with signal if x < -1 (including -INF) ; log1p(+INF) is
         * +INF; log1p(-1) is -INF with signal; log1p(NaN) is that NaN with no signal.
         *
         * Accuracy: according to an error analysis, the error is always less than 1 ulp (unit in
         * the last place).
         *
         * Constants: The hexadecimal values are the intended ones for the following constants. The
         * decimal values may be used, provided that the compiler will convert from decimal to
         * binary accurately enough to produce the hexadecimal values shown.
         *
         * Note: Assuming log() return accurate answer, the following algorithm can be used to
         * compute log1p(x) to within a few ULP:
         *
         * u = 1+x; if(u==1.0) return x ; else return log(u)*(x/(u-1.0));
         *
         * See HP-15C Advanced Functions Handbook, p.193.
         */
        double f = 0;
        int k = 1, hu = 0;
        final int hx = __HI(x); /* high word of x */
        final int ax = hx & 0x7fffffff;
        if (hx < 0x3FDA827A) { /* x < 0.41422 */
            if (ax >= 0x3ff00000) { /* x <= -1.0 */
                return x == -1.0d ? -two54 / zero : /* log1p(-1)=-inf */
                (x - x) / (x - x); /* log1p(x<-1)=NaN */
            }
            if (ax < 0x3e200000) { /* |x| < 2**-29 */
                if (two54 + x > zero /* raise inexact */
                                && ax < 0x3c900000) /* |x| < 2**-54 */{
                    return x;
                }
                return x - x * x * 0.5;
            }
            if (hx > 0 || hx <= ((int) 0xbfd2bec3)) { /* -0.2929<x<0.41422 */
                k = 0;
                f = x;
                hu = 1;
            }
        }

        if (hx >= 0x7ff00000) {
            return x + x;
        }
        double c;
        if (k != 0) {
            final double u = hx < 0x43400000 ? 1.0 + x : x;
            long ub = Double.doubleToRawLongBits(u);
            hu = (int) (ub >> 32); /* high word of u */
            k = (hu >> 20) - 1023;
            c = hx < 0x43400000 ? (k > 0 ? 1.0 - (u - x) : x - (u - 1.0)) / u : 0;
            int newhu;
            if ((hu &= 0x000fffff) < 0x6a09e) {
                newhu = hu | 0x3ff00000; /* normalize u */
            } else {
                k += 1;
                newhu = hu | 0x3fe00000; /* normalize u/2 */
                hu = (0x00100000 - hu) >> 2;
            }
            f = setHigh32bits(ub, newhu) - 1.0;
        } else
            c = 0;

        double hfsq = 0.5 * f * f;
        if (hu == 0) { /* |f| < 2**-20 */
            if (f == zero) {
                return k == 0 ? zero : k * ln2_hi + (c + k * ln2_lo);
            }
            final double R = hfsq * (1.0 - 0.66666666666666666 * f);
            return k == 0 ? f - R : k * ln2_hi - ((R - (k * ln2_lo + c)) - f);
        }
        final double s = f / (2.0 + f);
        final double z = s * s;
        final double R = s * (hfsq + z * (Lp1 + z * (Lp2 + z * (Lp3 + z * (Lp4 + z * (Lp5 + z * (Lp6 + z * Lp7)))))));
        return k == 0 ? f - (hfsq - R) : k * ln2_hi - ((hfsq - (R + (k * ln2_lo + c))) - f);
    }

    /**
     * Returns the first floating-point argument with the sign of the second floating-point
     * argument. For this method, a NaN {@code sign} argument is always treated as if it were
     * positive.
     *
     * @param magnitude the parameter providing the magnitude of the result
     * @param sign the parameter providing the sign of the result
     * @return a value with the magnitude of {@code magnitude} and the sign of {@code sign}.
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static double copySign(double magnitude, double sign) {
        return sun.misc.FpUtils.copySign(magnitude, sign);
    }

    /**
     * Returns the first floating-point argument with the sign of the second floating-point
     * argument. For this method, a NaN {@code sign} argument is always treated as if it were
     * positive.
     *
     * @param magnitude the parameter providing the magnitude of the result
     * @param sign the parameter providing the sign of the result
     * @return a value with the magnitude of {@code magnitude} and the sign of {@code sign}.
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static float copySign(float magnitude, float sign) {
        return sun.misc.FpUtils.copySign(magnitude, sign);
    }

    /**
     * Returns the unbiased exponent used in the representation of a {@code float}. Special cases:
     *
     * <ul>
     * <li>If the argument is NaN or infinite, then the result is {@link Float#MAX_EXPONENT} + 1.
     * <li>If the argument is zero or subnormal, then the result is {@link Float#MIN_EXPONENT} -1.
     * </ul>
     *
     * @param f a {@code float} value
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static int getExponent(float f) {
        return sun.misc.FpUtils.getExponent(f);
    }

    /**
     * Returns the unbiased exponent used in the representation of a {@code double}. Special cases:
     *
     * <ul>
     * <li>If the argument is NaN or infinite, then the result is {@link Double#MAX_EXPONENT} + 1.
     * <li>If the argument is zero or subnormal, then the result is {@link Double#MIN_EXPONENT} -1.
     * </ul>
     *
     * @param d a {@code double} value
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static int getExponent(double d) {
        return sun.misc.FpUtils.getExponent(d);
    }

    /**
     * Returns the floating-point number adjacent to the first argument in the direction of the
     * second argument. If both arguments compare as equal the second argument is returned.
     *
     * <p>
     * Special cases:
     * <ul>
     * <li>If either argument is a NaN, then NaN is returned.
     *
     * <li>If both arguments are signed zeros, {@code direction} is returned unchanged (as implied
     * by the requirement of returning the second argument if the arguments compare as equal).
     *
     * <li>If {@code start} is &plusmn;{@link Double#MIN_VALUE} and {@code direction} has a value
     * such that the result should have a smaller magnitude, then a zero with the same sign as
     * {@code start} is returned.
     *
     * <li>If {@code start} is infinite and {@code direction} has a value such that the result
     * should have a smaller magnitude, {@link Double#MAX_VALUE} with the same sign as {@code start}
     * is returned.
     *
     * <li>If {@code start} is equal to &plusmn; {@link Double#MAX_VALUE} and {@code direction} has
     * a value such that the result should have a larger magnitude, an infinity with same sign as
     * {@code start} is returned.
     * </ul>
     *
     * @param start starting floating-point value
     * @param direction value indicating which of {@code start}'s neighbors or {@code start} should
     *            be returned
     * @return The floating-point number adjacent to {@code start} in the direction of
     *         {@code direction}.
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static double nextAfter(double start, double direction) {
        return sun.misc.FpUtils.nextAfter(start, direction);
    }

    /**
     * Returns the floating-point number adjacent to the first argument in the direction of the
     * second argument. If both arguments compare as equal a value equivalent to the second argument
     * is returned.
     *
     * <p>
     * Special cases:
     * <ul>
     * <li>If either argument is a NaN, then NaN is returned.
     *
     * <li>If both arguments are signed zeros, a value equivalent to {@code direction} is returned.
     *
     * <li>If {@code start} is &plusmn;{@link Float#MIN_VALUE} and {@code direction} has a value
     * such that the result should have a smaller magnitude, then a zero with the same sign as
     * {@code start} is returned.
     *
     * <li>If {@code start} is infinite and {@code direction} has a value such that the result
     * should have a smaller magnitude, {@link Float#MAX_VALUE} with the same sign as {@code start}
     * is returned.
     *
     * <li>If {@code start} is equal to &plusmn; {@link Float#MAX_VALUE} and {@code direction} has a
     * value such that the result should have a larger magnitude, an infinity with same sign as
     * {@code start} is returned.
     * </ul>
     *
     * @param start starting floating-point value
     * @param direction value indicating which of {@code start}'s neighbors or {@code start} should
     *            be returned
     * @return The floating-point number adjacent to {@code start} in the direction of
     *         {@code direction}.
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static float nextAfter(float start, double direction) {
        return sun.misc.FpUtils.nextAfter(start, direction);
    }

    /**
     * Returns the floating-point value adjacent to {@code d} in the direction of positive infinity.
     * This method is semantically equivalent to {@code nextAfter(d,
     * Double.POSITIVE_INFINITY)}; however, a {@code nextUp} implementation may run faster than its
     * equivalent {@code nextAfter} call.
     *
     * <p>
     * Special Cases:
     * <ul>
     * <li>If the argument is NaN, the result is NaN.
     *
     * <li>If the argument is positive infinity, the result is positive infinity.
     *
     * <li>If the argument is zero, the result is {@link Double#MIN_VALUE}
     *
     * </ul>
     *
     * @param d starting floating-point value
     * @return The adjacent floating-point value closer to positive infinity.
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static double nextUp(double d) {
        return sun.misc.FpUtils.nextUp(d);
    }

    /**
     * Returns the floating-point value adjacent to {@code f} in the direction of positive infinity.
     * This method is semantically equivalent to {@code nextAfter(f,
     * Float.POSITIVE_INFINITY)}; however, a {@code nextUp} implementation may run faster than its
     * equivalent {@code nextAfter} call.
     *
     * <p>
     * Special Cases:
     * <ul>
     * <li>If the argument is NaN, the result is NaN.
     *
     * <li>If the argument is positive infinity, the result is positive infinity.
     *
     * <li>If the argument is zero, the result is {@link Float#MIN_VALUE}
     *
     * </ul>
     *
     * @param f starting floating-point value
     * @return The adjacent floating-point value closer to positive infinity.
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static float nextUp(float f) {
        return sun.misc.FpUtils.nextUp(f);
    }

    /**
     * Return {@code d} &times; 2<sup>{@code scaleFactor}</sup> rounded as if performed by a single
     * correctly rounded floating-point multiply to a member of the double value set. See the Java
     * Language Specification for a discussion of floating-point value sets. If the exponent of the
     * result is between {@link Double#MIN_EXPONENT} and {@link Double#MAX_EXPONENT}, the answer is
     * calculated exactly. If the exponent of the result would be larger than
     * {@code Double.MAX_EXPONENT}, an infinity is returned. Note that if the result is subnormal,
     * precision may be lost; that is, when {@code scalb(x, n)} is subnormal,
     * {@code scalb(scalb(x, n), -n)} may not equal <i>x</i>. When the result is non-NaN, the result
     * has the same sign as {@code d}.
     *
     * <p>
     * Special cases:
     * <ul>
     * <li>If the first argument is NaN, NaN is returned.
     * <li>If the first argument is infinite, then an infinity of the same sign is returned.
     * <li>If the first argument is zero, then a zero of the same sign is returned.
     * </ul>
     *
     * @param d number to be scaled by a power of two.
     * @param scaleFactor power of 2 used to scale {@code d}
     * @return {@code d} &times; 2<sup>{@code scaleFactor}</sup>
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static double scalb(double d, int scaleFactor) {
        return sun.misc.FpUtils.scalb(d, scaleFactor);
    }

    /**
     * Return {@code f} &times; 2<sup>{@code scaleFactor}</sup> rounded as if performed by a single
     * correctly rounded floating-point multiply to a member of the float value set. See the Java
     * Language Specification for a discussion of floating-point value sets. If the exponent of the
     * result is between {@link Float#MIN_EXPONENT} and {@link Float#MAX_EXPONENT}, the answer is
     * calculated exactly. If the exponent of the result would be larger than
     * {@code Float.MAX_EXPONENT}, an infinity is returned. Note that if the result is subnormal,
     * precision may be lost; that is, when {@code scalb(x, n)} is subnormal,
     * {@code scalb(scalb(x, n), -n)} may not equal <i>x</i>. When the result is non-NaN, the result
     * has the same sign as {@code f}.
     *
     * <p>
     * Special cases:
     * <ul>
     * <li>If the first argument is NaN, NaN is returned.
     * <li>If the first argument is infinite, then an infinity of the same sign is returned.
     * <li>If the first argument is zero, then a zero of the same sign is returned.
     * </ul>
     *
     * @param f number to be scaled by a power of two.
     * @param scaleFactor power of 2 used to scale {@code f}
     * @return {@code f} &times; 2<sup>{@code scaleFactor}</sup>
     * @since 1.6
     */
    @SuppressWarnings("deprecation")
    public static float scalb(float f, int scaleFactor) {
        return sun.misc.FpUtils.scalb(f, scaleFactor);
    }

    public static double setHhighbitsAddSome(double x, long highadd) {
        return setHhighbitsAddSome(Double.doubleToRawLongBits(x), highadd);
    }

    public static double setHhighbitsORSome(double x, long highOR) {
        return Double.longBitsToDouble(Double.doubleToRawLongBits(x) | (highOR << 32));
    }

    public static double setHhighbitsAddSome(long xl, long highadd) {
        return Double.longBitsToDouble((xl & clearHighmask) | (xl + highadd));
    }

    private static int __HI(double x) {
        return (int) (Double.doubleToRawLongBits(x) >> 32);
    }

    private static int __LO(double x) {
        return (int) Double.doubleToRawLongBits(x);
    }

    private static double clearLow32bits(double x) {
        return Double.longBitsToDouble(Double.doubleToRawLongBits(x) & 0xFFFFFFFF00000000L);
    }

    private static double setHigh32bits(double x, long newhigh) {
        return setHigh32bits(Double.doubleToRawLongBits(x), newhigh);
    }

    private static double setHigh32bitsXOR(double x, long toXor) {
        return Double.longBitsToDouble((Double.doubleToRawLongBits(x)) ^ toXor);
    }

    private static double addToHighBits(double x, long toadd) {
        return addToHighBits(Double.doubleToRawLongBits(x), toadd);
    }

    private static double addToHighBits(long x, long toadd) {
        return Double.longBitsToDouble((x & clearHighmask) | (((x >> 32) + toadd) << 32));
    }

    private static double setHigh32bits(long x, long newhigh) {
        return Double.longBitsToDouble((x & clearHighmask) | (newhigh << 32));
    }

    private static double setHigh32bitsDontMask(long x, long newhigh) {
        return Double.longBitsToDouble(x | (newhigh << 32));
    }

    private static double setHigh32bits(long newhigh) {
        return Double.longBitsToDouble((newhigh << 32));
    }

    private static double ldexp(double x, int exp) {
        double r = x;
        // set exponent of result to exp
        long ir = Double.doubleToRawLongBits(r);
        long oldexp = Math.getExponent(r);
        long newexpbits = (oldexp + exp + DoubleConsts.EXP_BIAS) << (DoubleConsts.SIGNIFICAND_WIDTH - 1);
        // clear exp bits to zero
        ir &= ~DoubleConsts.EXP_BIT_MASK;
        ir |= newexpbits;
        return Double.longBitsToDouble(ir);
    }

    @SuppressWarnings("deprecation")
    private static double fmod(double x, double y) {
        int ir, iy;
        double r, w;

        if ((y == 0.0) || FpUtils.isNaN(y) || !FpUtils.isFinite(x)) {
            return (x * y) / (x * y);
        }

        r = Math.abs(x);
        double absy = Math.abs(y);
        iy = getExponent(absy);
        while (r >= absy) {
            ir = getExponent(r);
            w = ldexp(absy, ir - iy);
            r -= (w <= r ? w : w * 0.5);
        }
        return (x >= 0.0 ? r : -r);
    }

}