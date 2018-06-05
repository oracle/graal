/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.createRoot;
import static com.oracle.truffle.api.dsl.test.TestHelper.executeWith;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.NullTestFactory.NullTest1Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class NullTest {

    @Test
    public void testGuardInvocations() {
        TestRootNode<NullTest1> root = createRoot(NullTest1Factory.getInstance());

        assertEquals("fallback", executeWith(root, (Object) null));
        assertEquals(true, executeWith(root, true));
        assertEquals(42L, executeWith(root, 42));
        assertEquals("string", executeWith(root, "s"));
        assertEquals("fallback", executeWith(root, (Object) null));
    }

    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class NullTest1 extends ValueNode {

        @Specialization
        long s(long a) {
            return a;
        }

        @Specialization
        boolean s(boolean a) {
            return a;
        }

        @Specialization
        String s(String a) {
            return "string";
        }

        @Fallback
        Object s(Object a) {
            return "fallback";
        }

    }

}
