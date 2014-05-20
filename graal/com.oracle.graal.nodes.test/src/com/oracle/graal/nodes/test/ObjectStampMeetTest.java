/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.test;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;

public class ObjectStampMeetTest extends ObjectStampTest {

    // class A
    // class B extends A
    // class C extends B implements I
    // class D extends A
    // abstract class E extends A
    // interface I

    @Test
    public void testMeet0() {
        Stamp a = StampFactory.declared(getType(A.class));
        Stamp b = StampFactory.declared(getType(B.class));
        Assert.assertEquals(a, meet(a, b));
    }

    @Test
    public void testMeet1() {
        Stamp a = StampFactory.declared(getType(A.class));
        Stamp aNonNull = StampFactory.declaredNonNull(getType(A.class));
        Stamp b = StampFactory.declared(getType(B.class));
        Stamp bNonNull = StampFactory.declaredNonNull(getType(B.class));
        Assert.assertEquals(a, meet(aNonNull, b));
        Assert.assertEquals(aNonNull, meet(aNonNull, bNonNull));
    }

    @Test
    public void testMeet2() {
        Stamp a = StampFactory.declared(getType(A.class));
        Stamp aExact = StampFactory.exactNonNull(getType(A.class));
        Stamp b = StampFactory.declared(getType(B.class));
        Assert.assertEquals(a, meet(aExact, b));
    }

    @Test
    public void testMeet3() {
        Stamp a = StampFactory.declared(getType(A.class));
        Stamp d = StampFactory.declared(getType(D.class));
        Stamp c = StampFactory.declared(getType(C.class));
        Assert.assertEquals(a, meet(c, d));
    }

    @Test
    public void testMeet4() {
        Stamp dExactNonNull = StampFactory.exactNonNull(getType(D.class));
        Stamp cExactNonNull = StampFactory.exactNonNull(getType(C.class));
        Stamp aNonNull = StampFactory.declaredNonNull(getType(A.class));
        Assert.assertEquals(aNonNull, meet(cExactNonNull, dExactNonNull));
    }

    @Test
    public void testMeet() {
        Stamp dExact = StampFactory.exact(getType(D.class));
        Stamp c = StampFactory.declared(getType(C.class));
        Stamp a = StampFactory.declared(getType(A.class));
        Assert.assertEquals(a, meet(dExact, c));
    }

    @Test
    public void testMeet6() {
        Stamp dExactNonNull = StampFactory.exactNonNull(getType(D.class));
        Stamp alwaysNull = StampFactory.alwaysNull();
        Stamp dExact = StampFactory.exact(getType(D.class));
        Assert.assertEquals(dExact, meet(dExactNonNull, alwaysNull));
    }

    @Test
    public void testMeet7() {
        Stamp aExact = StampFactory.exact(getType(A.class));
        Stamp e = StampFactory.declared(getType(E.class));
        Stamp a = StampFactory.declared(getType(A.class));
        Assert.assertEquals(a, meet(aExact, e));
    }

    @Test
    public void testMeetInterface0() {
        Stamp a = StampFactory.declared(getType(A.class));
        Stamp i = StampFactory.declared(getType(I.class));
        Assert.assertEquals(StampFactory.declared(getType(Object.class)), meet(a, i));
    }

    @Test
    public void testMeetIllegal1() {
        for (Class<?> clazz : new Class<?>[]{A.class, B.class, C.class, D.class, E.class, I.class, Object.class}) {
            ResolvedJavaType type = getType(clazz);
            for (Stamp test : new Stamp[]{StampFactory.declared(type), StampFactory.declaredNonNull(type), StampFactory.exact(type), StampFactory.exactNonNull(type)}) {
                if (!type.isAbstract() || !((ObjectStamp) test).isExactType()) {
                    Assert.assertEquals("meeting illegal and " + test, test, meet(StampFactory.illegal(Kind.Object), test));
                }
            }
        }
    }
}
