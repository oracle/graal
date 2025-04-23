/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.standard;

import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Substitution;

/**
 * These substitutions are just for performance. Directly uses the optimized host intrinsics
 * avoiding expensive guest native calls.
 */
@EspressoSubstitutions
public final class Target_java_lang_StrictMath {

    @Substitution
    public static double sin(double a) {
        return StrictMath.sin(a);
    }

    @Substitution
    public static double cos(double a) {
        return StrictMath.cos(a);
    }

    @Substitution
    public static double tan(double a) {
        return StrictMath.tan(a);
    }

    @Substitution
    public static double asin(double a) {
        return StrictMath.asin(a);
    }

    @Substitution
    public static double acos(double a) {
        return StrictMath.acos(a);
    }

    @Substitution
    public static double atan(double a) {
        return StrictMath.atan(a);
    }

    @Substitution
    public static double log(double a) {
        return StrictMath.log(a);
    }

    @Substitution
    public static double log10(double a) {
        return StrictMath.log10(a);
    }

    @Substitution
    public static double sqrt(double a) {
        return StrictMath.sqrt(a);
    }

    // Checkstyle: stop method name check
    @Substitution
    public static double IEEEremainder(double f1, double f2) {
        return StrictMath.IEEEremainder(f1, f2);
    }
    // Checkstyle: resume method name check

    @Substitution
    public static double atan2(double y, double x) {
        return StrictMath.atan2(y, x);
    }

    @Substitution
    public static double sinh(double x) {
        return StrictMath.sinh(x);
    }

    @Substitution
    public static double cosh(double x) {
        return StrictMath.cosh(x);
    }

    @Substitution
    public static double tanh(double x) {
        return StrictMath.tanh(x);
    }

    @Substitution
    public static double expm1(double x) {
        return StrictMath.expm1(x);
    }

    @Substitution
    public static double log1p(double x) {
        return StrictMath.log1p(x);
    }
}
