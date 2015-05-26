/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java.test;

import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.Kind;
import org.junit.*;


public class ConstantTest extends FieldUniverse {

    @Test
    public void testNegativeZero() {
        Assert.assertTrue("Constant for 0.0f must be different from -0.0f", JavaConstant.FLOAT_0 != JavaConstant.forFloat(-0.0F));
        Assert.assertTrue("Constant for 0.0d must be different from -0.0d", JavaConstant.DOUBLE_0 != JavaConstant.forDouble(-0.0d));
    }

    @Test
    public void testNullIsNull() {
        Assert.assertTrue(JavaConstant.NULL_POINTER.isNull());
    }

    @Test
    public void testOne() {
        for (Kind kind : Kind.values()) {
            if (kind.isNumericInteger() || kind.isNumericFloat()) {
                Assert.assertTrue(JavaConstant.one(kind).getKind() == kind);
            }
        }
        Assert.assertEquals(1, JavaConstant.one(Kind.Int).asInt());
        Assert.assertEquals(1L, JavaConstant.one(Kind.Long).asLong());
        Assert.assertEquals(1, JavaConstant.one(Kind.Byte).asInt());
        Assert.assertEquals(1, JavaConstant.one(Kind.Short).asInt());
        Assert.assertEquals(1, JavaConstant.one(Kind.Char).asInt());
        Assert.assertTrue(1F == JavaConstant.one(Kind.Float).asFloat());
        Assert.assertTrue(1D == JavaConstant.one(Kind.Double).asDouble());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalOne() {
        JavaConstant.one(Kind.Illegal);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVoidOne() {
        JavaConstant.one(Kind.Void);
    }
}
