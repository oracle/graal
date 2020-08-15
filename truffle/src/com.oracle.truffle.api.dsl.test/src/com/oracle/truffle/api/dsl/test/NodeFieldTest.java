/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.IntFieldNoGetterTestNodeFactory;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.IntFieldTestNodeFactory;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.MultipleFieldsTestNodeFactory;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.ObjectContainerNodeFactory;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.RewriteTestNodeFactory;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.StringFieldTestNodeFactory;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.TestContainerFactory;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.UncachedNodeIntFieldRefNodeGen;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.UncachedNodeIntFieldTestNodeGen;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.UncachedNodeObjectFieldTestNodeGen;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.UncachedNodeSettableIntFieldRefNodeGen;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public class NodeFieldTest {

    @Test
    public void testIntField() {
        assertEquals(42, createCallTarget(IntFieldTestNodeFactory.create(42)).call());
    }

    @NodeField(name = "field", type = int.class)
    abstract static class IntFieldTestNode extends ValueNode {

        public abstract int getField();

        @Specialization
        int intField() {
            return getField();
        }

    }

    @Test
    public void testIntFieldNoGetter() {
        assertEquals(42, createCallTarget(IntFieldNoGetterTestNodeFactory.create(42)).call());
    }

    @NodeField(name = "field", type = int.class)
    abstract static class IntFieldNoGetterTestNode extends ValueNode {

        @Specialization
        int intField(int field) {
            return field;
        }

    }

    @Test
    public void testMultipleFields() {
        assertEquals(42, createCallTarget(MultipleFieldsTestNodeFactory.create(21, 21)).call());
    }

    @NodeField(name = "field0", type = int.class)
    @NodeField(name = "field1", type = int.class)
    abstract static class MultipleFieldsTestNode extends ValueNode {

        public abstract int getField0();

        public abstract int getField1();

        @Specialization
        int intField() {
            return getField0() + getField1();
        }
    }

    @Test
    public void testStringField() {
        assertEquals("42", createCallTarget(StringFieldTestNodeFactory.create("42")).call());
    }

    @NodeField(name = "field", type = String.class)
    abstract static class StringFieldTestNode extends ValueNode {

        public abstract String getField();

        @Specialization
        String stringField() {
            return getField();
        }

    }

    @Test
    public void testRewrite() {
        assertEquals("42", createCallTarget(RewriteTestNodeFactory.create("42")).call());
    }

    @NodeField(name = "field", type = String.class)
    abstract static class RewriteTestNode extends ValueNode {

        public abstract String getField();

        @Specialization(rewriteOn = RuntimeException.class)
        String alwaysRewrite() {
            throw new RuntimeException();
        }

        @Specialization(replaces = "alwaysRewrite")
        Object returnField() {
            return getField();
        }
    }

    @Test
    public void testStringContainer() {
        assertEquals(42, createCallTarget(TestContainerFactory.create("42")).call());
    }

    @NodeField(name = "field", type = int.class)
    abstract static class IntContainerNode extends ValueNode {

        public abstract int getField();

    }

    @NodeField(name = "anotherField", type = String.class)
    abstract static class TestContainer extends ValueNode {

        @Specialization
        int containerField(String field) {
            return field.equals("42") ? 42 : -1;
        }

    }

    @Test
    public void testObjectContainer() {
        assertEquals("42", createCallTarget(ObjectContainerNodeFactory.create("42")).call());
    }

    @NodeField(name = "object", type = Object.class)
    abstract static class ObjectContainerNode extends ValueNode {

        public abstract Object getObject();

        @Specialization
        Object containerField() {
            return getObject();
        }

    }

    @Test
    public void testUncachedNodeIntFieldTest() {
        assertEquals(42, UncachedNodeIntFieldTestNodeGen.create(42).execute());
        assertFails(() -> UncachedNodeObjectFieldTestNodeGen.getUncached().execute(), UnsupportedOperationException.class);
    }

    @GenerateUncached
    @NodeField(name = "field", type = int.class)
    public abstract static class UncachedNodeIntFieldTest extends Node {

        abstract Object execute();

        protected abstract int getField();

        @Specialization
        Object s0() {
            return getField();
        }

        @Override
        public SourceSection getSourceSection() {
            // uses getSourceLength
            return null;
        }
    }

    @Test
    public void testUncachedNodeObjectFieldTest() {
        Object instance = new Object();
        assertSame(instance, UncachedNodeObjectFieldTestNodeGen.create(instance).execute());
        assertFails(() -> UncachedNodeObjectFieldTestNodeGen.getUncached().execute(), UnsupportedOperationException.class);
    }

    @GenerateUncached
    @NodeField(name = "field", type = Object.class)
    public abstract static class UncachedNodeObjectFieldTest extends Node {

        abstract Object execute();

        protected abstract Object getField();

        @Specialization
        Object s0() {
            return getField();
        }

        @Override
        public SourceSection getSourceSection() {
            // uses getSourceLength
            return null;
        }
    }

    @Test
    public void testUncachedNodeIntFieldRef() {
        assertEquals(42, UncachedNodeIntFieldRefNodeGen.create(42).execute());
        assertEquals(0, UncachedNodeIntFieldRefNodeGen.getUncached().execute());
    }

    @GenerateUncached
    @NodeField(name = "foo", type = int.class)
    public abstract static class UncachedNodeIntFieldRef extends Node {

        abstract Object execute();

        @Specialization
        static Object s0(int foo) {
            return foo;
        }
    }

    @Test
    public void testUncachedNodeSettableIntFieldRef() {
        UncachedNodeSettableIntFieldRef cached = UncachedNodeSettableIntFieldRefNodeGen.create();
        assertEquals(0, cached.execute());
        assertEquals(0, cached.getFoo());
        cached.setFoo(42);
        assertEquals(42, cached.execute());
        assertEquals(42, cached.getFoo());
        assertFails(() -> UncachedNodeSettableIntFieldRefNodeGen.getUncached().execute(), UnsupportedOperationException.class);
        assertFails(() -> UncachedNodeSettableIntFieldRefNodeGen.getUncached().getFoo(), UnsupportedOperationException.class);
        assertFails(() -> UncachedNodeSettableIntFieldRefNodeGen.getUncached().setFoo(42), UnsupportedOperationException.class);
    }

    @GenerateUncached
    @NodeField(name = "foo", type = int.class)
    public abstract static class UncachedNodeSettableIntFieldRef extends Node {

        abstract Object execute();

        abstract int getFoo();

        abstract void setFoo(int foo);

        @Specialization
        Object s0() {
            return getFoo();
        }
    }

}
