/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.truffle.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.runtime.FixedPointMath;

public class FixedPointMathTest {

    @Test
    public void testMultiply() {
        int one = FixedPointMath.toFixedPoint(1.0);
        Assert.assertEquals(400, FixedPointMath.multiply(one, 400));
        Assert.assertEquals(200, FixedPointMath.multiply(FixedPointMath.toFixedPoint(0.5), 400));
        Assert.assertEquals(600, FixedPointMath.multiply(FixedPointMath.toFixedPoint(1.5), 400));
        Assert.assertEquals(134217727, FixedPointMath.multiply(one, 134217727));
    }

    /**
     * A threshold at or above 2^31 / 16 used to overflow the 32-bit product and produce a negative
     * scaled threshold, which made every call target eligible for compilation.
     */
    @Test
    public void testMultiplyDoesNotOverflow() {
        int one = FixedPointMath.toFixedPoint(1.0);
        Assert.assertEquals(134217728, FixedPointMath.multiply(one, 134217728));
        Assert.assertEquals(Integer.MAX_VALUE, FixedPointMath.multiply(one, Integer.MAX_VALUE));
        int highLoadScale = FixedPointMath.toFixedPoint(50.0);
        Assert.assertEquals(500000000, FixedPointMath.multiply(highLoadScale, 10000000));
        Assert.assertEquals(Integer.MAX_VALUE, FixedPointMath.multiply(highLoadScale, 100000000));
        Assert.assertEquals(Integer.MAX_VALUE, FixedPointMath.multiply(FixedPointMath.toFixedPoint(1e12), 1000));
    }
}
