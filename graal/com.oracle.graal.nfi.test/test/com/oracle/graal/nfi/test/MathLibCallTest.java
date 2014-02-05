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
package com.oracle.graal.nfi.test;

import org.junit.*;

import com.oracle.graal.api.code.*;

public class MathLibCallTest extends LibCallTest {

    private final Object[] args = new Object[]{Double.doubleToLongBits(3), Double.doubleToLongBits(5.5)};
    private final NativeFunctionHandle handle = ffi.getFunctionHandle("pow", double.class, new Class[]{double.class, double.class});

    @Test
    public void powTest() {
        double result = (double) handle.call(args);
        Assert.assertEquals(Math.pow(3, 5.5), result, 0);
    }

    @Test
    public void compilePowTest() {
        double result = 0;
        for (int i = 0; i < 100000; i++) {
            result = callPow();
        }
        Assert.assertEquals(Math.pow(3, 5.5), result, 0);

    }

    private double callPow() {
        return (double) handle.call(args);
    }
}
