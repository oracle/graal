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
package com.oracle.graal.truffle.substitutions;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.truffle.nodes.arithmetic.*;
import com.oracle.truffle.api.*;

/**
 * Intrinsics for exact math operations that throw an exception in case the operation would overflow
 * the allowed range.
 */
@ClassSubstitution(ExactMath.class)
public class ExactMathSubstitutions {

    @MethodSubstitution
    public static int addExact(int x, int y) {
        return IntegerAddExactNode.addExact(x, y);
    }

    @MethodSubstitution
    public static long addExact(long x, long y) {
        return IntegerAddExactNode.addExact(x, y);
    }

    @MethodSubstitution
    public static int subtractExact(int x, int y) {
        return IntegerSubExactNode.subtractExact(x, y);
    }

    @MethodSubstitution
    public static long subtractExact(long x, long y) {
        return IntegerSubExactNode.subtractExact(x, y);
    }

    @MethodSubstitution
    public static int multiplyExact(int x, int y) {
        return IntegerMulExactNode.multiplyExact(x, y);
    }

    @MethodSubstitution
    public static long multiplyExact(long x, long y) {
        return IntegerMulExactNode.multiplyExact(x, y);
    }
}
