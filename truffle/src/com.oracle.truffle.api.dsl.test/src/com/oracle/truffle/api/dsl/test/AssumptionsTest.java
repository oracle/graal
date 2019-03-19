/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.dsl.test.TestHelper.createCallTarget;
import static com.oracle.truffle.api.dsl.test.TestHelper.getNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
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
