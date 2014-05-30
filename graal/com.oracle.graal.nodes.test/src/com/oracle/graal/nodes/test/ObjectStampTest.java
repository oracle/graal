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
import com.oracle.graal.compiler.test.*;

public class ObjectStampTest extends GraalCompilerTest {

    protected static class A {

    }

    protected static class B extends A {

    }

    protected static class C extends B implements I {

    }

    protected static class D extends A {

    }

    protected abstract static class E extends A {

    }

    protected interface I {

    }

    protected static Stamp join(Stamp a, Stamp b) {
        Stamp ab = a.join(b);
        Stamp ba = b.join(a);
        Assert.assertEquals(ab, ba);
        return ab;
    }

    protected static Stamp meet(Stamp a, Stamp b) {
        Stamp ab = a.meet(b);
        Stamp ba = b.meet(a);
        Assert.assertEquals(ab, ba);
        return ab;
    }

    protected ResolvedJavaType getType(Class<?> clazz) {
        return getMetaAccess().lookupJavaType(clazz);
    }
}
