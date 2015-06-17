/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.dsl.test.TestHelper.*;
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.LimitTestFactory.ConstantLimitTestFactory;
import com.oracle.truffle.api.dsl.test.LimitTestFactory.DefaultLimit3TestFactory;
import com.oracle.truffle.api.dsl.test.LimitTestFactory.LocalLimitTestFactory;
import com.oracle.truffle.api.dsl.test.LimitTestFactory.MethodLimitTestFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

@SuppressWarnings("unused")
public class LimitTest {

    @Test
    public void testDefaultLimit3() {
        CallTarget root = createCallTarget(DefaultLimit3TestFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        assertEquals(44, root.call(44));
        try {
            root.call(45);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @NodeChild
    static class DefaultLimit3Test extends ValueNode {
        @Specialization(guards = "value == cachedValue")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }
    }

    @Test
    public void testConstantLimit() {
        CallTarget root = createCallTarget(ConstantLimitTestFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        try {
            root.call(44);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @NodeChild
    static class ConstantLimitTest extends ValueNode {

        public static final int LIMIT = 2;

        @Specialization(limit = "LIMIT", guards = "value == cachedValue")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }
    }

    @Test
    public void testLocalLimit() {
        CallTarget root = createCallTarget(LocalLimitTestFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        try {
            root.call(44);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @NodeChild
    static class LocalLimitTest extends ValueNode {

        protected int localLimit = 2;

        @Specialization(limit = "localLimit", guards = "value == cachedValue")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }
    }

    @Test
    public void testMethodLimit() {
        MethodLimitTest.invocations = 0;
        CallTarget root = createCallTarget(MethodLimitTestFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        try {
            root.call(44);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
        assertEquals(3, MethodLimitTest.invocations);
    }

    @NodeChild
    static class MethodLimitTest extends ValueNode {

        static int invocations = 0;

        @Specialization(limit = "calculateLimitFor(cachedValue, invocations)", guards = "value == cachedValue")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        int calculateLimitFor(int cachedValue, int boundField) {
            invocations = boundField + 1;
            return 2;
        }

    }

    @NodeChild
    static class LimitErrorTest1 extends ValueNode {
        @ExpectError("The limit expression has no effect.%")
        @Specialization(limit = "4")
        static int do1(int value) {
            return value;
        }
    }

    @NodeChild
    static class LimitErrorTest2 extends ValueNode {
        public static final Object CONSTANT = new Object();

        @ExpectError("Incompatible return type Object. Limit expressions must return int.")
        @Specialization(limit = "CONSTANT", guards = "value == cachedValue")
        static int do1(int value, @Cached("value") int cachedValue) {
            return value;
        }
    }

    @NodeChild
    static class LimitErrorTest3 extends ValueNode {

        public static final Object CONSTANT = new Object();

        @ExpectError("Limit expressions must not bind dynamic parameter values.")
        @Specialization(limit = "value", guards = "value == cachedValue")
        static int do1(int value, @Cached("value") int cachedValue) {
            return value;
        }
    }

}
