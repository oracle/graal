/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

/**
 * These substitutions are just for performance. Directly uses the optimized host intrinsics
 * avoiding expensive guest native calls.
 */
@EspressoSubstitutions
public final class Target_java_lang_Math {

    @Substitution(isTrivial = true)
    public static double sin(double a) {
        return Math.sin(a);
    }

    @Substitution(isTrivial = true)
    public static double cos(double a) {
        return Math.cos(a);
    }

    @Substitution(isTrivial = true)
    public static double tan(double a) {
        return Math.tan(a);
    }

    @Substitution(isTrivial = true)
    public static double asin(double a) {
        return Math.asin(a);
    }

    @Substitution(isTrivial = true)
    public static double acos(double a) {
        return Math.acos(a);
    }

    @Substitution(isTrivial = true)
    public static double atan(double a) {
        return Math.atan(a);
    }

    @Substitution(isTrivial = true)
    public static double exp(double a) {
        return Math.exp(a);
    }

    @Substitution(isTrivial = true)
    public static double log(double a) {
        return Math.log(a);
    }

    @Substitution(isTrivial = true)
    public static double log10(double a) {
        return Math.log10(a);
    }

    @Substitution(isTrivial = true)
    public static double sqrt(double a) {
        return Math.sqrt(a);
    }

    @Substitution(isTrivial = true)
    public static double cbrt(double a) {
        return Math.cbrt(a);
    }

    // Checkstyle: stop method name check
    @Substitution(isTrivial = true)
    public static double IEEEremainder(double f1, double f2) {
        return Math.IEEEremainder(f1, f2);
    }
    // Checkstyle: resume method name check

    @Substitution(isTrivial = true)
    public static double ceil(double a) {
        return Math.ceil(a);
    }

    @Substitution(isTrivial = true)
    public static double floor(double a) {
        return Math.floor(a);
    }

    @Substitution(isTrivial = true)
    public static double rint(double a) {
        return Math.rint(a);
    }

    @Substitution(isTrivial = true)
    public static double atan2(double y, double x) {
        return Math.atan2(y, x);
    }

    @Substitution(isTrivial = true)
    public static double pow(double a, double b) {
        return Math.pow(a, b);
    }

    @Substitution(isTrivial = true)
    public static int round(float a) {
        return Math.round(a);
    }

    @Substitution(isTrivial = true)
    public static long round(double a) {
        return Math.round(a);
    }

    @Substitution(isTrivial = true)
    public static int abs(int a) {
        return Math.abs(a);
    }

    @Substitution(isTrivial = true)
    public static long abs(long a) {
        return Math.abs(a);
    }

    @Substitution(isTrivial = true)
    public static float abs(float a) {
        return Math.abs(a);
    }

    @Substitution(isTrivial = true)
    public static double abs(double a) {
        return Math.abs(a);
    }

    @Substitution(isTrivial = true)
    public static float max(float a, float b) {
        return Math.max(a, b);
    }

    @Substitution(isTrivial = true)
    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    @Substitution(isTrivial = true)
    public static float min(float a, float b) {
        return Math.min(a, b);
    }

    @Substitution(isTrivial = true)
    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    @Substitution(isTrivial = true)
    public static double ulp(double d) {
        return Math.ulp(d);
    }

    @Substitution(isTrivial = true)
    public static float ulp(float f) {
        return Math.ulp(f);
    }

    @Substitution(isTrivial = true)
    public static double signum(double d) {
        return Math.signum(d);
    }

    @Substitution(isTrivial = true)
    public static float signum(float f) {
        return Math.signum(f);
    }

    @Substitution(isTrivial = true)
    public static double sinh(double x) {
        return Math.sinh(x);
    }

    @Substitution(isTrivial = true)
    public static double cosh(double x) {
        return Math.cosh(x);
    }

    @Substitution(isTrivial = true)
    public static double tanh(double x) {
        return Math.tanh(x);
    }

    @Substitution(isTrivial = true)
    public static double hypot(double x, double y) {
        return Math.hypot(x, y);
    }

    @Substitution(isTrivial = true)
    public static double expm1(double x) {
        return Math.expm1(x);
    }

    @Substitution(isTrivial = true)
    public static double log1p(double x) {
        return Math.log1p(x);
    }

    @Substitution(isTrivial = true)
    public static double copySign(double magnitude, double sign) {
        return Math.copySign(magnitude, sign);
    }

    @Substitution(isTrivial = true)
    public static float copySign(float magnitude, float sign) {
        return Math.copySign(magnitude, sign);
    }

    @Substitution(isTrivial = true)
    public static int getExponent(float f) {
        return Math.getExponent(f);
    }

    @Substitution(isTrivial = true)
    public static int getExponent(double d) {
        return Math.getExponent(d);
    }

    @Substitution(isTrivial = true)
    public static double nextAfter(double start, double direction) {
        return Math.nextAfter(start, direction);
    }

    @Substitution(isTrivial = true)
    public static float nextAfter(float start, double direction) {
        return Math.nextAfter(start, direction);
    }

    @Substitution(isTrivial = true)
    public static double nextUp(double d) {
        return Math.nextUp(d);
    }

    @Substitution(isTrivial = true)
    public static float nextUp(float f) {
        return Math.nextUp(f);
    }

    @Substitution(isTrivial = true)
    public static double nextDown(double d) {
        return Math.nextDown(d);
    }

    @Substitution(isTrivial = true)
    public static float nextDown(float f) {
        return Math.nextDown(f);
    }

    @Substitution(isTrivial = true)
    public static double scalb(double d, int scaleFactor) {
        return Math.scalb(d, scaleFactor);
    }

    @Substitution(isTrivial = true)
    public static float scalb(float f, int scaleFactor) {
        return Math.scalb(f, scaleFactor);
    }
}
