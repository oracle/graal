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

import static com.oracle.graal.compiler.common.calc.Condition.*;
import static com.oracle.graal.nodes.calc.ConditionalNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Substitutions for {@link UnsignedMath}.
 */
@ClassSubstitution(UnsignedMath.class)
public class UnsignedMathSubstitutions {

    @MethodSubstitution
    public static boolean aboveThan(int a, int b) {
        return materializeCondition(BT, b, a);
    }

    @MethodSubstitution
    public static boolean aboveOrEqual(int a, int b) {
        return !materializeCondition(BT, a, b);
    }

    /**
     * Unsigned comparison belowThan for two numbers.
     */
    @MethodSubstitution
    public static boolean belowThan(int a, int b) {
        return materializeCondition(BT, a, b);
    }

    /**
     * Unsigned comparison belowOrEqual for two numbers.
     */
    @MethodSubstitution
    public static boolean belowOrEqual(int a, int b) {
        return !materializeCondition(BT, b, a);
    }

    /**
     * Unsigned comparison aboveThan for two numbers.
     */
    @MethodSubstitution
    public static boolean aboveThan(long a, long b) {
        return materializeCondition(BT, b, a);
    }

    /**
     * Unsigned comparison aboveOrEqual for two numbers.
     */
    @MethodSubstitution
    public static boolean aboveOrEqual(long a, long b) {
        return !materializeCondition(BT, a, b);
    }

    /**
     * Unsigned comparison belowThan for two numbers.
     */
    @MethodSubstitution
    public static boolean belowThan(long a, long b) {
        return materializeCondition(BT, a, b);
    }

    /**
     * Unsigned comparison belowOrEqual for two numbers.
     */
    @MethodSubstitution
    public static boolean belowOrEqual(long a, long b) {
        return !materializeCondition(BT, b, a);
    }

    /**
     * Unsigned division for two numbers.
     */
    @MethodSubstitution
    public static int divide(int a, int b) {
        return UnsignedDivNode.unsignedDivide(a, b);
    }

    /**
     * Unsigned remainder for two numbers.
     */
    @MethodSubstitution
    public static int remainder(int a, int b) {
        return UnsignedRemNode.unsignedRemainder(a, b);
    }

    /**
     * Unsigned division for two numbers.
     */
    @MethodSubstitution
    public static long divide(long a, long b) {
        return UnsignedDivNode.unsignedDivide(a, b);
    }

    /**
     * Unsigned remainder for two numbers.
     */
    @MethodSubstitution
    public static long remainder(long a, long b) {
        return UnsignedRemNode.unsignedRemainder(a, b);
    }
}
