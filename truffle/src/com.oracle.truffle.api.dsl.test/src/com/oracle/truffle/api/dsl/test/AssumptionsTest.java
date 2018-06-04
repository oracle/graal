/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.dsl.test.TestHelper.createCallTarget;
import static com.oracle.truffle.api.dsl.test.TestHelper.getNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.AssumptionArrayTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.AssumptionArraysAreCompilationFinalCachedFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.AssumptionArraysAreCompilationFinalFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.AssumptionInvalidateTest1NodeGen;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.AssumptionInvalidateTest2NodeGen;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.AssumptionInvalidateTest3NodeGen;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.AssumptionInvalidateTest4NodeGen;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.CacheAssumptionTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.FieldTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.MethodTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.MultipleAssumptionArraysTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.MultipleAssumptionsTestFactory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.NodeFieldTest2Factory;
import com.oracle.truffle.api.dsl.test.AssumptionsTestFactory.StaticFieldTestFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.dsl.test.examples.ExampleNode;
import com.oracle.truffle.api.dsl.test.examples.ExampleTypes;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

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
        // here is an unfixed bug in the old dsl layout. the new layout gets rid
        // of invalid cached entries earlier, therefore lines are available again.
        assertEquals("do1", root.call(45)); // 43 got removed, so there is space for 45
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

        @Specialization(guards = "value == cachedValue", limit = "3", assumptions = "getAssumption(cachedValue)")
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
    public void testMultipleAssumptions() {
        CallTarget root = createCallTarget(MultipleAssumptionsTestFactory.getInstance());
        MultipleAssumptionsTest node = getNode(root);
        node.assumption1 = Truffle.getRuntime().createAssumption();
        node.assumption2 = Truffle.getRuntime().createAssumption();

        assertEquals("do1", root.call(42));
        node.assumption1.invalidate();
        assertEquals("do2", root.call(42));

        CallTarget root2 = createCallTarget(MultipleAssumptionsTestFactory.getInstance());
        MultipleAssumptionsTest node2 = getNode(root2);
        node2.assumption1 = Truffle.getRuntime().createAssumption();
        node2.assumption2 = Truffle.getRuntime().createAssumption();

        assertEquals("do1", root2.call(42));
        node2.assumption2.invalidate();
        assertEquals("do2", root2.call(42));
    }

    @NodeChild
    @SuppressWarnings("unused")
    static class MultipleAssumptionsTest extends ValueNode {

        Assumption assumption1;
        Assumption assumption2;

        @Specialization(assumptions = {"assumption1", "assumption2"})
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
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

    @Test
    public void testMultipleAssumptionArrays() {
        CallTarget root = createCallTarget(MultipleAssumptionArraysTestFactory.getInstance());
        MultipleAssumptionArraysTest node = getNode(root);

        Assumption a1 = Truffle.getRuntime().createAssumption();
        Assumption a2 = Truffle.getRuntime().createAssumption();

        node.assumptions1 = new Assumption[]{a1};
        node.assumptions2 = new Assumption[]{a2};

        assertEquals("do1", root.call(42));

        a2.invalidate();

        assertEquals("do2", root.call(42));
    }

    @NodeChild
    @SuppressWarnings("unused")
    static class MultipleAssumptionArraysTest extends ValueNode {

        Assumption[] assumptions1;
        Assumption[] assumptions2;

        @Specialization(assumptions = {"assumptions1", "assumptions2"})
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }

    }

    @Test
    public void testAssumptionInvalidateTest1() {
        AssumptionInvalidateTest1 node = AssumptionInvalidateTest1NodeGen.create();
        node.execute(0);
        node.execute(1);
        node.execute(2);

        for (int i = 0; i < 100; i++) {
            int removeIndex = i % 3;
            Assumption a = node.assumptions[removeIndex];
            a.invalidate();
            node.execute(removeIndex);
            assertNotSame(a, node.assumptions[removeIndex]);
        }
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class AssumptionInvalidateTest1 extends Node {

        Assumption[] assumptions = new Assumption[3];

        abstract int execute(int value);

        @Specialization(guards = "value == cachedValue", limit = "3", assumptions = "createAssumption(cachedValue)")
        public int s0(int value, @SuppressWarnings("unused") @Cached("value") int cachedValue) {
            return value;
        }

        Assumption createAssumption(int value) {
            Assumption a = Truffle.getRuntime().createAssumption();
            assumptions[value] = a;
            return a;
        }
    }

    @Test
    public void testAssumptionInvalidateTest2() {
        AssumptionInvalidateTest2 node = AssumptionInvalidateTest2NodeGen.create();
        node.execute(0);
        node.execute(1);
        node.execute(2);

        for (int i = 0; i < 100; i++) {
            int removeIndex = i % 3;
            Assumption a = node.assumptions[removeIndex];
            a.invalidate();
            node.execute(removeIndex);
            assertNotSame(a, node.assumptions[removeIndex]);
        }
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class AssumptionInvalidateTest2 extends Node {

        Assumption[] assumptions = new Assumption[3];

        abstract int execute(int value);

        @Specialization(guards = "value == cachedValue", limit = "3", assumptions = "createAssumption(cachedValue)")
        @SuppressWarnings("unused")
        public int s0(int value, @Cached("value") int cachedValue, @Cached("createChild()") Node node) {
            return value;
        }

        static Node createChild() {
            // does not matter for this test
            return null;
        }

        Assumption createAssumption(int value) {
            Assumption a = Truffle.getRuntime().createAssumption();
            assumptions[value] = a;
            return a;
        }
    }

    @Test
    public void testAssumptionInvalidateTest3() {
        AssumptionInvalidateTest3 node = AssumptionInvalidateTest3NodeGen.create();
        node.execute(0);

        for (int i = 0; i < 100; i++) {
            int removeIndex = 0;
            Assumption a = node.assumptions[removeIndex];
            a.invalidate();
            node.execute(removeIndex);
            assertNotSame(a, node.assumptions[removeIndex]);
        }
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class AssumptionInvalidateTest3 extends Node {

        Assumption[] assumptions = new Assumption[1];

        abstract int execute(int value);

        @Specialization(guards = "value == cachedValue", assumptions = "createAssumption(cachedValue)", limit = "1")
        @SuppressWarnings("unused")
        public int s0(int value, @Cached("value") int cachedValue, @Cached("createChild()") Node node) {
            return value;
        }

        static Node createChild() {
            // does not matter for this test
            return new ExampleNode() {
            };
        }

        Assumption createAssumption(int value) {
            Assumption a = Truffle.getRuntime().createAssumption();
            assumptions[value] = a;
            return a;
        }
    }

    @Test
    public void testAssumptionInvalidateTest4() {
        AssumptionInvalidateTest4 node = AssumptionInvalidateTest4NodeGen.create();
        node.execute(0);

        for (int i = 0; i < 100; i++) {
            int removeIndex = 0;
            Assumption a = node.assumptions;
            a.invalidate();
            node.execute(removeIndex);
            assertNotSame(a, node.assumptions);
        }
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class AssumptionInvalidateTest4 extends Node {

        Assumption assumptions;

        abstract int execute(int value);

        @Specialization(assumptions = "createAssumption()")
        public int s0(int value) {
            return value;
        }

        Assumption createAssumption() {
            Assumption a = Truffle.getRuntime().createAssumption();
            assumptions = a;
            return a;
        }
    }

    @Test
    public void testAssumptionArraysAreCompilationFinal() throws NoSuchFieldException,
                    SecurityException {
        AssumptionArraysAreCompilationFinal node = TestHelper.createNode(AssumptionArraysAreCompilationFinalFactory.getInstance(), false);
        Field field = node.getClass().getDeclaredField("do1_assumption0_");
        field.setAccessible(true);
        assertEquals(Assumption[].class, field.getType());
        CompilationFinal compilationFinal = field.getAnnotation(CompilationFinal.class);
        assertEquals(1, compilationFinal.dimensions());
    }

    @NodeChild
    static class AssumptionArraysAreCompilationFinal extends ValueNode {

        @Specialization(assumptions = "createAssumptions()")
        static int do1(int value) {
            return value;
        }

        Assumption[] createAssumptions() {
            return new Assumption[]{Truffle.getRuntime().createAssumption()};
        }
    }

    @Test
    public void testAssumptionArraysAreCompilationFinalCached() throws NoSuchFieldException,
                    SecurityException, IllegalArgumentException {
        AssumptionArraysAreCompilationFinalCached node = TestHelper.createNode(AssumptionArraysAreCompilationFinalCachedFactory.getInstance(), false);
        Field doCachedField = node.getClass().getDeclaredField("do1_cache");
        doCachedField.setAccessible(true);
        Field field = doCachedField.getType().getDeclaredField("assumption0_");
        field.setAccessible(true);
        assertEquals(Assumption[].class, field.getType());
        CompilationFinal compilationFinal = field.getAnnotation(CompilationFinal.class);
        assertEquals(1, compilationFinal.dimensions());
    }

    @Test
    public void testAssumptionArraysCheckUseExplodeLoop() throws SecurityException, IllegalArgumentException, NoSuchMethodException {
        AssumptionArraysAreCompilationFinalCached node = TestHelper.createNode(AssumptionArraysAreCompilationFinalCachedFactory.getInstance(), false);
        Method isValidMethod = node.getClass().getDeclaredMethod("isValid_", Assumption[].class);
        assertNotNull(isValidMethod.getAnnotation(ExplodeLoop.class));
    }

    @NodeChild
    static class AssumptionArraysAreCompilationFinalCached extends ValueNode {

        @Specialization(guards = "value == cachedValue", limit = "3", assumptions = "createAssumptions()")
        static int do1(int value, @SuppressWarnings("unused") @Cached("value") int cachedValue) {
            return value;
        }

        Assumption[] createAssumptions() {
            return new Assumption[]{Truffle.getRuntime().createAssumption()};
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
