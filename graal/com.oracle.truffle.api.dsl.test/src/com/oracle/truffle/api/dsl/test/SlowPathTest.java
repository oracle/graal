/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import org.junit.*;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.SlowPathTestFactory.SlowPathOnGeneric0Factory;
import com.oracle.truffle.api.dsl.test.SlowPathTestFactory.SlowPathOnGeneric1Factory;
import com.oracle.truffle.api.dsl.test.SlowPathTestFactory.SlowPathOnGeneric2Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/** Tests the generated placement of {@link SlowPath} in generated methods. */
public class SlowPathTest {

    @Test
    public void testSlowPathOnGeneric0() throws NoSuchMethodException, SecurityException {
        Node node = SlowPathOnGeneric0Factory.create(null);
        Assert.assertNull(node.getClass().getSuperclass().getDeclaredMethod("executeGeneric0", VirtualFrame.class, Object.class).getAnnotation(SlowPath.class));
    }

    @NodeChild
    abstract static class SlowPathOnGeneric0 extends ValueNode {

        @Specialization
        @SuppressWarnings("unused")
        Object doObject0(VirtualFrame frame, int value0) {
            throw new AssertionError();
        }

    }

    @Test
    public void testSlowPathOnGeneric1() throws NoSuchMethodException, SecurityException {
        Node node = SlowPathOnGeneric1Factory.create(null);
        Assert.assertNotNull(node.getClass().getSuperclass().getDeclaredMethod("executeGeneric0", Object.class).getAnnotation(SlowPath.class));
    }

    @NodeChild
    abstract static class SlowPathOnGeneric1 extends ValueNode {

        @Specialization
        @SuppressWarnings("unused")
        Object doObject0(int value0) {
            throw new AssertionError();
        }

    }

    @Test
    public void testSlowPathOnGeneric2() throws NoSuchMethodException, SecurityException {
        Node node = SlowPathOnGeneric2Factory.create(null);
        Assert.assertNull(node.getClass().getSuperclass().getDeclaredMethod("executeGeneric0", VirtualFrame.class, Object.class).getAnnotation(SlowPath.class));
    }

    @NodeChild
    abstract static class SlowPathOnGeneric2 extends ValueNode {

        @Specialization(order = 0)
        @SuppressWarnings("unused")
        Object doObject0(int value0) {
            throw new AssertionError();
        }

        @Specialization(order = 1)
        @SuppressWarnings("unused")
        Object doObject1(VirtualFrame frame, String value0) {
            throw new AssertionError();
        }

    }

}
