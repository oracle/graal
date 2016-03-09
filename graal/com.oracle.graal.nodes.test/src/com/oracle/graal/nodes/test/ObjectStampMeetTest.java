/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.JavaKind;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.compiler.common.type.TypeReference;

public class ObjectStampMeetTest extends AbstractObjectStampTest {

    // class A
    // class B extends A
    // class C extends B implements I
    // class D extends A
    // abstract class E extends A
    // interface I

    @Test
    public void testMeet0() {
        Stamp a = StampFactory.object(getType(A.class));
        Stamp b = StampFactory.object(getType(B.class));
        Assert.assertEquals(a, meet(a, b));
    }

    @Test
    public void testMeet1() {
        Stamp a = StampFactory.object(getType(A.class));
        Stamp aNonNull = StampFactory.objectNonNull(getType(A.class));
        Stamp b = StampFactory.object(getType(B.class));
        Stamp bNonNull = StampFactory.objectNonNull(getType(B.class));
        Assert.assertEquals(a, meet(aNonNull, b));
        Assert.assertEquals(aNonNull, meet(aNonNull, bNonNull));
    }

    @Test
    public void testMeet2() {
        Stamp a = StampFactory.object(getType(A.class));
        Stamp aExact = StampFactory.objectNonNull(getType(A.class).asExactReference());
        Stamp b = StampFactory.object(getType(B.class));
        Assert.assertEquals(a, meet(aExact, b));
    }

    @Test
    public void testMeet3() {
        Stamp a = StampFactory.object(getType(A.class));
        Stamp d = StampFactory.object(getType(D.class));
        Stamp c = StampFactory.object(getType(C.class));
        Assert.assertEquals(a, meet(c, d));
    }

    @Test
    public void testMeet4() {
        Stamp dExactNonNull = StampFactory.objectNonNull(getType(D.class).asExactReference());
        Stamp cExactNonNull = StampFactory.objectNonNull(getType(C.class).asExactReference());
        Stamp aNonNull = StampFactory.objectNonNull(getType(A.class));
        Assert.assertEquals(aNonNull, meet(cExactNonNull, dExactNonNull));
    }

    @Test
    public void testMeet() {
        Stamp dExact = StampFactory.object(getType(D.class).asExactReference());
        Stamp c = StampFactory.object(getType(C.class));
        Stamp a = StampFactory.object(getType(A.class));
        Assert.assertEquals(a, meet(dExact, c));
    }

    @Test
    public void testMeet6() {
        Stamp dExactNonNull = StampFactory.objectNonNull(getType(D.class).asExactReference());
        Stamp alwaysNull = StampFactory.alwaysNull();
        Stamp dExact = StampFactory.object(getType(D.class).asExactReference());
        Assert.assertEquals(dExact, meet(dExactNonNull, alwaysNull));
    }

    @Test
    public void testMeet7() {
        Stamp aExact = StampFactory.object(getType(A.class).asExactReference());
        Stamp e = StampFactory.object(getType(E.class));
        Stamp a = StampFactory.object(getType(A.class));
        Assert.assertEquals(a, meet(aExact, e));
    }

    @Test
    public void testMeetInterface0() {
        Stamp a = StampFactory.object(getType(A.class));
        Stamp i = StampFactory.object(getType(I.class));
        Assert.assertEquals(StampFactory.object(getType(Object.class)), meet(a, i));
    }

    @Test
    public void testMeetIllegal1() {
        for (Class<?> clazz : new Class<?>[]{A.class, B.class, C.class, D.class, E.class, I.class, Object.class}) {
            TypeReference type = getType(clazz);
            for (Stamp test : new Stamp[]{StampFactory.object(type), StampFactory.objectNonNull(type), StampFactory.object(type.asExactReference()),
                            StampFactory.objectNonNull(type.asExactReference())}) {
                if (type.getType().isConcrete() || !((ObjectStamp) test).isExactType()) {
                    Assert.assertEquals("meeting empty and " + test, test, meet(StampFactory.empty(JavaKind.Object), test));
                }
            }
        }
    }
}
