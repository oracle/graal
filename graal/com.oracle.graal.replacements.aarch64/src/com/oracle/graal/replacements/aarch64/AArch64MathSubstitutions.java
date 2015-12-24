/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.replacements.aarch64;

import com.oracle.graal.api.replacements.ClassSubstitution;
import com.oracle.graal.api.replacements.MethodSubstitution;

/**
 * Substitutions for {@link java.lang.Math} methods. Aarch64 does not offer special instructions to
 * implement these functions, so implement them either in Java or call standard c library functions.
 */
@ClassSubstitution(java.lang.Math.class)
public class AArch64MathSubstitutions {

    @MethodSubstitution
    public static double log(double x) {
        return StrictMath.log(x);
    }

    @MethodSubstitution
    public static double log10(double x) {
        return StrictMath.log10(x);
    }

    @MethodSubstitution
    public static double sin(double x) {
        return StrictMath.sin(x);
    }

    @MethodSubstitution
    public static double cos(double x) {
        return StrictMath.cos(x);
    }

    @MethodSubstitution
    public static double tan(double x) {
        return StrictMath.tan(x);
    }

}
