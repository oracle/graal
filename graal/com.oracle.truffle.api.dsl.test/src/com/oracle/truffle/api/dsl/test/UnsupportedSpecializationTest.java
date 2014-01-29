/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.dsl.test.UnsupportedSpecializationTestFactory.Undefined1Factory;

public class UnsupportedSpecializationTest {

    @Test
    public void testUndefined1() {
        TestRootNode<Undefined1> root = TestHelper.createRoot(Undefined1Factory.getInstance());
        try {
            TestHelper.executeWith(root, "");
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertNotNull(e.getSuppliedValues());
            Assert.assertEquals(1, e.getSuppliedValues().length);
            Assert.assertEquals("", e.getSuppliedValues()[0]);
            Assert.assertEquals(root.getNode(), e.getNode());
        }
    }

    @NodeChild("a")
    abstract static class Undefined1 extends ValueNode {

        @Specialization
        public int doInteger(@SuppressWarnings("unused") int a) {
            throw new AssertionError();
        }
    }

    // TODO more tests required
}
