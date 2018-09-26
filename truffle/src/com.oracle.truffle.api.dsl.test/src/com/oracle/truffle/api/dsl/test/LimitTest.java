/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.LimitTestFactory.ConstantLimitTestFactory;
import com.oracle.truffle.api.dsl.test.LimitTestFactory.DefaultLimit3TestFactory;
import com.oracle.truffle.api.dsl.test.LimitTestFactory.LocalLimitTestFactory;
import com.oracle.truffle.api.dsl.test.LimitTestFactory.MethodLimitTestFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

@SuppressWarnings("unused")
public class LimitTest {

    @Test
    public void testDefaultLimit3() {
        CallTarget root = TestHelper.createCallTarget(DefaultLimit3TestFactory.getInstance());
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
        CallTarget root = TestHelper.createCallTarget(ConstantLimitTestFactory.getInstance());
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
        CallTarget root = TestHelper.createCallTarget(LocalLimitTestFactory.getInstance());
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
        CallTarget root = TestHelper.createCallTarget(MethodLimitTestFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        try {
            root.call(44);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
        Assert.assertTrue(MethodLimitTest.invocations >= 3);
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

        @ExpectError({"Incompatible return type Object. Limit expressions must return int."})
        @Specialization(limit = "CONSTANT", guards = "value == cachedValue")
        static int do1(int value, @Cached("value") int cachedValue) {
            return value;
        }
    }

    @NodeChild
    static class LimitErrorTest3 extends ValueNode {

        public static final Object CONSTANT = new Object();

        @ExpectError({"Limit expressions must not bind dynamic parameter values."})
        @Specialization(limit = "value", guards = "value == cachedValue")
        static int do1(int value, @Cached("value") int cachedValue) {
            return value;
        }
    }

}
