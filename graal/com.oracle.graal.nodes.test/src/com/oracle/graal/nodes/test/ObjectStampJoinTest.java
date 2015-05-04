/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodes.type.*;

public class ObjectStampJoinTest extends AbstractObjectStampTest {

    // class A
    // class B extends A
    // class C extends B implements I
    // class D extends A
    // abstract class E extends A
    // interface I

    @Test
    public void testJoin0() {
        Stamp a = StampFactory.declared(getType(A.class));
        Stamp b = StampFactory.declared(getType(B.class));
        Assert.assertEquals(b, join(a, b));
    }

    @Test
    public void testJoin1() {
        Stamp aNonNull = StampFactory.declaredNonNull(getType(A.class));
        Stamp b = StampFactory.declared(getType(B.class));
        Stamp bNonNull = StampFactory.declaredNonNull(getType(B.class));
        Assert.assertEquals(bNonNull, join(aNonNull, b));
    }

    @Test
    public void testJoin2() {
        Stamp aExact = StampFactory.exactNonNull(getType(A.class));
        Stamp b = StampFactory.declared(getType(B.class));
        Assert.assertEquals(StampFactory.empty(Kind.Object), join(aExact, b));
    }

    @Test
    public void testJoin3() {
        Stamp d = StampFactory.declared(getType(D.class));
        Stamp c = StampFactory.declared(getType(C.class));
        Assert.assertTrue(StampTool.isPointerAlwaysNull(join(c, d)));
    }

    @Test
    public void testJoin4() {
        Stamp dExactNonNull = StampFactory.exactNonNull(getType(D.class));
        Stamp c = StampFactory.declared(getType(C.class));
        Assert.assertEquals(StampFactory.empty(Kind.Object), join(c, dExactNonNull));
    }

    @Test
    public void testJoin5() {
        Stamp dExact = StampFactory.exact(getType(D.class));
        Stamp c = StampFactory.declared(getType(C.class));
        Stamp join = join(c, dExact);
        Assert.assertTrue(StampTool.isPointerAlwaysNull(join));
        Assert.assertNull(StampTool.typeOrNull(join));
        Assert.assertFalse(StampTool.isExactType(join));
    }

    @Test
    public void testJoin6() {
        Stamp dExactNonNull = StampFactory.exactNonNull(getType(D.class));
        Stamp alwaysNull = StampFactory.alwaysNull();
        Stamp join = join(alwaysNull, dExactNonNull);
        Assert.assertFalse(join.hasValues());
        Assert.assertFalse(StampTool.isPointerNonNull(join));
        Assert.assertFalse(StampTool.isPointerAlwaysNull(join));
    }

    @Test
    public void testJoin7() {
        Stamp aExact = StampFactory.exact(getType(A.class));
        Stamp e = StampFactory.declared(getType(E.class));
        Stamp join = join(aExact, e);
        Assert.assertTrue(StampTool.isPointerAlwaysNull(join));
        Assert.assertNull(StampTool.typeOrNull(join));
        Assert.assertFalse(StampTool.isExactType(join));
    }

    @Test
    public void testJoin8() {
        Stamp bExact = StampFactory.exactNonNull(getType(B.class));
        Stamp dExact = StampFactory.exact(getType(D.class));
        Stamp join = join(bExact, dExact);
        Assert.assertFalse(join.hasValues());
    }

    @Test
    public void testJoin9() {
        Stamp bExact = StampFactory.exact(getType(B.class));
        Stamp dExact = StampFactory.exact(getType(D.class));
        Stamp join = join(bExact, dExact);
        Assert.assertTrue(StampTool.isPointerAlwaysNull(join));
        Assert.assertNull(StampTool.typeOrNull(join));
        Assert.assertNull(StampTool.typeOrNull(join));
    }

    @Test
    public void testJoinInterfaceSimple() {
        // Tests joining of interface
        testJoinInterface(A.class, B.class, I.class);
    }

    @Test
    public void testJoinInterfaceArray() {
        // Tests joining of arrays interface
        testJoinInterface(A[].class, B[].class, I[].class);
    }

    @Test
    public void testJoinInterfaceMultiArray() {
        // Tests joining of multidimensional arrays of interface
        testJoinInterface(A[][].class, B[][].class, I[][].class);
    }

    private void testJoinInterface(Class<?> typeA, Class<?> typeB, Class<?> typeI) {
        testJoinInterface0(typeA, typeI);
        testJoinInterface1(typeA, typeI);
        testJoinInterface2(typeB, typeI);
        testJoinInterface3(typeB, typeI);
    }

    private void testJoinInterface0(Class<?> typeA, Class<?> typeI) {
        Stamp a = StampFactory.declared(getType(typeA));
        Stamp i = StampFactory.declaredTrusted(getType(typeI));
        Assert.assertNotSame(StampFactory.empty(Kind.Object), join(a, i));
    }

    private void testJoinInterface1(Class<?> typeA, Class<?> typeI) {
        Stamp aNonNull = StampFactory.declaredNonNull(getType(typeA));
        Stamp i = StampFactory.declaredTrusted(getType(typeI));
        Stamp join = join(aNonNull, i);
        Assert.assertTrue(join instanceof ObjectStamp);
        Assert.assertTrue(((ObjectStamp) join).nonNull());
    }

    private void testJoinInterface2(Class<?> typeB, Class<?> typeI) {
        Stamp bExact = StampFactory.exactNonNull(getType(typeB));
        Stamp i = StampFactory.declaredTrusted(getType(typeI));
        Stamp join = join(i, bExact);
        Assert.assertEquals(StampFactory.empty(Kind.Object), join);
    }

    private void testJoinInterface3(Class<?> typeB, Class<?> typeI) {
        Stamp bExact = StampFactory.exactNonNull(getType(typeB));
        Stamp i = StampFactory.declared(getType(typeI)); // not trusted
        Stamp join = join(i, bExact);
        Assert.assertEquals(bExact, join);
    }

}
