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
package com.oracle.graal.nodes.test;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.type.*;

public class ObjectStampJoinTest extends GraalCompilerTest {

    private static class A {

    }

    private static class B extends A {

    }

    private static class C extends B implements I {

    }

    private static class D extends A {

    }

    private abstract static class E extends A {

    }

    private interface I {

    }

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
        Assert.assertEquals(StampFactory.illegal(Kind.Object), join(aExact, b));
    }

    @Test
    public void testJoin3() {
        Stamp d = StampFactory.declared(getType(D.class));
        Stamp c = StampFactory.declared(getType(C.class));
        Assert.assertTrue(StampTool.isObjectAlwaysNull(join(c, d)));
    }

    @Test
    public void testJoin4() {
        Stamp dExactNonNull = StampFactory.exactNonNull(getType(D.class));
        Stamp c = StampFactory.declared(getType(C.class));
        Assert.assertEquals(StampFactory.illegal(Kind.Object), join(c, dExactNonNull));
    }

    @Test
    public void testJoin5() {
        Stamp dExact = StampFactory.exact(getType(D.class));
        Stamp c = StampFactory.declared(getType(C.class));
        Stamp join = join(c, dExact);
        Assert.assertTrue(StampTool.isObjectAlwaysNull(join));
        Assert.assertNull(StampTool.typeOrNull(join));
        Assert.assertFalse(StampTool.isExactType(join));
    }

    @Test
    public void testJoin6() {
        Stamp dExactNonNull = StampFactory.exactNonNull(getType(D.class));
        Stamp alwaysNull = StampFactory.alwaysNull();
        Stamp join = join(alwaysNull, dExactNonNull);
        Assert.assertFalse(join.isLegal());
        Assert.assertFalse(StampTool.isObjectNonNull(join));
        Assert.assertFalse(StampTool.isObjectAlwaysNull(join));
    }

    @Test
    public void testJoin7() {
        Stamp aExact = StampFactory.exact(getType(A.class));
        Stamp e = StampFactory.declared(getType(E.class));
        Stamp join = join(aExact, e);
        Assert.assertTrue(StampTool.isObjectAlwaysNull(join));
        Assert.assertNull(StampTool.typeOrNull(join));
        Assert.assertFalse(StampTool.isExactType(join));
    }

    @Test
    public void testJoinInterface0() {
        Stamp a = StampFactory.declared(getType(A.class));
        Stamp b = StampFactory.declared(getType(I.class));
        Assert.assertNotSame(StampFactory.illegal(Kind.Object), join(a, b));
    }

    @Test
    public void testJoinInterface1() {
        Stamp aNonNull = StampFactory.declaredNonNull(getType(A.class));
        Stamp i = StampFactory.declared(getType(I.class));
        Stamp join = join(aNonNull, i);
        Assert.assertTrue(join instanceof ObjectStamp);
        Assert.assertTrue(((ObjectStamp) join).nonNull());
    }

    @Test
    public void testJoinInterface2() {
        Stamp bExact = StampFactory.exactNonNull(getType(B.class));
        Stamp i = StampFactory.declared(getType(I.class));
        Stamp join = join(i, bExact);
        Assert.assertEquals(StampFactory.illegal(Kind.Object), join);
    }

    private static Stamp join(Stamp a, Stamp b) {
        Stamp ab = a.join(b);
        Stamp ba = b.join(a);
        Assert.assertEquals(ab, ba);
        return ab;
    }

    private ResolvedJavaType getType(Class<?> clazz) {
        return getMetaAccess().lookupJavaType(clazz);
    }
}
