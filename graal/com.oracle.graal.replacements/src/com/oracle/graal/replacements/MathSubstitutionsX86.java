/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.replacements.nodes.MathIntrinsicNode.Operation;

/**
 * Substitutions for {@link java.lang.Math} methods.
 */
@ClassSubstitution(java.lang.Math.class)
public class MathSubstitutionsX86 {

    private static final double PI_4 = Math.PI / 4;

    @MethodSubstitution
    public static double abs(double x) {
        return MathIntrinsicNode.compute(x, Operation.ABS);
    }

    @MethodSubstitution
    public static double sqrt(double x) {
        return MathIntrinsicNode.compute(x, Operation.SQRT);
    }

    @MethodSubstitution
    public static double log(double x) {
        return MathIntrinsicNode.compute(x, Operation.LOG);
    }

    @MethodSubstitution
    public static double log10(double x) {
        return MathIntrinsicNode.compute(x, Operation.LOG10);
    }

    /**
     * Special cases from {@link Math#pow} and __ieee754_pow (in sharedRuntimeTrans.cpp).
     */
    @MethodSubstitution
    public static double pow(double x, double y) {
        // If the second argument is positive or negative zero, then the result is 1.0.
        if (y == 0) {
            return 1;
        }

        // If the second argument is 1.0, then the result is the same as the first argument.
        if (y == 1) {
            return x;
        }

        // If the second argument is NaN, then the result is NaN.
        if (Double.isNaN(y)) {
            return Double.NaN;
        }

        // If the first argument is NaN and the second argument is nonzero, then the result is NaN.
        if (Double.isNaN(x) && y != 0) {
            return Double.NaN;
        }

        // x**-1 = 1/x
        if (y == -1) {
            return 1 / x;
        }

        // x**2 = x*x
        if (y == 2) {
            return x * x;
        }

        // x**0.5 = sqrt(x)
        if (y == 0.5 && x >= 0) {
            return sqrt(x);
        }

        return pow(x, y);
    }

    // NOTE on snippets below:
    // Math.sin(), .cos() and .tan() guarantee a value within 1 ULP of the
    // exact result, but x87 trigonometric FPU instructions are only that
    // accurate within [-pi/4, pi/4]. Examine the passed value and provide
    // a slow path for inputs outside of that interval.

    @MethodSubstitution
    public static double sin(double x) {
        if (abs(x) < PI_4) {
            return MathIntrinsicNode.compute(x, Operation.SIN);
        } else {
            return callDouble(ARITHMETIC_SIN, x);
        }
    }

    @MethodSubstitution
    public static double cos(double x) {
        if (abs(x) < PI_4) {
            return MathIntrinsicNode.compute(x, Operation.COS);
        } else {
            return callDouble(ARITHMETIC_COS, x);
        }
    }

    @MethodSubstitution
    public static double tan(double x) {
        if (abs(x) < PI_4) {
            return MathIntrinsicNode.compute(x, Operation.TAN);
        } else {
            return callDouble(ARITHMETIC_TAN, x);
        }
    }

    public static final ForeignCallDescriptor ARITHMETIC_SIN = new ForeignCallDescriptor("arithmeticSin", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_COS = new ForeignCallDescriptor("arithmeticCos", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_TAN = new ForeignCallDescriptor("arithmeticTan", double.class, double.class);

    @NodeIntrinsic(value = ForeignCallNode.class, setStampFromReturnType = true)
    public static double callDouble(@ConstantNodeParameter ForeignCallDescriptor descriptor, double value) {
        if (descriptor == ARITHMETIC_SIN) {
            return Math.sin(value);
        }
        if (descriptor == ARITHMETIC_COS) {
            return Math.cos(value);
        }
        assert descriptor == ARITHMETIC_TAN;
        return Math.tan(value);
    }
}
