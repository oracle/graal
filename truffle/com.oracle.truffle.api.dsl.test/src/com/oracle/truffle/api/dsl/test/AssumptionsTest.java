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

import static com.oracle.truffle.api.dsl.test.TestHelper.*;
import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.AssumptionArrayTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.CacheAssumptionTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.FieldTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.MethodTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.NodeFieldTest2Factory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.StaticFieldTestFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class AssumptionsTest {

    @Test
    public void testField() {
        CallTarget root = createCallTarget(FieldTestFactory.getInstance());
        FieldTest node = getNode(root);
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(42));
        node.field.invalidate();
        try {
            root.call(45);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @NodeChild
    static class FieldTest extends ValueNode {

        protected final Assumption field = Truffle.getRuntime().createAssumption();

        @Specialization(assumptions = "field")
        static int do1(int value) {
            return value;
        }
    }

    @Test
    public void testNodeField() {
        Assumption assumption = Truffle.getRuntime().createAssumption();
        CallTarget root = createCallTarget(NodeFieldTest2Factory.getInstance(), assumption);
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(42));
        assumption.invalidate();
        try {
            root.call(45);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @NodeChild
    @NodeField(name = "field", type = Assumption.class)
    static class NodeFieldTest2 extends ValueNode {

        @Specialization(assumptions = "field")
        static int do1(int value) {
            return value;
        }
    }

    @Test
    public void testStaticField() {
        CallTarget root = createCallTarget(StaticFieldTestFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(42));
        StaticFieldTest.FIELD.invalidate();
        try {
            root.call(45);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @NodeChild
    static class StaticFieldTest extends ValueNode {

        static final Assumption FIELD = Truffle.getRuntime().createAssumption();

        @Specialization(assumptions = "FIELD")
        static int do1(int value) {
            return value;
        }
    }

    @Test
    public void testMethod() {
        CallTarget root = createCallTarget(MethodTestFactory.getInstance());
        MethodTest node = getNode(root);
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(42));
        node.hiddenAssumption.invalidate();
        try {
            root.call(45);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @NodeChild
    static class MethodTest extends ValueNode {

        private final Assumption hiddenAssumption = Truffle.getRuntime().createAssumption();

        @Specialization(assumptions = "getAssumption()")
        static int do1(int value) {
            return value;
        }

        Assumption getAssumption() {
            return hiddenAssumption;
        }
    }

    @Test
    public void testCacheAssumption() {
        CallTarget root = createCallTarget(CacheAssumptionTestFactory.getInstance());
        CacheAssumptionTest node = getNode(root);
        assertEquals("do1", root.call(42));
        assertEquals("do1", root.call(43));
        assertEquals("do1", root.call(44));
        node.assumptions.get(42).invalidate();
        node.assumptions.put(42, null); // clear 42
        node.assumptions.get(43).invalidate();
        node.assumptions.get(44).invalidate();
        assertEquals("do1", root.call(42)); // recreates 42
        assertEquals("do2", root.call(43)); // invalid 43 -> remove -> insert do2
        assertEquals("do2", root.call(46)); // no more lines can be created.
    }

    @Test
    public void testCacheAssumptionLimit() {
        CallTarget root = createCallTarget(CacheAssumptionTestFactory.getInstance());
        assertEquals("do1", root.call(42));
        assertEquals("do1", root.call(43));
        assertEquals("do1", root.call(44));
        assertEquals("do2", root.call(45));
        assertEquals("do1", root.call(43));
        assertEquals("do1", root.call(44));
    }

    @NodeChild
    @SuppressWarnings("unused")
    static class CacheAssumptionTest extends ValueNode {

        private final Map<Integer, Assumption> assumptions = new HashMap<>();

        @Specialization(guards = "value == cachedValue", assumptions = "getAssumption(cachedValue)")
        static String do1(int value, @Cached("value") int cachedValue) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }

        Assumption getAssumption(int value) {
            Assumption assumption = assumptions.get(value);
            if (assumption == null) {
                assumption = Truffle.getRuntime().createAssumption();
                assumptions.put(value, assumption);
            }
            return assumption;
        }
    }

    @Test
    public void testAssumptionArrays() {
        CallTarget root = createCallTarget(AssumptionArrayTestFactory.getInstance());
        AssumptionArrayTest node = getNode(root);

        Assumption a1 = Truffle.getRuntime().createAssumption();
        Assumption a2 = Truffle.getRuntime().createAssumption();

        node.assumptions = new Assumption[]{a1, a2};

        assertEquals("do1", root.call(42));

        a2.invalidate();

        assertEquals("do2", root.call(42));
    }

    @NodeChild
    @SuppressWarnings("unused")
    static class AssumptionArrayTest extends ValueNode {

        Assumption[] assumptions;

        @Specialization(assumptions = "assumptions")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }

    }

    @NodeChild
    static class ErrorIncompatibleReturnType extends ValueNode {
        @ExpectError("Incompatible return type int. Assumptions must be assignable to Assumption or Assumption[].")
        @Specialization(assumptions = "3")
        static int do1(int value) {
            return value;
        }
    }

    @NodeChild
    static class ErrorBoundDynamicValue extends ValueNode {

        @ExpectError("Assumption expressions must not bind dynamic parameter values.")
        @Specialization(assumptions = "createAssumption(value)")
        static int do1(int value) {
            return value;
        }

        Assumption createAssumption(int value) {
            return Truffle.getRuntime().createAssumption(String.valueOf(value));
        }
    }

}
