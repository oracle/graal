/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
@ClassSubstitution(value = java.lang.Math.class)
public class MathSubstitutionsX86 {

    private static final double PI_4 = Math.PI / 4;

    /**
     * Special cases from {@link Math#pow} and __ieee754_pow (in sharedRuntimeTrans.cpp).
     */
    @MethodSubstitution(guard = MathGuard.class)
    public static double pow(double x, double y) {
        // If the second argument is positive or negative zero, then the result is 1.0.
        if (y == 0.0D) {
            return 1;
        }

        // If the second argument is 1.0, then the result is the same as the first argument.
        if (y == 1.0D) {
            return x;
        }

        // If the second argument is NaN, then the result is NaN.
        if (Double.isNaN(y)) {
            return Double.NaN;
        }

        // If the first argument is NaN and the second argument is nonzero, then the result is NaN.
        if (Double.isNaN(x) && y != 0.0D) {
            return Double.NaN;
        }

        // x**-1 = 1/x
        if (y == -1.0D) {
            return 1 / x;
        }

        // x**2 = x*x
        if (y == 2.0D) {
            return x * x;
        }

        // x**0.5 = sqrt(x)
        if (y == 0.5D && x >= 0.0D) {
            return Math.sqrt(x);
        }
        return callDouble2(ARITHMETIC_POW, x, y);
    }

    // NOTE on snippets below:
    // Math.sin(), .cos() and .tan() guarantee a value within 1 ULP of the
    // exact result, but x87 trigonometric FPU instructions are only that
    // accurate within [-pi/4, pi/4]. Examine the passed value and provide
    // a slow path for inputs outside of that interval.

    @MethodSubstitution(guard = MathGuard.class)
    public static double sin(double x) {
        if (Math.abs(x) < PI_4) {
            return MathIntrinsicNode.compute(x, Operation.SIN);
        } else {
            return callDouble1(ARITHMETIC_SIN, x);
        }
    }

    @MethodSubstitution(guard = MathGuard.class)
    public static double cos(double x) {
        if (Math.abs(x) < PI_4) {
            return MathIntrinsicNode.compute(x, Operation.COS);
        } else {
            return callDouble1(ARITHMETIC_COS, x);
        }
    }

    @MethodSubstitution(guard = MathGuard.class)
    public static double tan(double x) {
        if (Math.abs(x) < PI_4) {
            return MathIntrinsicNode.compute(x, Operation.TAN);
        } else {
            return callDouble1(ARITHMETIC_TAN, x);
        }
    }

    public static class MathGuard implements SubstitutionGuard {
        public boolean execute() {
            // FIXME should return whether the current compilation target supports these
            String arch = System.getProperty("os.arch");
            return arch.equals("amd64") || arch.equals("x86_64");
        }
    }

    public static final ForeignCallDescriptor ARITHMETIC_SIN = new ForeignCallDescriptor("arithmeticSin", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_COS = new ForeignCallDescriptor("arithmeticCos", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_TAN = new ForeignCallDescriptor("arithmeticTan", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_POW = new ForeignCallDescriptor("arithmeticPow", double.class, double.class, double.class);

    @NodeIntrinsic(value = ForeignCallNode.class, setStampFromReturnType = true)
    private static native double callDouble1(@ConstantNodeParameter ForeignCallDescriptor descriptor, double value);

    @NodeIntrinsic(value = ForeignCallNode.class, setStampFromReturnType = true)
    private static native double callDouble2(@ConstantNodeParameter ForeignCallDescriptor descriptor, double a, double b);
}
