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
import com.oracle.graal.nodes.calc.*;

/**
 * Substitutions for {@link java.lang.Double} methods.
 */
@ClassSubstitution(java.lang.Double.class)
public class DoubleSubstitutions {

    private static final long NAN_RAW_LONG_BITS = Double.doubleToRawLongBits(Double.NaN);

    @MethodSubstitution
    public static long doubleToRawLongBits(double value) {
        return ReinterpretNode.reinterpret(Kind.Long, value);
    }

    // TODO This method is not necessary, since the JDK method does exactly this
    @MethodSubstitution
    public static long doubleToLongBits(double value) {
        if (value != value) {
            return NAN_RAW_LONG_BITS;
        } else {
            return doubleToRawLongBits(value);
        }
    }

    @MethodSubstitution
    public static double longBitsToDouble(long bits) {
        return ReinterpretNode.reinterpret(Kind.Double, bits);
    }
}
