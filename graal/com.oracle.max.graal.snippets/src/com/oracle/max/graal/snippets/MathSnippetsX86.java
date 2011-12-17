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
package com.oracle.max.graal.snippets;

import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.snippets.nodes.*;
import com.oracle.max.graal.snippets.nodes.MathIntrinsicNode.Operation;
import com.sun.cri.ci.*;

/**
 * Snippets for {@link java.lang.Math} methods.
 */
@ClassSubstitution(java.lang.Math.class)
public class MathSnippetsX86 implements SnippetsInterface {

    private static final double PI_4 = 0.7853981633974483;

    public static double abs(double x) {
        return MathIntrinsicNode.compute(x, Operation.ABS);
    }

    public static double sqrt(double x) {
        return MathIntrinsicNode.compute(x, Operation.SQRT);
    }

    public static double log(double x) {
        return MathIntrinsicNode.compute(x, Operation.LOG);
    }

    public static double log10(double x) {
        return MathIntrinsicNode.compute(x, Operation.LOG10);
    }

    // NOTE on snippets below:
    //   Math.sin(), .cos() and .tan() guarantee a value within 1 ULP of the
    //   exact result, but x87 trigonometric FPU instructions are only that
    //   accurate within [-pi/4, pi/4]. Examine the passed value and provide
    //   a slow path for inputs outside of that interval.

    public static double sin(double x) {
        if (abs(x) < PI_4) {
            return MathIntrinsicNode.compute(x, Operation.SIN);
        } else {
            return RuntimeCallNode.performCall(CiRuntimeCall.ArithmeticSin, x);
        }
    }

    public static double cos(double x) {
        if (abs(x) < PI_4) {
            return MathIntrinsicNode.compute(x, Operation.COS);
        } else {
            return RuntimeCallNode.performCall(CiRuntimeCall.ArithmeticCos, x);
        }
    }

    public static double tan(double x) {
        if (abs(x) < PI_4) {
            return MathIntrinsicNode.compute(x, Operation.TAN);
        } else {
            return RuntimeCallNode.performCall(CiRuntimeCall.ArithmeticTan, x);
        }
    }

}
