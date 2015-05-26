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
import com.oracle.truffle.api.dsl.test.CachedTestFactory.BoundCacheFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.BoundCacheOverflowFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestBoundCacheOverflowContainsFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestCacheFieldFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestCacheMethodFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestCacheNodeFieldFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestGuardWithCachedAndDynamicParameterFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestGuardWithJustCachedParameterFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestMultipleCachesFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.UnboundCacheFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

@SuppressWarnings("unused")
public class CachedTest {

    @Test
    public void testUnboundCache() {
        CallTarget root = createCallTarget(UnboundCacheFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(43));
        assertEquals(42, root.call(44));
    }

    @NodeChild
    static class UnboundCache extends ValueNode {
        @Specialization
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }
    }

    @Test
    public void testBoundCache() {
        CallTarget root = createCallTarget(BoundCacheFactory.getInstance());
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
    static class BoundCache extends ValueNode {

        @Specialization(guards = "value == cachedValue", limit = "3")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testBoundCacheOverflow() {
        CallTarget root = createCallTarget(BoundCacheOverflowFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        assertEquals(-1, root.call(44));
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        assertEquals(-1, root.call(44));
    }

    @NodeChild
    static class BoundCacheOverflow extends ValueNode {

        @Specialization(guards = "value == cachedValue", limit = "2")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        @Specialization
        static int do2(int value) {
            return -1;
        }

    }

    @Test
    public void testBoundCacheOverflowContains() {
        CallTarget root = createCallTarget(TestBoundCacheOverflowContainsFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        assertEquals(-1, root.call(44));
        assertEquals(-1, root.call(42));
        assertEquals(-1, root.call(43));
        assertEquals(-1, root.call(44));
    }

    @NodeChild
    static class TestBoundCacheOverflowContains extends ValueNode {

        @Specialization(guards = "value == cachedValue", limit = "2")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        @Specialization(contains = "do1")
        static int do2(int value) {
            return -1;
        }

    }

    @Test
    public void testCacheField() {
        CallTarget root = createCallTarget(TestCacheFieldFactory.getInstance());
        assertEquals(3, root.call(42));
        assertEquals(3, root.call(43));
    }

    @NodeChild
    static class TestCacheField extends ValueNode {

        protected int field = 3;

        @Specialization()
        static int do1(int value, @Cached("field") int cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testCacheNodeField() {
        CallTarget root = createCallTarget(TestCacheNodeFieldFactory.getInstance(), 21);
        assertEquals(21, root.call(42));
        assertEquals(21, root.call(43));
    }

    @NodeChild
    @NodeField(name = "field", type = int.class)
    static class TestCacheNodeField extends ValueNode {

        @Specialization
        static int do1(int value, @Cached("field") int cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testCacheMethod() {
        TestCacheMethod.invocations = 0;
        CallTarget root = createCallTarget(TestCacheMethodFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(43));
        assertEquals(42, root.call(44));
        assertEquals(1, TestCacheMethod.invocations);
    }

    @NodeChild
    static class TestCacheMethod extends ValueNode {

        static int invocations = 0;

        @Specialization
        static int do1(int value, @Cached("someMethod(value)") int cachedValue) {
            return cachedValue;
        }

        static int someMethod(int value) {
            invocations++;
            return value;
        }

    }

    @Test
    public void testGuardWithJustCachedParameter() {
        TestGuardWithJustCachedParameter.invocations = 0;
        CallTarget root = createCallTarget(TestGuardWithJustCachedParameterFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(43));
        assertEquals(42, root.call(44));
        // guards with just cached parameters are just invoked on the slow path
        assertEquals(assertionsEnabled() ? 4 : 1, TestGuardWithJustCachedParameter.invocations);
    }

    @NodeChild
    static class TestGuardWithJustCachedParameter extends ValueNode {

        static int invocations = 0;

        @Specialization(guards = "someMethod(cachedValue)")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        static boolean someMethod(int value) {
            invocations++;
            return true;
        }

    }

    @Test
    public void testGuardWithCachedAndDynamicParameter() {
        TestGuardWithCachedAndDynamicParameter.cachedMethodInvocations = 0;
        TestGuardWithCachedAndDynamicParameter.dynamicMethodInvocations = 0;
        CallTarget root = createCallTarget(TestGuardWithCachedAndDynamicParameterFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(43));
        assertEquals(42, root.call(44));
        // guards with just cached parameters are just invoked on the slow path
        assertEquals(assertionsEnabled() ? 4 : 1, TestGuardWithCachedAndDynamicParameter.cachedMethodInvocations);
        assertEquals(4, TestGuardWithCachedAndDynamicParameter.dynamicMethodInvocations);
    }

    @NodeChild
    static class TestGuardWithCachedAndDynamicParameter extends ValueNode {

        static int cachedMethodInvocations = 0;
        static int dynamicMethodInvocations = 0;

        @Specialization(guards = {"dynamicMethod(value)", "cachedMethod(cachedValue)"})
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        static boolean cachedMethod(int value) {
            cachedMethodInvocations++;
            return true;
        }

        static boolean dynamicMethod(int value) {
            dynamicMethodInvocations++;
            return true;
        }

    }

    /*
     * Node should not produce any warnings in isIdentical of the generated code. Unnecessary casts
     * were generated for isIdentical on the fast path.
     */
    @NodeChildren({@NodeChild, @NodeChild})
    static class RegressionTestWarningInIsIdentical extends ValueNode {

        @Specialization(guards = {"cachedName == name"})
        protected Object directAccess(String receiver, String name, //
                        @Cached("name") String cachedName, //
                        @Cached("create(receiver, name)") Object callHandle) {
            return receiver;
        }

        protected static Object create(String receiver, String name) {
            return receiver;
        }

    }

    @NodeChild
    static class TestMultipleCaches extends ValueNode {

        @Specialization
        static int do1(int value, @Cached("value") int cachedValue1, @Cached("value") int cachedValue2) {
            return cachedValue1 + cachedValue2;
        }

    }

    @Test
    public void testMultipleCaches() {
        CallTarget root = createCallTarget(TestMultipleCachesFactory.getInstance());
        assertEquals(42, root.call(21));
        assertEquals(42, root.call(22));
        assertEquals(42, root.call(23));
    }

    @NodeChild
    static class CachedError1 extends ValueNode {
        @Specialization
        static int do1(int value, @ExpectError("Incompatible return type int. The expression type must be equal to the parameter type double.")//
                        @Cached("value") double cachedValue) {
            return value;
        }
    }

    @NodeChild
    static class CachedError2 extends ValueNode {

        // caches are not allowed to make backward references

        @Specialization
        static int do1(int value,
                        @ExpectError("The initializer expression of parameter 'cachedValue1' binds unitialized parameter 'cachedValue2. Reorder the parameters to resolve the problem.") @Cached("cachedValue2") int cachedValue1,
                        @Cached("value") int cachedValue2) {
            return cachedValue1 + cachedValue2;
        }

    }

    @NodeChild
    static class CachedError3 extends ValueNode {

        // cyclic dependency between cached expressions
        @Specialization
        static int do1(int value,
                        @ExpectError("The initializer expression of parameter 'cachedValue1' binds unitialized parameter 'cachedValue2. Reorder the parameters to resolve the problem.") @Cached("cachedValue2") int cachedValue1,
                        @Cached("cachedValue1") int cachedValue2) {
            return cachedValue1 + cachedValue2;
        }

    }

}
