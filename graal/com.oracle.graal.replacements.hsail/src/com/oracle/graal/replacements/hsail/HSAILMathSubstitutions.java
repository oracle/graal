/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.lir.hsail.*;

/**
 * Substitutions for {@link Math} methods. For any calls to the routines listed below and annotated
 * with {@link MethodSubstitution}, Graal replaces the call with a {@link HSAILMathIntrinsicsNode}.
 */
@ClassSubstitution(java.lang.Math.class)
public class HSAILMathSubstitutions {

    /**
     * Substitution for {@link Math#abs(int)}.
     * 
     * @param x the input
     * @return the result of the computation
     */
    @MethodSubstitution
    public static int abs(int x) {
        return HSAILMathIntrinsicsNode.compute(x, HSAILArithmetic.ABS);
    }

    /**
     * Substitution for {@link Math#abs(long)}.
     * 
     * @param x the input
     * @return the result of the computation
     */
    @MethodSubstitution
    public static long abs(long x) {
        return HSAILMathIntrinsicsNode.compute(x, HSAILArithmetic.ABS);
    }

    /**
     * Substitution for {@link Math#abs(float)}.
     * 
     * @param x the input
     * @return the result of the computation
     */
    @MethodSubstitution
    public static float abs(float x) {
        return HSAILMathIntrinsicsNode.compute(x, HSAILArithmetic.ABS);
    }

    /**
     * Substitution for {@link Math#abs(double)}.
     * 
     * @param x the input
     * @return the result of the computation
     */
    @MethodSubstitution
    public static double abs(double x) {
        return HSAILMathIntrinsicsNode.compute(x, HSAILArithmetic.ABS);
    }

    /**
     * Substitution for Math.ceil(double).
     * 
     * @param x the input
     * @return the result of the computation
     */
    @MethodSubstitution
    public static double ceil(double x) {
        return HSAILMathIntrinsicsNode.compute(x, HSAILArithmetic.CEIL);
    }

    /**
     * Substitution for {@link Math#floor(double)}.
     * 
     * @param x the input
     * @return the result of the computation
     */
    @MethodSubstitution
    public static double floor(double x) {
        return HSAILMathIntrinsicsNode.compute(x, HSAILArithmetic.FLOOR);
    }

    /**
     * Substitution for {@link Math#rint(double)}.
     * 
     * @param x the input
     * @return the result of the computation
     */
    @MethodSubstitution
    public static double rint(double x) {
        return HSAILMathIntrinsicsNode.compute(x, HSAILArithmetic.RINT);
    }

    /**
     * Substitution for {@link Math#sqrt(double)}.
     * 
     * @param x the input
     * @return the result of the computation
     */
    @MethodSubstitution
    public static double sqrt(double x) {
        return HSAILMathIntrinsicsNode.compute(x, HSAILArithmetic.SQRT);
    }
}
