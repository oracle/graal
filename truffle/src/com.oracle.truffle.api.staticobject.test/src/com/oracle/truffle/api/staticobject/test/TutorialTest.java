/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.staticobject.test;

import com.oracle.truffle.api.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticPropertyKind;
import com.oracle.truffle.api.staticobject.StaticShape;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/**
 * These tests are derived from the Static Object Model tutorial
 * (`truffle/docs/StaticObjectModel.md`). Do not modify them without updating the tutorial.
 */
@RunWith(Theories.class)
public class TutorialTest extends StaticObjectModelTest {
    @DataPoints //
    public static TestEnvironment[] environments;

    @BeforeClass
    public static void setup() {
        environments = getTestEnvironments();
    }

    @AfterClass
    public static void teardown() {
        for (TestEnvironment env : environments) {
            env.close();
        }
    }

    public static abstract class MyStaticObject {
        final String arg1;
        final Object arg2;

        public MyStaticObject(String arg1) {
            this(arg1, null);
        }

        public MyStaticObject(String arg1, Object arg2) {
            this.arg1 = arg1;
            this.arg2 = arg2;
        }
    }

    public interface MyStaticObjectInterface {
        MyStaticObject create(String arg1);
        MyStaticObject create(String arg1, Object arg2);
    }

    @Theory
    public void gettingStarted(TestEnvironment te) {
        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty p1 = new DefaultStaticProperty("property1", StaticPropertyKind.Int, false);
        StaticProperty p2 = new DefaultStaticProperty("property2", StaticPropertyKind.Object, true);
        builder.property(p1).property(p2);
        StaticShape<DefaultStaticObjectFactory> shape = builder.build();
        Object staticObject = shape.getFactory().create();
        p1.setInt(staticObject, 42);
        p2.setObject(staticObject, "42");
        assert p1.getInt(staticObject) == 42;
        assert p2.getObject(staticObject).equals("42");
    }

    @Theory
    public void shapeHierarchies(TestEnvironment te) {
        // Create a shape
        StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
        StaticProperty s1p1 = new DefaultStaticProperty("property1", StaticPropertyKind.Int, false);
        StaticProperty s1p2 = new DefaultStaticProperty("property2", StaticPropertyKind.Object, true);
        b1.property(s1p1).property(s1p2);
        StaticShape<DefaultStaticObjectFactory> s1 = b1.build();
        // Create a sub-shape
        StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
        StaticProperty s2p1 = new DefaultStaticProperty("property1", StaticPropertyKind.Int, false);
        b2.property(s2p1);
        StaticShape<DefaultStaticObjectFactory> s2 = b2.build(s1);
        // Create a static object for the sub-shape
        Object o2 = s2.getFactory().create();
        // Perform property accesses
        s1p1.setInt(o2, 42);
        s1p2.setObject(o2, "42");
        s2p1.setInt(o2, 24);
        assert s1p1.getInt(o2) == 42;
        assert s1p2.getObject(o2).equals("42");
        assert s2p1.getInt(o2) == 24;
    }

    @Theory
    public void extendingCustomBaseClasses(TestEnvironment te) {
        StaticProperty property = new DefaultStaticProperty("arg1", StaticPropertyKind.Object, false);
        StaticShape<MyStaticObjectInterface> shape = StaticShape.newBuilder(te.testLanguage).property(property).build(MyStaticObject.class, MyStaticObjectInterface.class);
        MyStaticObject staticObject = shape.getFactory().create("arg1");
        property.setObject(staticObject, "42");
        assert staticObject.arg1.equals("arg1"); // fields of the custom super class are directly accessible
        assert property.getObject(staticObject).equals("42"); // static properties are accessible as usual
    }

    @Test
    @SuppressWarnings("unused")
    public void memoryFootprint1() {
        class MyField {
            final StaticProperty p;

            MyField(StaticProperty p) {
                this.p = p;
            }
        }

        new MyField(new DefaultStaticProperty("property1", StaticPropertyKind.Int, false));
    }

    @Test
    @SuppressWarnings("unused")
    public void memoryFootprint2() {
        class MyField extends StaticProperty {
            final Object name;

            MyField(Object name, StaticPropertyKind kind, boolean storeAsFinal) {
                super(kind, storeAsFinal);
                this.name = name;
            }

            @Override
            public String getId() {
                return name.toString(); // this string must be a unique identifier within a Builder
            }
        }

        new MyField("property1", StaticPropertyKind.Int, false);
    }

    @Theory
    public void safetyChecks1(TestEnvironment te) {
        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Int, false);
        Object staticObject = builder.property(property).build().getFactory().create();
        try {
            property.setObject(staticObject, "wrong access type");
            assert false;
        } catch (IllegalArgumentException e) {
        }
    }

    @Theory
    @SuppressWarnings("unused")
    public void safetyChecks2(TestEnvironment te) {
        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty property = new DefaultStaticProperty("property", StaticPropertyKind.Object, false);
        Object staticObject1 = builder.property(property).build().getFactory().create();
        Object staticObject2 = StaticShape.newBuilder(te.testLanguage).build().getFactory().create();

        try {
            property.setObject(staticObject2, "wrong shape");
            assert false;
        } catch (IllegalArgumentException e) {
        }
    }
}
